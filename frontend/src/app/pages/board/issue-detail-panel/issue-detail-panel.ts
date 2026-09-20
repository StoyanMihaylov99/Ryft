import { DatePipe } from '@angular/common';
import { Component, HostListener, computed, effect, inject, input, output, signal } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { EMPTY, Observable, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthService } from '../../../core/auth/auth.service';
import { Comment } from '../../../core/comment/models';
import { CommentService } from '../../../core/comment/comment.service';
import { Issue, IssuePriority, IssueStatus, UpdateIssueRequest } from '../../../core/issue/models';
import { IssueService } from '../../../core/issue/issue.service';

@Component({
  imports: [DatePipe, A11yModule],
  selector: 'app-issue-detail-panel',
  templateUrl: './issue-detail-panel.html',
  styleUrl: './issue-detail-panel.css',
})
export class IssueDetailPanel {
  private readonly issueService = inject(IssueService);
  private readonly commentService = inject(CommentService);
  private readonly authService = inject(AuthService);

  readonly issueKey = input.required<string>();
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

  /** Staged edits for title/status/priority/storyPoints/description — not sent until save() is called. */
  readonly draftTitle = signal('');
  readonly draftDescription = signal('');
  readonly draftPriority = signal<IssuePriority>('MEDIUM');
  readonly draftStoryPoints = signal<number | null>(null);
  readonly draftStatus = signal<IssueStatus>('TODO');
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

  constructor() {
    effect(() => this.load(this.issueKey()));
  }

  private load(issueKey: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.issue.set(null);
    this.comments.set([]);
    this.issueService.get(issueKey).subscribe({
      next: (issue) => {
        this.issue.set(issue);
        this.resetDraft(issue);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load the issue.');
      },
    });
    this.commentService.listForIssue(issueKey).subscribe({
      next: (comments) => this.comments.set(comments),
    });
  }

  private resetDraft(issue: Issue): void {
    this.draftTitle.set(issue.title);
    this.draftDescription.set(issue.description ?? '');
    this.draftPriority.set(issue.priority);
    this.draftStoryPoints.set(issue.storyPoints);
    this.draftStatus.set(issue.status);
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

    const savingKey = this.issueKey();
    this.saving.set(true);
    this.errorMessage.set(null);

    const patched$: Observable<Issue> = patchRequest ? this.issueService.update(issue.key, patchRequest) : of(issue);
    const saved$: Observable<Issue> = patched$.pipe(
      switchMap((patched) => {
        if (!statusChanged) {
          return of(patched);
        }
        return this.issueService.changeStatus(issue.key, status).pipe(
          catchError(() => {
            this.saving.set(false);
            if (this.issueKey() === savingKey) {
              // The PATCH already succeeded server-side, so reflect it locally even though the
              // status change failed — otherwise local state would diverge from the server.
              this.issue.set(patched);
              this.resetDraft(patched);
              this.draftStatus.set(status);
              this.errorMessage.set(
                patchRequest ? 'Status change failed; other changes were saved.' : 'Failed to change status.',
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
        if (this.issueKey() !== savingKey) {
          return;
        }
        this.issue.set(saved);
        this.resetDraft(saved);
        this.updated.emit(saved);
      },
      error: () => {
        this.saving.set(false);
        if (this.issueKey() !== savingKey) {
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
        this.comments.update((list) => list.map((comment) => (comment.id === commentId ? updated : comment)));
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
      next: () => this.comments.update((list) => list.filter((comment) => comment.id !== commentId)),
      error: () => {
        this.confirmingDeleteCommentId.set(null);
        this.errorMessage.set('Failed to delete the comment.');
      },
    });
  }
}
