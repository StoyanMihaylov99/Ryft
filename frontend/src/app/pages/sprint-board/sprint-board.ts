import {
  CdkDragDrop,
  DragDropModule,
  moveItemInArray,
  transferArrayItem,
} from '@angular/cdk/drag-drop';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { AuthService } from '../../core/auth/auth.service';
import { BoardColumn } from '../../core/board/models';
import { ProjectRole } from '../../core/project/models';
import { ProjectService } from '../../core/project/project.service';
import { Burndown, SprintBoard as SprintBoardModel } from '../../core/sprint/models';
import { SprintService } from '../../core/sprint/sprint.service';
import { Issue } from '../../core/issue/models';
import { IssueService } from '../../core/issue/issue.service';
import { IssueCard } from '../../shared/issue-card/issue-card';
import { BurndownChart } from '../../shared/burndown-chart/burndown-chart';
import { IssueDetailPanel } from '../board/issue-detail-panel/issue-detail-panel';

@Component({
  imports: [DragDropModule, RouterLink, Sidebar, IssueCard, BurndownChart, IssueDetailPanel],
  selector: 'app-sprint-board',
  templateUrl: './sprint-board.html',
  styleUrl: './sprint-board.css',
})
export class SprintBoard {
  private readonly route = inject(ActivatedRoute);
  private readonly sprintService = inject(SprintService);
  private readonly issueService = inject(IssueService);
  private readonly projectService = inject(ProjectService);
  private readonly authService = inject(AuthService);

  readonly projectKey = this.route.snapshot.paramMap.get('projectKey')!;
  readonly board = signal<SprintBoardModel | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  /** Distinguished from errorMessage: a 404 here means "no ACTIVE sprint", a normal, actionable
   *  empty state (link to Sprints) rather than a failure banner. */
  readonly noActiveSprint = signal(false);
  readonly selectedIssueKey = signal<string | null>(null);

  readonly showBurndown = signal(false);
  readonly burndown = signal<Burndown | null>(null);
  readonly burndownLoading = signal(false);
  readonly burndownError = signal<string | null>(null);

  /** Only Owner/Admin create, edit or delete issues — everyone else just comments and drags cards,
   *  mirrors Board's canManageIssues gate. */
  readonly myRole = signal<ProjectRole | null>(null);
  readonly canManageIssues = computed(() => this.myRole() === 'OWNER' || this.myRole() === 'ADMIN');
  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

  readonly listIds = computed(() =>
    (this.board()?.columns ?? []).map((column) => this.columnListId(column)),
  );

  /** Every EPIC currently on the sprint board — used to resolve each card's "Epic: <title>" chip
   *  without an extra request. */
  readonly epics = computed<Issue[]>(() =>
    (this.board()?.columns ?? [])
      .flatMap((column) => column.issues)
      .filter((issue) => issue.type === 'EPIC'),
  );

  constructor() {
    this.loadBoard();
    this.loadMembers();
  }

  private loadMembers(): void {
    this.projectService.listMembers(this.projectKey).subscribe({
      next: (members) => {
        const mine = members.find((member) => member.userId === this.currentUserId());
        this.myRole.set(mine?.role ?? null);
      },
      // Leave myRole null on failure — canManageIssues() then stays false, the safe default.
      error: () => {},
    });
  }

  loadBoard(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.noActiveSprint.set(false);
    this.sprintService.getBoard(this.projectKey).subscribe({
      next: (board) => {
        this.board.set(board);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 404) {
          this.noActiveSprint.set(true);
        } else {
          this.errorMessage.set('Failed to load the sprint board.');
        }
      },
    });
  }

  toggleBurndown(): void {
    this.showBurndown.update((shown) => !shown);
    if (this.showBurndown() && !this.burndown() && !this.burndownLoading()) {
      this.loadBurndown();
    }
  }

  private loadBurndown(): void {
    const sprintId = this.board()?.sprintId;
    if (!sprintId) {
      return;
    }
    this.burndownLoading.set(true);
    this.burndownError.set(null);
    this.sprintService.getBurndown(sprintId).subscribe({
      next: (burndown) => {
        this.burndownLoading.set(false);
        this.burndown.set(burndown);
      },
      error: () => {
        this.burndownLoading.set(false);
        this.burndownError.set('Failed to load the burndown chart.');
      },
    });
  }

  columnListId(column: BoardColumn): string {
    return `column-${column.statusId}`;
  }

  /** Drag-and-drop is the client half of "server-validated" status changes — the actual legality
   *  check happens in PATCH /issues/{issueKey}/status; a failed request reverts the optimistic
   *  move. Identical to Board.drop: moving a card here is a status change, not a sprint-membership
   *  change, so it goes through the same endpoint. */
  drop(event: CdkDragDrop<Issue[]>, targetColumn: BoardColumn): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      return;
    }

    const issue = event.previousContainer.data[event.previousIndex];
    transferArrayItem(
      event.previousContainer.data,
      event.container.data,
      event.previousIndex,
      event.currentIndex,
    );

    this.issueService.changeStatus(issue.key, targetColumn.category).subscribe({
      error: () => {
        transferArrayItem(
          event.container.data,
          event.previousContainer.data,
          event.currentIndex,
          event.previousIndex,
        );
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
        board.columns
          .find((candidate) => candidate.category === updated.status)
          ?.issues.push(updated);
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

  epicTitleFor(issue: Issue): string | null {
    if (!issue.parentId) {
      return null;
    }
    return this.epics().find((epic) => epic.id === issue.parentId)?.title ?? null;
  }
}
