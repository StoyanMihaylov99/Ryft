import { DatePipe } from '@angular/common';
import {
  Component,
  HostListener,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { EMPTY, Observable, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthService } from '../../../core/auth/auth.service';
import { Comment } from '../../../core/comment/models';
import { CommentService } from '../../../core/comment/comment.service';
import {
  Issue,
  IssuePriority,
  IssueStatus,
  IssueType,
  UpdateIssueRequest,
} from '../../../core/issue/models';
import { IssueService } from '../../../core/issue/issue.service';
import { IssueTypeBadge } from '../../../shared/issue-type-badge/issue-type-badge';

@Component({
  imports: [DatePipe, A11yModule, IssueTypeBadge],
  selector: 'app-issue-detail-panel',
  templateUrl: './issue-detail-panel.html',
  styleUrl: './issue-detail-panel.css',
})
export class IssueDetailPanel {
  private readonly issueService = inject(IssueService);
  private readonly commentService = inject(CommentService);
  private readonly authService = inject(AuthService);

  readonly issueKey = input.required<string>();
  /** Needed to load this project's issues, used to populate the Epic field's options and to
   *  resolve the linked-epic/subtask-parent chips — optional so a panel opened without it (e.g. in
   *  isolation) just skips that field and those chips. */
  readonly projectKey = input<string | null>(null);
  /** Owner/Admin only — title, description, priority and delete are gated on this; status and
   *  comments are not (see IssueServiceImpl.changeStatus's javadoc for why status stays open). */
  readonly canManage = input(false);
  readonly closed = output<void>();
  readonly updated = output<Issue>();
  readonly deleted = output<string>();

  readonly issue = signal<Issue | null>(null);
  readonly comments = signal<Comment[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly newCommentBody = signal('');
  readonly postingComment = signal(false);
  readonly editingCommentId = signal<string | null>(null);
  readonly editingCommentBody = signal('');
  readonly confirmingDeleteIssue = signal(false);
  readonly confirmingDeleteCommentId = signal<string | null>(null);

  /** This project's non-Subtask issues, for the Epic field's options, for resolving the
   *  linked-epic chip, and for resolving a Subtask's parent chip — loaded once per projectKey,
   *  independent of which issue is currently open. */
  readonly projectIssues = signal<Issue[]>([]);

  /** The key currently being displayed, which drifts from `issueKey()` once the user drills into a
   *  subtask — see `openSubtask`. Doubles as the race-guard for in-flight requests: a response is
   *  only applied if this still matches the key it was requested for. */
  readonly displayedIssueKey = signal<string | null>(null);

  readonly subtasks = signal<Issue[]>([]);
  readonly loadingSubtasks = signal(false);
  readonly subtaskError = signal<string | null>(null);
  readonly newSubtaskTitle = signal('');
  readonly creatingSubtask = signal(false);

  /** Staged edits for title/status/priority/storyPoints/description/parentId — not sent until
   *  save() is called. */
  readonly draftTitle = signal('');
  readonly draftDescription = signal('');
  readonly draftPriority = signal<IssuePriority>('MEDIUM');
  readonly draftStoryPoints = signal<number | null>(null);
  readonly draftStatus = signal<IssueStatus>('TODO');
  readonly draftParentId = signal<string | null>(null);
  readonly saving = signal(false);

  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

  readonly hasUnsavedChanges = computed(() => {
    const issue = this.issue();
    if (!issue) {
      return false;
    }
    return this.buildPatchRequest(issue) !== null || this.draftStatus() !== issue.status;
  });

  readonly titleIsBlank = computed(() => this.draftTitle().trim().length === 0);

  /** True once the user picks "No epic" on an issue that currently has one linked. There is no
   *  backend support for clearing a parent link (see UpdateIssueRequest's javadoc), so this
   *  selection is never sent — the Epic field shows an inline warning instead of silently no-oping. */
  readonly attemptingToUnlinkEpic = computed(() => {
    const issue = this.issue();
    return !!issue?.parentId && this.draftParentId() === null;
  });

  /** This project's Epics, derived from `projectIssues` for the Epic field's options. */
  readonly epics = computed<Issue[]>(() =>
    this.projectIssues().filter((issue) => issue.type === 'EPIC'),
  );

  /** The issue's linked Epic, resolved from the loaded `epics` list — null if unlinked, or if the
   *  epic couldn't be resolved (e.g. no projectKey was provided). */
  readonly linkedEpic = computed<Issue | null>(() => {
    const parentId = this.issue()?.parentId;
    if (!parentId) {
      return null;
    }
    return this.epics().find((epic) => epic.id === parentId) ?? null;
  });

  /** A Subtask's parent issue, resolved from `projectIssues` the same way `linkedEpic` resolves an
   *  Epic — null if it can't be resolved from what's already loaded (e.g. no projectKey given).
   *  There's no by-id lookup endpoint, so this is a deliberate, accepted limitation rather than an
   *  extra round trip. */
  readonly parentIssue = computed<Issue | null>(() => {
    const issue = this.issue();
    if (issue?.type !== 'SUBTASK' || !issue.parentId) {
      return null;
    }
    return this.projectIssues().find((candidate) => candidate.id === issue.parentId) ?? null;
  });

  /** Subtasks are checklist-style children of a STORY/TASK/BUG only — not of an EPIC, and not of
   *  another SUBTASK (no nesting), mirroring the backend's parent-link rules. */
  readonly canHaveSubtasks = computed(() => {
    const type = this.issue()?.type;
    return type === 'STORY' || type === 'TASK' || type === 'BUG';
  });

  readonly subtaskProgressLabel = computed<string | null>(() => {
    const subtasks = this.subtasks();
    if (subtasks.length === 0) {
      return null;
    }
    const done = subtasks.filter((subtask) => subtask.status === 'DONE').length;
    return `${done}/${subtasks.length} done`;
  });

  constructor() {
    effect(() => this.load(this.issueKey()));
    effect(() => {
      const projectKey = this.projectKey();
      if (projectKey) {
        this.loadProjectIssues(projectKey);
      }
    });
  }

  private load(issueKey: string): void {
    this.displayedIssueKey.set(issueKey);
    this.loading.set(true);
    this.errorMessage.set(null);
    this.issue.set(null);
    this.comments.set([]);
    this.subtasks.set([]);
    this.subtaskError.set(null);
    this.newSubtaskTitle.set('');
    this.issueService.get(issueKey).subscribe({
      next: (issue) => {
        if (this.displayedIssueKey() !== issueKey) {
          return;
        }
        this.issue.set(issue);
        this.resetDraft(issue);
        this.loading.set(false);
        if (this.canLoadSubtasksFor(issue.type)) {
          this.loadSubtasks(issueKey);
        }
      },
      error: () => {
        if (this.displayedIssueKey() !== issueKey) {
          return;
        }
        this.loading.set(false);
        this.errorMessage.set('Failed to load the issue.');
      },
    });
    this.commentService.listForIssue(issueKey).subscribe({
      next: (comments) => {
        if (this.displayedIssueKey() === issueKey) {
          this.comments.set(comments);
        }
      },
    });
  }

  private canLoadSubtasksFor(type: IssueType): boolean {
    return type === 'STORY' || type === 'TASK' || type === 'BUG';
  }

  private loadSubtasks(issueKey: string): void {
    this.loadingSubtasks.set(true);
    this.issueService.listSubtasks(issueKey).subscribe({
      next: (subtasks) => {
        if (this.displayedIssueKey() !== issueKey) {
          return;
        }
        this.loadingSubtasks.set(false);
        this.subtasks.set(subtasks);
      },
      error: () => {
        if (this.displayedIssueKey() !== issueKey) {
          return;
        }
        this.loadingSubtasks.set(false);
        this.subtaskError.set('Failed to load subtasks.');
      },
    });
  }

  private loadProjectIssues(projectKey: string): void {
    this.issueService.listForProject(projectKey).subscribe({
      next: (issues) => this.projectIssues.set(issues),
      // Non-critical: the Epic field just has no options and the linked-epic/parent chips stay hidden.
      error: () => {},
    });
  }

  /** Swaps the panel to show a subtask in place of its parent — a lightweight drill-down rather
   *  than a full navigation stack, since there's nowhere further to drill from a Subtask (it can't
   *  have subtasks of its own). Closing the panel from here returns to the board, not to the parent. */
  openSubtask(subtaskKey: string): void {
    this.load(subtaskKey);
  }

  submitSubtask(): void {
    const issue = this.issue();
    const title = this.newSubtaskTitle().trim();
    if (!issue || !title || !this.canManage() || this.creatingSubtask()) {
      return;
    }
    const parentKey = issue.key;
    this.creatingSubtask.set(true);
    this.subtaskError.set(null);
    this.issueService.createSubtask(parentKey, { title }).subscribe({
      next: (subtask) => {
        this.creatingSubtask.set(false);
        if (this.displayedIssueKey() !== parentKey) {
          return;
        }
        this.subtasks.update((list) => [...list, subtask]);
        this.newSubtaskTitle.set('');
      },
      error: () => {
        this.creatingSubtask.set(false);
        if (this.displayedIssueKey() === parentKey) {
          this.subtaskError.set('Failed to create the subtask.');
        }
      },
    });
  }

  changeSubtaskStatus(subtask: Issue, status: IssueStatus): void {
    const previous = this.subtasks();
    this.subtasks.set(
      previous.map((candidate) =>
        candidate.key === subtask.key ? { ...candidate, status } : candidate,
      ),
    );
    this.issueService.changeStatus(subtask.key, status).subscribe({
      next: (updated) => {
        this.subtasks.update((list) =>
          list.map((candidate) => (candidate.key === updated.key ? updated : candidate)),
        );
      },
      error: () => {
        this.subtasks.set(previous);
        this.subtaskError.set(`Failed to change ${subtask.key}'s status.`);
      },
    });
  }

  private resetDraft(issue: Issue): void {
    this.draftTitle.set(issue.title);
    this.draftDescription.set(issue.description ?? '');
    this.draftPriority.set(issue.priority);
    this.draftStoryPoints.set(issue.storyPoints);
    this.draftStatus.set(issue.status);
    this.draftParentId.set(issue.parentId);
  }

  updateDraftTitle(value: string): void {
    if (!this.canManage()) {
      return;
    }
    this.draftTitle.set(value);
  }

  updateDraftDescription(value: string): void {
    if (!this.canManage()) {
      return;
    }
    this.draftDescription.set(value);
  }

  updateDraftPriority(priority: IssuePriority): void {
    if (!this.canManage()) {
      return;
    }
    this.draftPriority.set(priority);
  }

  updateDraftStoryPoints(storyPoints: number | null): void {
    if (!this.canManage()) {
      return;
    }
    this.draftStoryPoints.set(storyPoints);
  }

  updateDraftStatus(status: IssueStatus): void {
    this.draftStatus.set(status);
  }

  updateDraftParentId(parentId: string | null): void {
    if (!this.canManage()) {
      return;
    }
    this.draftParentId.set(parentId);
  }

  save(): void {
    const issue = this.issue();
    if (!issue || this.saving()) {
      return;
    }
    const patchRequest = this.buildPatchRequest(issue);
    const status = this.draftStatus();
    const statusChanged = status !== issue.status;
    if (!patchRequest && !statusChanged) {
      return;
    }

    const savingKey = this.displayedIssueKey();
    this.saving.set(true);
    this.errorMessage.set(null);

    const patched$: Observable<Issue> = patchRequest
      ? this.issueService.update(issue.key, patchRequest)
      : of(issue);
    const saved$: Observable<Issue> = patched$.pipe(
      switchMap((patched) => {
        if (!statusChanged) {
          return of(patched);
        }
        return this.issueService.changeStatus(issue.key, status).pipe(
          catchError(() => {
            this.saving.set(false);
            if (this.displayedIssueKey() === savingKey) {
              // The PATCH already succeeded server-side, so reflect it locally even though the
              // status change failed — otherwise local state would diverge from the server.
              this.issue.set(patched);
              this.resetDraft(patched);
              this.draftStatus.set(status);
              this.errorMessage.set(
                patchRequest
                  ? 'Status change failed; other changes were saved.'
                  : 'Failed to change status.',
              );
            }
            return EMPTY;
          }),
        );
      }),
    );

    saved$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        if (this.displayedIssueKey() !== savingKey) {
          return;
        }
        this.issue.set(saved);
        this.resetDraft(saved);
        this.updated.emit(saved);
      },
      error: () => {
        this.saving.set(false);
        if (this.displayedIssueKey() !== savingKey) {
          return;
        }
        this.errorMessage.set('Failed to save changes.');
      },
    });
  }

  /** Returns only the managed fields that differ from the loaded issue, or null if none/not allowed. */
  private buildPatchRequest(issue: Issue): UpdateIssueRequest | null {
    if (!this.canManage()) {
      return null;
    }
    const request: UpdateIssueRequest = {};
    const title = this.draftTitle().trim();
    if (title && title !== issue.title) {
      request.title = title;
    }
    if (this.draftDescription() !== (issue.description ?? '')) {
      request.description = this.draftDescription();
    }
    if (this.draftPriority() !== issue.priority) {
      request.priority = this.draftPriority();
    }
    if (this.draftStoryPoints() !== issue.storyPoints) {
      request.storyPoints = this.draftStoryPoints();
    }
    // A null parentId means "don't touch it" server-side, not "clear it" (see
    // UpdateIssueRequest's javadoc), so an unlink attempt is never sent — attemptingToUnlinkEpic
    // surfaces that constraint to the user instead.
    if (this.draftParentId() !== issue.parentId && !this.attemptingToUnlinkEpic()) {
      request.parentId = this.draftParentId();
    }
    return Object.keys(request).length > 0 ? request : null;
  }

  requestDeleteIssue(): void {
    this.confirmingDeleteIssue.set(true);
  }

  cancelDeleteIssue(): void {
    this.confirmingDeleteIssue.set(false);
  }

  deleteIssue(): void {
    const issue = this.issue();
    if (!this.canManage() || !issue) {
      return;
    }
    this.issueService.delete(issue.key).subscribe({
      next: () => this.deleted.emit(issue.key),
      error: () => {
        this.confirmingDeleteIssue.set(false);
        this.errorMessage.set('Failed to delete the issue.');
      },
    });
  }

  close(): void {
    this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }

  submitComment(): void {
    const issue = this.issue();
    const body = this.newCommentBody().trim();
    if (!issue || !body) {
      return;
    }
    this.postingComment.set(true);
    this.commentService.create(issue.key, body).subscribe({
      next: (comment) => {
        this.comments.update((list) => [...list, comment]);
        this.newCommentBody.set('');
        this.postingComment.set(false);
      },
      error: () => {
        this.postingComment.set(false);
        this.errorMessage.set('Failed to post the comment.');
      },
    });
  }

  startEditComment(comment: Comment): void {
    this.editingCommentId.set(comment.id);
    this.editingCommentBody.set(comment.body);
  }

  cancelEditComment(): void {
    this.editingCommentId.set(null);
  }

  saveComment(commentId: string): void {
    const body = this.editingCommentBody().trim();
    if (!body) {
      return;
    }
    this.commentService.update(commentId, body).subscribe({
      next: (updated) => {
        this.comments.update((list) =>
          list.map((comment) => (comment.id === commentId ? updated : comment)),
        );
        this.editingCommentId.set(null);
      },
      error: () => this.errorMessage.set('Failed to save the comment.'),
    });
  }

  requestDeleteComment(commentId: string): void {
    this.confirmingDeleteCommentId.set(commentId);
  }

  cancelDeleteComment(): void {
    this.confirmingDeleteCommentId.set(null);
  }

  deleteComment(commentId: string): void {
    this.commentService.delete(commentId).subscribe({
      next: () =>
        this.comments.update((list) => list.filter((comment) => comment.id !== commentId)),
      error: () => {
        this.confirmingDeleteCommentId.set(null);
        this.errorMessage.set('Failed to delete the comment.');
      },
    });
  }
}
