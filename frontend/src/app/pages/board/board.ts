import {
  CdkDragDrop,
  DragDropModule,
  moveItemInArray,
  transferArrayItem,
} from '@angular/cdk/drag-drop';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { AuthService } from '../../core/auth/auth.service';
import { Board as BoardModel, BoardColumn } from '../../core/board/models';
import { BoardService } from '../../core/board/board.service';
import { ProjectMember, ProjectRole } from '../../core/project/models';
import { ProjectService } from '../../core/project/project.service';
import {
  CreateComponentRequest,
  CreateLabelRequest,
  Issue,
  IssueType,
  Label,
  ProjectComponent,
  UpdateComponentRequest,
  UpdateLabelRequest,
} from '../../core/issue/models';
import { IssueService } from '../../core/issue/issue.service';
import { ComponentChip } from '../../shared/component-chip/component-chip';
import { IssueCard } from '../../shared/issue-card/issue-card';
import { LabelChip } from '../../shared/label-chip/label-chip';
import { IssueDetailPanel } from './issue-detail-panel/issue-detail-panel';

@Component({
  imports: [
    DragDropModule,
    ReactiveFormsModule,
    RouterLink,
    Sidebar,
    IssueCard,
    IssueDetailPanel,
    LabelChip,
    ComponentChip,
  ],
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

  /** Every Label/Component defined for this project — used by the create-issue form's toggle
   *  chips, the board's filter dropdowns, and (once loaded) the management panel below. */
  readonly labels = signal<Label[]>([]);
  readonly components = signal<ProjectComponent[]>([]);

  readonly showLabelsPanel = signal(false);
  readonly newLabelName = signal('');
  readonly newLabelColor = signal('#6d5ef5');
  readonly creatingLabel = signal(false);
  readonly labelError = signal<string | null>(null);
  readonly newComponentName = signal('');
  readonly creatingComponent = signal(false);
  readonly componentError = signal<string | null>(null);

  /** Single-select filters applied client-side over the already-loaded board — the board endpoint
   *  has no server-side labelId/componentId filtering (it's never paginated and every card already
   *  carries full label/component data), so a new HTTP round-trip per filter change would be
   *  unnecessary. Both are independent (AND'd together), unlike the backend's mutually-exclusive
   *  sprintId/epicId/labelId/componentId precedence chain — that constraint exists only to avoid a
   *  dynamic query server-side and doesn't apply to filtering data already sitting in the browser. */
  readonly labelFilter = signal<string | null>(null);
  readonly componentFilter = signal<string | null>(null);
  readonly isFiltering = computed(
    () => this.labelFilter() !== null || this.componentFilter() !== null,
  );

  /** Staged for the create-issue form — not sent until submitCreate(). */
  readonly createLabelIds = signal<string[]>([]);
  readonly createComponentIds = signal<string[]>([]);

  readonly listIds = computed(() =>
    (this.board()?.columns ?? []).map((column) => this.columnListId(column)),
  );

  readonly createForm = this.formBuilder.nonNullable.group({
    type: ['TASK' as IssueType, [Validators.required]],
    title: ['', [Validators.required, Validators.maxLength(200)]],
    parentId: null as string | null,
  });

  /** All EPIC-typed issues currently on the board — the create form's Epic options and each card's
   *  "Epic: <title>" chip are both resolved from this, so linking an epic never needs a new request. */
  readonly epics = computed<Issue[]>(() =>
    (this.board()?.columns ?? [])
      .flatMap((column) => column.issues)
      .filter((issue) => issue.type === 'EPIC'),
  );

  readonly inviteForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    role: ['MEMBER' as ProjectRole, [Validators.required]],
  });

  constructor() {
    this.loadBoard();
    this.loadMembers();
    this.loadLabels();
    this.loadComponents();
  }

  private loadLabels(): void {
    this.issueService.listLabels(this.projectKey).subscribe({
      next: (labels) => this.labels.set(labels),
      // Non-critical: the filter dropdown and create form's Labels field just have no options.
      error: () => {},
    });
  }

  private loadComponents(): void {
    this.issueService.listComponents(this.projectKey).subscribe({
      next: (components) => this.components.set(components),
      // Non-critical: the filter dropdown and create form's Components field just have no options.
      error: () => {},
    });
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
    this.members.update((members) =>
      members.map((m) => (m.userId === userId ? { ...m, role } : m)),
    );
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

  toggleCreateForm(): void {
    this.showCreateForm.update((shown) => !shown);
    this.createLabelIds.set([]);
    this.createComponentIds.set([]);
  }

  isCreateLabelSelected(labelId: string): boolean {
    return this.createLabelIds().includes(labelId);
  }

  isCreateComponentSelected(componentId: string): boolean {
    return this.createComponentIds().includes(componentId);
  }

  toggleCreateLabel(labelId: string): void {
    this.createLabelIds.update((ids) =>
      ids.includes(labelId) ? ids.filter((id) => id !== labelId) : [...ids, labelId],
    );
  }

  toggleCreateComponent(componentId: string): void {
    this.createComponentIds.update((ids) =>
      ids.includes(componentId) ? ids.filter((id) => id !== componentId) : [...ids, componentId],
    );
  }

  /** An EPIC can't itself be linked to a parent — clear any staged selection so switching back to
   *  another type doesn't resurrect a stale, no-longer-visible choice. */
  onCreateTypeChange(): void {
    if (this.createForm.controls.type.value === 'EPIC') {
      this.createForm.controls.parentId.setValue(null);
    }
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    const { type, title, parentId } = this.createForm.getRawValue();
    const labelIds = this.createLabelIds();
    const componentIds = this.createComponentIds();
    this.issueService
      .create(this.projectKey, {
        type,
        title,
        ...(type !== 'EPIC' && parentId ? { parentId } : {}),
        ...(labelIds.length ? { labelIds } : {}),
        ...(componentIds.length ? { componentIds } : {}),
      })
      .subscribe({
        next: (issue) => {
          this.creating.set(false);
          this.showCreateForm.set(false);
          this.createForm.reset({ type: 'TASK', title: '', parentId: null });
          this.createLabelIds.set([]);
          this.createComponentIds.set([]);
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

  epicTitleFor(issue: Issue): string | null {
    if (!issue.parentId) {
      return null;
    }
    return this.epics().find((epic) => epic.id === issue.parentId)?.title ?? null;
  }

  /* An EPIC-typed card here (or on the backlog/sprint board) intentionally has no progress bar of
   * its own: the backend deliberately doesn't embed EpicProgress in IssueResponse to avoid an
   * extra query per Epic on every list/board response, so showing it per-card here would mean one
   * extra HTTP request per visible Epic card — potentially dozens on a card-dense board. Progress
   * is shown in the issue detail panel instead, where the user has explicitly opened that one Epic. */

  setLabelFilter(labelId: string | null): void {
    this.labelFilter.set(labelId);
  }

  setComponentFilter(componentId: string | null): void {
    this.componentFilter.set(componentId);
  }

  /** Whether `issue` should stay visible under the current filter selection — both filters are
   *  optional and AND'd together, unlike the backend's mutually-exclusive precedence chain (see
   *  `isFiltering`'s comment for why that doesn't apply here). */
  issueMatchesFilter(issue: Issue): boolean {
    const labelId = this.labelFilter();
    const componentId = this.componentFilter();
    if (labelId && !issue.labels.some((label) => label.id === labelId)) {
      return false;
    }
    if (componentId && !issue.components.some((component) => component.id === componentId)) {
      return false;
    }
    return true;
  }

  visibleIssueCount(column: BoardColumn): number {
    return column.issues.filter((issue) => this.issueMatchesFilter(issue)).length;
  }

  toggleLabelsPanel(): void {
    this.showLabelsPanel.update((shown) => !shown);
    this.labelError.set(null);
    this.componentError.set(null);
  }

  submitNewLabel(): void {
    const name = this.newLabelName().trim();
    if (!name || this.creatingLabel()) {
      return;
    }
    this.creatingLabel.set(true);
    this.labelError.set(null);
    const request: CreateLabelRequest = { name, color: this.newLabelColor() };
    this.issueService.createLabel(this.projectKey, request).subscribe({
      next: (label) => {
        this.creatingLabel.set(false);
        this.labels.update((labels) => [...labels, label]);
        this.newLabelName.set('');
      },
      error: () => {
        this.creatingLabel.set(false);
        this.labelError.set('Failed to create the label.');
      },
    });
  }

  renameLabel(labelId: string, name: string): void {
    const trimmed = name.trim();
    const current = this.labels().find((label) => label.id === labelId);
    if (!trimmed || !current || trimmed === current.name) {
      return;
    }
    this.updateLabel(labelId, { name: trimmed });
  }

  recolorLabel(labelId: string, color: string): void {
    this.updateLabel(labelId, { color });
  }

  private updateLabel(labelId: string, request: UpdateLabelRequest): void {
    const previous = this.labels();
    this.labels.update((labels) =>
      labels.map((label) => (label.id === labelId ? { ...label, ...request } : label)),
    );
    this.issueService.updateLabel(this.projectKey, labelId, request).subscribe({
      next: (updated) => {
        this.labels.update((labels) =>
          labels.map((label) => (label.id === labelId ? updated : label)),
        );
      },
      error: () => {
        this.labels.set(previous);
        this.labelError.set('Failed to update the label.');
      },
    });
  }

  deleteLabel(labelId: string): void {
    const previous = this.labels();
    const previousFilter = this.labelFilter();
    this.labels.set(previous.filter((label) => label.id !== labelId));
    if (previousFilter === labelId) {
      this.labelFilter.set(null);
    }
    this.issueService.deleteLabel(this.projectKey, labelId).subscribe({
      error: () => {
        this.labels.set(previous);
        this.labelFilter.set(previousFilter);
        this.labelError.set('Failed to delete the label.');
      },
    });
  }

  submitNewComponent(): void {
    const name = this.newComponentName().trim();
    if (!name || this.creatingComponent()) {
      return;
    }
    this.creatingComponent.set(true);
    this.componentError.set(null);
    const request: CreateComponentRequest = { name };
    this.issueService.createComponent(this.projectKey, request).subscribe({
      next: (component) => {
        this.creatingComponent.set(false);
        this.components.update((components) => [...components, component]);
        this.newComponentName.set('');
      },
      error: () => {
        this.creatingComponent.set(false);
        this.componentError.set('Failed to create the component.');
      },
    });
  }

  renameComponent(componentId: string, name: string): void {
    const trimmed = name.trim();
    const current = this.components().find((component) => component.id === componentId);
    if (!trimmed || !current || trimmed === current.name) {
      return;
    }
    const request: UpdateComponentRequest = { name: trimmed };
    const previous = this.components();
    this.components.update((components) =>
      components.map((component) =>
        component.id === componentId ? { ...component, ...request } : component,
      ),
    );
    this.issueService.updateComponent(this.projectKey, componentId, request).subscribe({
      next: (updated) => {
        this.components.update((components) =>
          components.map((component) => (component.id === componentId ? updated : component)),
        );
      },
      error: () => {
        this.components.set(previous);
        this.componentError.set('Failed to update the component.');
      },
    });
  }

  deleteComponent(componentId: string): void {
    const previous = this.components();
    const previousFilter = this.componentFilter();
    this.components.set(previous.filter((component) => component.id !== componentId));
    if (previousFilter === componentId) {
      this.componentFilter.set(null);
    }
    this.issueService.deleteComponent(this.projectKey, componentId).subscribe({
      error: () => {
        this.components.set(previous);
        this.componentFilter.set(previousFilter);
        this.componentError.set('Failed to delete the component.');
      },
    });
  }
}
