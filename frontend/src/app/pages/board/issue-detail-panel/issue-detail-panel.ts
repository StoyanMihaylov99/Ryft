import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { Comment } from '../../../core/comment/models';
import { CommentService } from '../../../core/comment/comment.service';
import { Issue, IssuePriority, IssueStatus, UpdateIssueRequest } from '../../../core/issue/models';
import { IssueService } from '../../../core/issue/issue.service';

@Component({
  imports: [],
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

  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

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

  saveTitle(value: string): void {
    const issue = this.issue();
    const title = value.trim();
    if (!this.canManage() || !issue || !title || title === issue.title) {
      return;
    }
    this.patch({ title });
  }

  saveDescription(value: string): void {
    const issue = this.issue();
    if (!this.canManage() || !issue) {
      return;
    }
    this.patch({ description: value });
  }

  changePriority(priority: IssuePriority): void {
    if (!this.canManage()) {
      return;
    }
    this.patch({ priority });
  }

  changeStatus(status: IssueStatus): void {
    const issue = this.issue();
    if (!issue) {
      return;
    }
    this.issueService.changeStatus(issue.key, status).subscribe({
      next: (updated) => {
        this.issue.set(updated);
        this.updated.emit(updated);
      },
      error: () => this.errorMessage.set('Failed to change the status.'),
    });
  }

  private patch(request: UpdateIssueRequest): void {
    const issue = this.issue();
    if (!issue) {
      return;
    }
    this.issueService.update(issue.key, request).subscribe({
      next: (updated) => {
        this.issue.set(updated);
        this.updated.emit(updated);
      },
      error: () => this.errorMessage.set('Failed to save changes.'),
    });
  }

  deleteIssue(): void {
    const issue = this.issue();
    if (!this.canManage() || !issue || !confirm(`Delete ${issue.key}? This cannot be undone.`)) {
      return;
    }
    this.issueService.delete(issue.key).subscribe({
      next: () => this.deleted.emit(issue.key),
      error: () => this.errorMessage.set('Failed to delete the issue.'),
    });
  }

  close(): void {
    this.closed.emit();
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

  deleteComment(commentId: string): void {
    if (!confirm('Delete this comment?')) {
      return;
    }
    this.commentService.delete(commentId).subscribe({
      next: () => this.comments.update((list) => list.filter((comment) => comment.id !== commentId)),
      error: () => this.errorMessage.set('Failed to delete the comment.'),
    });
  }
}
