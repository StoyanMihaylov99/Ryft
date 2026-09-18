import { CdkDragDrop, DragDropModule, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { AuthService } from '../../core/auth/auth.service';
import { Board as BoardModel, BoardColumn } from '../../core/board/models';
import { BoardService } from '../../core/board/board.service';
import { ProjectMember, ProjectRole } from '../../core/project/models';
import { ProjectService } from '../../core/project/project.service';
import { Issue, IssueType } from '../../core/issue/models';
import { IssueService } from '../../core/issue/issue.service';
import { IssueCard } from '../../shared/issue-card/issue-card';
import { IssueDetailPanel } from './issue-detail-panel/issue-detail-panel';

@Component({
  imports: [DragDropModule, ReactiveFormsModule, RouterLink, Sidebar, IssueCard, IssueDetailPanel],
  selector: 'app-board',
  templateUrl: './board.html',
  styleUrl: './board.css',
})
export class Board {
  private readonly route = inject(ActivatedRoute);
  private readonly boardService = inject(BoardService);
  private readonly issueService = inject(IssueService);
  private readonly projectService = inject(ProjectService);
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);

  readonly projectKey = this.route.snapshot.paramMap.get('projectKey')!;
  readonly board = signal<BoardModel | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedIssueKey = signal<string | null>(null);
  readonly showCreateForm = signal(false);
  readonly creating = signal(false);

  /** Only Owner/Admin create, edit or delete issues — everyone else just comments and drags cards. */
  readonly myRole = signal<ProjectRole | null>(null);
  readonly canManageIssues = computed(() => this.myRole() === 'OWNER' || this.myRole() === 'ADMIN');
  /** Adding members is Owner/Admin; changing roles or removing members is Owner-only (server-enforced). */
  readonly canAddMembers = computed(() => this.canManageIssues());
  readonly isOwner = computed(() => this.myRole() === 'OWNER');
  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

  readonly members = signal<ProjectMember[]>([]);
  readonly showMembersPanel = signal(false);
  readonly inviting = signal(false);
  readonly memberError = signal<string | null>(null);

  readonly listIds = computed(() => (this.board()?.columns ?? []).map((column) => this.columnListId(column)));

  readonly createForm = this.formBuilder.nonNullable.group({
    type: ['TASK' as IssueType, [Validators.required]],
    title: ['', [Validators.required, Validators.maxLength(200)]],
  });

  readonly inviteForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    role: ['MEMBER' as ProjectRole, [Validators.required]],
  });

  constructor() {
    this.loadBoard();
    this.loadMembers();
  }

  private loadMembers(): void {
    this.projectService.listMembers(this.projectKey).subscribe({
      next: (members) => {
        this.members.set(members);
        const mine = members.find((member) => member.userId === this.currentUserId());
        this.myRole.set(mine?.role ?? null);
      },
      // Leave myRole null on failure — canManageIssues() then stays false, the safe default.
      error: () => {},
    });
  }

  toggleMembersPanel(): void {
    this.showMembersPanel.update((shown) => !shown);
    this.memberError.set(null);
  }

  submitInvite(): void {
    if (this.inviteForm.invalid) {
      this.inviteForm.markAllAsTouched();
      return;
    }
    this.inviting.set(true);
    this.memberError.set(null);
    const { email, role } = this.inviteForm.getRawValue();
    this.projectService.addMember(this.projectKey, { email, role }).subscribe({
      next: (member) => {
        this.inviting.set(false);
        this.members.update((members) => [...members, member]);
        this.inviteForm.reset({ email: '', role: 'MEMBER' });
      },
      error: (err) => {
        this.inviting.set(false);
        this.memberError.set(
          err.status === 404
            ? 'No user with that email exists yet — they need to register first.'
            : err.status === 409
              ? 'That user is already a member of this project.'
              : 'Failed to add member.',
        );
      },
    });
  }

  changeMemberRole(userId: string, role: ProjectRole): void {
    this.memberError.set(null);
    const previous = this.members();
    this.members.update((members) => members.map((m) => (m.userId === userId ? { ...m, role } : m)));
    this.projectService.changeMemberRole(this.projectKey, userId, role).subscribe({
      error: () => {
        this.members.set(previous);
        this.memberError.set('Failed to change role.');
      },
    });
  }

  removeMember(userId: string): void {
    this.memberError.set(null);
    const previous = this.members();
    this.members.set(previous.filter((m) => m.userId !== userId));
    this.projectService.removeMember(this.projectKey, userId).subscribe({
      error: () => {
        this.members.set(previous);
        this.memberError.set('Failed to remove member.');
      },
    });
  }

  loadBoard(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.boardService.get(this.projectKey).subscribe({
      next: (board) => {
        this.board.set(board);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load the board.');
      },
    });
  }

  columnListId(column: BoardColumn): string {
    return `column-${column.statusId}`;
  }

  /** Drag-and-drop is the client half of "server-validated" status changes — the actual legality
   *  check happens in PATCH /issues/{issueKey}/status; a failed request reverts the optimistic move. */
  drop(event: CdkDragDrop<Issue[]>, targetColumn: BoardColumn): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      return;
    }

    const issue = event.previousContainer.data[event.previousIndex];
    transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);

    this.issueService.changeStatus(issue.key, targetColumn.category).subscribe({
      error: () => {
        transferArrayItem(event.container.data, event.previousContainer.data, event.currentIndex, event.previousIndex);
        this.errorMessage.set(`Failed to move ${issue.key}. Please try again.`);
      },
    });
  }

  openIssue(issueKey: string): void {
    this.selectedIssueKey.set(issueKey);
  }

  closePanel(): void {
    this.selectedIssueKey.set(null);
  }

  onIssueUpdated(updated: Issue): void {
    const board = this.board();
    if (!board) {
      return;
    }
    for (const column of board.columns) {
      const index = column.issues.findIndex((issue) => issue.key === updated.key);
      if (index === -1) {
        continue;
      }
      if (column.category === updated.status) {
        column.issues[index] = updated;
      } else {
        column.issues.splice(index, 1);
        board.columns.find((candidate) => candidate.category === updated.status)?.issues.push(updated);
      }
      break;
    }
    this.board.set({ ...board });
  }

  onIssueDeleted(issueKey: string): void {
    const board = this.board();
    if (!board) {
      return;
    }
    for (const column of board.columns) {
      column.issues = column.issues.filter((issue) => issue.key !== issueKey);
    }
    this.board.set({ ...board });
    this.closePanel();
  }

  toggleCreateForm(): void {
    this.showCreateForm.update((shown) => !shown);
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    const { type, title } = this.createForm.getRawValue();
    this.issueService.create(this.projectKey, { type, title }).subscribe({
      next: (issue) => {
        this.creating.set(false);
        this.showCreateForm.set(false);
        this.createForm.reset({ type: 'TASK', title: '' });
        const board = this.board();
        if (board) {
          board.columns.find((column) => column.category === 'TODO')?.issues.push(issue);
          this.board.set({ ...board });
        }
      },
      error: () => {
        this.creating.set(false);
        this.errorMessage.set('Failed to create the issue.');
      },
    });
  }
}
