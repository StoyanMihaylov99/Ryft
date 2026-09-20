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
  EpicProgress,
  Issue,
  IssuePriority,
  IssueStatus,
  IssueType,
  Label,
  ProjectComponent,
  UpdateIssueRequest,
} from '../../../core/issue/models';
import { IssueService } from '../../../core/issue/issue.service';
import { ProjectRole } from '../../../core/project/models';
import {
  canChangeStatus,
  canComment as canCommentPermission,
  canManageIssues,
} from '../../../core/project/permissions';
import { WorkflowStatus, WorkflowTransition } from '../../../core/workflow/models';
import { WorkflowService } from '../../../core/workflow/workflow.service';
import { ComponentChip } from '../../../shared/component-chip/component-chip';
import { IssueTypeBadge } from '../../../shared/issue-type-badge/issue-type-badge';
import { LabelChip } from '../../../shared/label-chip/label-chip';

@Component({
  imports: [DatePipe, A11yModule, IssueTypeBadge, LabelChip, ComponentChip],
  selector: 'app-issue-detail-panel',
  templateUrl: './issue-detail-panel.html',
  styleUrl: './issue-detail-panel.css',
})
export class IssueDetailPanel {
  private readonly issueService = inject(IssueService);
  private readonly commentService = inject(CommentService);
  private readonly workflowService = inject(WorkflowService);
  private readonly authService = inject(AuthService);

  readonly issueKey = input.required<string>();
  /** Needed to load this project's issues, used to populate the Epic field's options and to
   *  resolve the linked-epic/subtask-parent chips — optional so a panel opened without it (e.g. in
   *  isolation) just skips that field and those chips. */
  readonly projectKey = input<string | null>(null);
  /** The caller's role on this project — drives every permission gate below except `canEditIssue`
   *  (see that computed's own doc for why it's sourced from the issue itself instead). */
  readonly myRole = input<ProjectRole | null>(null);
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

  /** All Labels/Components defined for this project, for the toggle-chip attach UI — loaded once
   *  per projectKey, same lifecycle as `projectIssues`. */
  readonly allLabels = signal<Label[]>([]);
  readonly allComponents = signal<ProjectComponent[]>([]);

  /** This project's workflow scheme, for the status dropdown's options and its legal-next-status
   *  filtering — loaded once per projectKey, same lifecycle as `projectIssues`/`allLabels`. */
  readonly workflowStatuses = signal<WorkflowStatus[]>([]);
  readonly workflowTransitions = signal<WorkflowTransition[]>([]);
  readonly workflowLoaded = signal(false);

  /** The key currently being displayed, which drifts from `issueKey()` once the user drills into a
   *  subtask — see `openSubtask`. Doubles as the race-guard for in-flight requests: a response is
   *  only applied if this still matches the key it was requested for. */
  readonly displayedIssueKey = signal<string | null>(null);

  readonly subtasks = signal<Issue[]>([]);
  readonly loadingSubtasks = signal(false);
  readonly subtaskError = signal<string | null>(null);
  readonly newSubtaskTitle = signal('');
  readonly creatingSubtask = signal(false);

  readonly epicProgress = signal<EpicProgress | null>(null);
  readonly loadingEpicProgress = signal(false);
  readonly epicProgressError = signal<string | null>(null);

  /** Staged edits for title/status/priority/storyPoints/description/parentId — not sent until
   *  save() is called. */
  readonly draftTitle = signal('');
  readonly draftDescription = signal('');
  readonly draftPriority = signal<IssuePriority>('MEDIUM');
  readonly draftStoryPoints = signal<number | null>(null);
  readonly draftStatusId = signal('');
  readonly draftParentId = signal<string | null>(null);
  readonly draftLabelIds = signal<string[]>([]);
  readonly draftComponentIds = signal<string[]>([]);
  /** Whether the user has touched the label/component selection since it was last loaded/saved —
   *  distinct from "the selection differs from the original", since toggling something on then
   *  back off leaves the *content* unchanged but must still be treated as touched. This is what
   *  lets `buildPatchRequest` tell "leave it alone" (omit the field) apart from "clear it" (send
   *  `[]`), matching UpdateIssueRequest's null-vs-empty-array contract. */
  readonly labelsTouched = signal(false);
  readonly componentsTouched = signal(false);
  readonly saving = signal(false);

  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

  /** Owner/Admin, or an involved Member (assignee/reporter) — sourced directly from the server's
   *  `Issue.callerCanEdit` rather than recomputed from `myRole()`/`currentUserId()` here. The pure
   *  `canEditIssue` function in `core/project/permissions.ts` mirrors the same backend rule and is
   *  independently unit-tested, but trusting the field the issue itself already carries means this
   *  component can never drift out of sync with `IssueServiceImpl.requireCanEditIssue` if that rule
   *  ever changes — there's exactly one place the rule is expressed as logic (the backend), and one
   *  place it's read from (here). */
  readonly canEditIssue = computed(() => this.issue()?.callerCanEdit ?? false);
  /** Delete stays Owner/Admin-only regardless of involvement — not derivable from `callerCanEdit`,
   *  which also returns true for an involved Member. */
  readonly canDeleteIssue = computed(() => canManageIssues(this.myRole()));
  /** A Viewer can never change status; every other role can, on any issue (see
   *  `IssueServiceImpl.changeStatus`, which checks only for Viewer, not involvement). */
  readonly canChangeStatusForPanel = computed(() => canChangeStatus(this.myRole()));
  readonly canCommentInPanel = computed(() => canCommentPermission(this.myRole()));
  /** Subtask creation stays Owner/Admin-only, unlike editing an existing issue — the backend's
   *  `createSubtask` gate wasn't loosened by the Phase-4 involvement rule (only create/update/delete
   *  of top-level issues were), so this deliberately doesn't use `canEditIssue`. */
  readonly canCreateSubtask = computed(() => canManageIssues(this.myRole()));

  readonly hasUnsavedChanges = computed(() => {
    const issue = this.issue();
    if (!issue) {
      return false;
    }
    return this.buildPatchRequest(issue) !== null || this.draftStatusId() !== issue.statusId;
  });

  readonly titleIsBlank = computed(() => this.draftTitle().trim().length === 0);

  /** The status dropdown's options for the currently displayed issue: every status reachable from
   *  its current one per the loaded transition graph, plus its current status itself (a no-op
   *  "keep as-is" choice). Falls back to just the current status until the workflow scheme has
   *  loaded, rather than show an empty or unfiltered dropdown. */
  readonly statusOptions = computed<WorkflowStatus[]>(() => {
    const issue = this.issue();
    if (!issue) {
      return [];
    }
    return this.legalStatusOptionsFrom(issue.statusId, issue.statusName, issue.statusCategory);
  });

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
    const done = subtasks.filter((subtask) => subtask.statusCategory === 'DONE').length;
    return `${done}/${subtasks.length} done`;
  });

  readonly epicProgressLabel = computed<string | null>(() => {
    const progress = this.epicProgress();
    return progress ? `${progress.doneCount}/${progress.totalCount} done` : null;
  });

  /** Rounded for display/aria — the raw `percentDone` can be a long-tailed float (e.g. 3/7 issues). */
  readonly epicProgressPercent = computed<number>(() =>
    Math.round(this.epicProgress()?.percentDone ?? 0),
  );

  constructor() {
    effect(() => this.load(this.issueKey()));
    effect(() => {
      const projectKey = this.projectKey();
      if (projectKey) {
        this.loadProjectIssues(projectKey);
        this.loadLabelsAndComponents(projectKey);
        this.loadWorkflow(projectKey);
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
    this.epicProgress.set(null);
    this.epicProgressError.set(null);
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
        if (issue.type === 'EPIC') {
          this.loadEpicProgress(issueKey);
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

  private loadEpicProgress(issueKey: string): void {
    this.loadingEpicProgress.set(true);
    this.issueService.getEpicProgress(issueKey).subscribe({
      next: (progress) => {
        if (this.displayedIssueKey() !== issueKey) {
          return;
        }
        this.loadingEpicProgress.set(false);
        this.epicProgress.set(progress);
      },
      error: () => {
        if (this.displayedIssueKey() !== issueKey) {
          return;
        }
        this.loadingEpicProgress.set(false);
        this.epicProgressError.set('Failed to load progress.');
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

  private loadLabelsAndComponents(projectKey: string): void {
    this.issueService.listLabels(projectKey).subscribe({
      next: (labels) => this.allLabels.set(labels),
      // Non-critical: the Labels field just has no options to toggle.
      error: () => {},
    });
    this.issueService.listComponents(projectKey).subscribe({
      next: (components) => this.allComponents.set(components),
      // Non-critical: the Components field just has no options to toggle.
      error: () => {},
    });
  }

  private loadWorkflow(projectKey: string): void {
    this.workflowService.get(projectKey).subscribe({
      next: (scheme) => {
        this.workflowStatuses.set(scheme.statuses);
        this.workflowTransitions.set(scheme.transitions);
        this.workflowLoaded.set(true);
      },
      // Non-critical: the status dropdown falls back to showing just the current status.
      error: () => {},
    });
  }

  /** Every status reachable from `fromStatusId` per the loaded transition graph, plus
   *  `fromStatusId` itself (a no-op "keep as-is" choice) — falls back to just that one status until
   *  the workflow scheme has loaded. Shared by the main issue's status dropdown and each subtask
   *  row's, since a Subtask has its own status independent of its parent's. */
  private legalStatusOptionsFrom(
    fromStatusId: string,
    fromStatusName: string,
    fromStatusCategory: IssueStatus,
  ): WorkflowStatus[] {
    if (!this.workflowLoaded()) {
      return [{ id: fromStatusId, name: fromStatusName, category: fromStatusCategory, sortOrder: 0 }];
    }
    const legalToIds = new Set(
      this.workflowTransitions()
        .filter((transition) => transition.fromStatusId === fromStatusId)
        .map((transition) => transition.toStatusId),
    );
    return this.workflowStatuses().filter(
      (status) => status.id === fromStatusId || legalToIds.has(status.id),
    );
  }

  subtaskStatusOptions(subtask: Issue): WorkflowStatus[] {
    return this.legalStatusOptionsFrom(subtask.statusId, subtask.statusName, subtask.statusCategory);
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
    if (!issue || !title || !this.canCreateSubtask() || this.creatingSubtask()) {
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

  changeSubtaskStatus(subtask: Issue, statusId: string): void {
    if (!this.canChangeStatusForPanel()) {
      return;
    }
    const previous = this.subtasks();
    const targetStatus = this.workflowStatuses().find((status) => status.id === statusId);
    this.subtasks.set(
      previous.map((candidate) =>
        candidate.key === subtask.key
          ? {
              ...candidate,
              statusId,
              statusName: targetStatus?.name ?? candidate.statusName,
              statusCategory: targetStatus?.category ?? candidate.statusCategory,
            }
          : candidate,
      ),
    );
    this.issueService.changeStatus(subtask.key, statusId).subscribe({
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
    this.draftStatusId.set(issue.statusId);
    this.draftParentId.set(issue.parentId);
    this.draftLabelIds.set(issue.labels.map((label) => label.id));
    this.draftComponentIds.set(issue.components.map((component) => component.id));
    this.labelsTouched.set(false);
    this.componentsTouched.set(false);
  }

  isLabelSelected(labelId: string): boolean {
    return this.draftLabelIds().includes(labelId);
  }

  isComponentSelected(componentId: string): boolean {
    return this.draftComponentIds().includes(componentId);
  }

  toggleDraftLabel(labelId: string): void {
    if (!this.canEditIssue()) {
      return;
    }
    this.labelsTouched.set(true);
    this.draftLabelIds.update((ids) =>
      ids.includes(labelId) ? ids.filter((id) => id !== labelId) : [...ids, labelId],
    );
  }

  toggleDraftComponent(componentId: string): void {
    if (!this.canEditIssue()) {
      return;
    }
    this.componentsTouched.set(true);
    this.draftComponentIds.update((ids) =>
      ids.includes(componentId) ? ids.filter((id) => id !== componentId) : [...ids, componentId],
    );
  }

  updateDraftTitle(value: string): void {
    if (!this.canEditIssue()) {
      return;
    }
    this.draftTitle.set(value);
  }

  updateDraftDescription(value: string): void {
    if (!this.canEditIssue()) {
      return;
    }
    this.draftDescription.set(value);
  }

  updateDraftPriority(priority: IssuePriority): void {
    if (!this.canEditIssue()) {
      return;
    }
    this.draftPriority.set(priority);
  }

  updateDraftStoryPoints(storyPoints: number | null): void {
    if (!this.canEditIssue()) {
      return;
    }
    this.draftStoryPoints.set(storyPoints);
  }

  updateDraftStatus(statusId: string): void {
    if (!this.canChangeStatusForPanel()) {
      return;
    }
    this.draftStatusId.set(statusId);
  }

  updateDraftParentId(parentId: string | null): void {
    if (!this.canEditIssue()) {
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
    const statusId = this.draftStatusId();
    const statusChanged = statusId !== issue.statusId;
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
        return this.issueService.changeStatus(issue.key, statusId).pipe(
          catchError(() => {
            this.saving.set(false);
            if (this.displayedIssueKey() === savingKey) {
              // The PATCH already succeeded server-side, so reflect it locally even though the
              // status change failed — otherwise local state would diverge from the server.
              this.issue.set(patched);
              this.resetDraft(patched);
              this.draftStatusId.set(statusId);
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
    if (!this.canEditIssue()) {
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
    // Only send labelIds/componentIds once the user has actually touched the selection — sending
    // them unconditionally would send `[]` (and silently clear existing labels/components) even
    // when the user never opened that section. See UpdateIssueRequest's javadoc for why `[]` and
    // "omitted" mean different things here, unlike every other field on this request.
    if (this.labelsTouched()) {
      request.labelIds = this.draftLabelIds();
    }
    if (this.componentsTouched()) {
      request.componentIds = this.draftComponentIds();
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
    if (!this.canDeleteIssue() || !issue) {
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
    if (!issue || !body || !this.canCommentInPanel()) {
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
    if (!body || !this.canCommentInPanel()) {
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
    if (!this.canCommentInPanel()) {
      return;
    }
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
