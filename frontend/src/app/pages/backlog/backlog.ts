import {
  CdkDragDrop,
  DragDropModule,
  moveItemInArray,
  transferArrayItem,
} from '@angular/cdk/drag-drop';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { AuthService } from '../../core/auth/auth.service';
import { ProjectRole } from '../../core/project/models';
import { ProjectService } from '../../core/project/project.service';
import { Sprint } from '../../core/sprint/models';
import { SprintService } from '../../core/sprint/sprint.service';
import { Issue } from '../../core/issue/models';
import { IssueService } from '../../core/issue/issue.service';
import { IssueCard } from '../../shared/issue-card/issue-card';
import { IssueDetailPanel } from '../board/issue-detail-panel/issue-detail-panel';

interface SprintSection {
  sprint: Sprint;
  issues: Issue[];
}

@Component({
  imports: [DragDropModule, RouterLink, Sidebar, IssueCard, IssueDetailPanel],
  selector: 'app-backlog',
  templateUrl: './backlog.html',
  styleUrl: './backlog.css',
})
export class Backlog {
  private readonly route = inject(ActivatedRoute);
  private readonly sprintService = inject(SprintService);
  private readonly issueService = inject(IssueService);
  private readonly projectService = inject(ProjectService);
  private readonly authService = inject(AuthService);

  readonly projectKey = this.route.snapshot.paramMap.get('projectKey')!;
  readonly sections = signal<SprintSection[]>([]);
  readonly backlogIssues = signal<Issue[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedIssueKey = signal<string | null>(null);

  /** Only Owner/Admin drag issues between the backlog and a sprint — mirrors Board's canManageIssues gate. */
  readonly myRole = signal<ProjectRole | null>(null);
  readonly canManage = computed(() => this.myRole() === 'OWNER' || this.myRole() === 'ADMIN');
  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

  readonly backlogListId = 'backlog';
  readonly listIds = computed(() => [
    ...this.sections().map((section) => this.sprintListId(section.sprint.id)),
    this.backlogListId,
  ]);

  /** Every EPIC currently loaded across sprint sections and the backlog — used to resolve each
   *  card's "Epic: <title>" chip without an extra request. An epic that only lives in a completed
   *  (unloaded) sprint won't resolve here; its cards simply show no chip. */
  readonly epics = computed<Issue[]>(() =>
    [...this.sections().flatMap((section) => section.issues), ...this.backlogIssues()].filter(
      (issue) => issue.type === 'EPIC',
    ),
  );

  constructor() {
    this.load();
    this.loadMembers();
  }

  private loadMembers(): void {
    this.projectService.listMembers(this.projectKey).subscribe({
      next: (members) => {
        const mine = members.find((member) => member.userId === this.currentUserId());
        this.myRole.set(mine?.role ?? null);
      },
      // Leave myRole null on failure — canManage() then stays false, the safe default.
      error: () => {},
    });
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.sprintService.listForProject(this.projectKey).subscribe({
      next: (sprints) => {
        const openSprints = sprints.filter(
          (sprint) => sprint.state === 'PLANNED' || sprint.state === 'ACTIVE',
        );
        if (openSprints.length === 0) {
          this.sections.set([]);
          this.loadBacklog();
          return;
        }
        forkJoin(
          openSprints.map((sprint) => this.issueService.listForProject(this.projectKey, sprint.id)),
        ).subscribe({
          next: (issuesPerSprint) => {
            this.sections.set(
              openSprints.map((sprint, index) => ({ sprint, issues: issuesPerSprint[index] })),
            );
            this.loadBacklog();
          },
          error: () => {
            this.loading.set(false);
            this.errorMessage.set('Failed to load sprint issues.');
          },
        });
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load sprints.');
      },
    });
  }

  private loadBacklog(): void {
    this.issueService.listBacklog(this.projectKey).subscribe({
      next: (issues) => {
        this.backlogIssues.set(issues);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load the backlog.');
      },
    });
  }

  sprintListId(sprintId: string): string {
    return `sprint-${sprintId}`;
  }

  /** Drag-and-drop is the client half of "server-validated" moves — the actual legality check
   *  happens server-side; a failed request reverts the optimistic move, mirroring Board.drop. */
  drop(event: CdkDragDrop<Issue[]>, targetSprintId: string | null): void {
    if (event.previousContainer === event.container) {
      const issues = event.container.data;
      moveItemInArray(issues, event.previousIndex, event.currentIndex);

      if (targetSprintId !== null) {
        // Only the backlog has a persisted order (Issue.backlogRank); a sprint section's issues
        // have no ranking column, so reordering within one is a client-side-only affordance —
        // same as Board's same-column drag, which never calls the server either.
        return;
      }

      const issue = issues[event.currentIndex];
      const beforeIssueKey = event.currentIndex > 0 ? issues[event.currentIndex - 1].key : null;
      const afterIssueKey =
        event.currentIndex < issues.length - 1 ? issues[event.currentIndex + 1].key : null;
      this.issueService.reorderBacklog(issue.key, beforeIssueKey, afterIssueKey).subscribe({
        error: () => {
          moveItemInArray(issues, event.currentIndex, event.previousIndex);
          this.errorMessage.set(`Failed to reorder ${issue.key}. Please try again.`);
        },
      });
      return;
    }

    const issue = event.previousContainer.data[event.previousIndex];
    transferArrayItem(
      event.previousContainer.data,
      event.container.data,
      event.previousIndex,
      event.currentIndex,
    );

    if (targetSprintId === null) {
      // The backlog is the only section with a persisted order, so a card dragged in from a
      // sprint still needs its backlogRank set from the drop position — otherwise it snaps back
      // to its stale, creation-time rank on the next fetch.
      const issues = event.container.data;
      const beforeIssueKey = event.currentIndex > 0 ? issues[event.currentIndex - 1].key : null;
      const afterIssueKey =
        event.currentIndex < issues.length - 1 ? issues[event.currentIndex + 1].key : null;

      this.issueService.moveToSprint(issue.key, targetSprintId).subscribe({
        next: () => {
          this.issueService.reorderBacklog(issue.key, beforeIssueKey, afterIssueKey).subscribe({
            error: () => {
              // The sprint-to-backlog move already succeeded server-side, so keep the issue in the
              // backlog rather than reverting the whole drop — just drop the unconfirmed position.
              moveItemInArray(issues, event.currentIndex, issues.length - 1);
              this.errorMessage.set(`Failed to reorder ${issue.key}. Please try again.`);
            },
          });
        },
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
      return;
    }

    this.issueService.moveToSprint(issue.key, targetSprintId).subscribe({
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
    const sections = this.sections();
    for (const section of sections) {
      const index = section.issues.findIndex((issue) => issue.key === updated.key);
      if (index !== -1) {
        section.issues[index] = updated;
        this.sections.set([...sections]);
        return;
      }
    }
    const backlogIssues = this.backlogIssues();
    const backlogIndex = backlogIssues.findIndex((issue) => issue.key === updated.key);
    if (backlogIndex !== -1) {
      backlogIssues[backlogIndex] = updated;
      this.backlogIssues.set([...backlogIssues]);
    }
  }

  epicTitleFor(issue: Issue): string | null {
    if (!issue.parentId) {
      return null;
    }
    return this.epics().find((epic) => epic.id === issue.parentId)?.title ?? null;
  }

  onIssueDeleted(issueKey: string): void {
    const sections = this.sections();
    for (const section of sections) {
      section.issues = section.issues.filter((issue) => issue.key !== issueKey);
    }
    this.sections.set([...sections]);
    this.backlogIssues.set(this.backlogIssues().filter((issue) => issue.key !== issueKey));
    this.closePanel();
  }
}
