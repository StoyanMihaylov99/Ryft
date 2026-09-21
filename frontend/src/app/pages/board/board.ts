import {
  CdkDragDrop,
  DragDropModule,
  moveItemInArray,
  transferArrayItem,
} from '@angular/cdk/drag-drop';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { AuthService } from '../../core/auth/auth.service';
import { Board as BoardModel, BoardColumn } from '../../core/board/models';
import { BoardService } from '../../core/board/board.service';
import { ProjectMember, ProjectRole } from '../../core/project/models';
import {
  canChangeStatus,
  canManageIssues as canManageIssuesPermission,
  canManageProjectSettings,
  isOwner as isOwnerPermission,
} from '../../core/project/permissions';
import { ProjectService } from '../../core/project/project.service';
import {
  CreateComponentRequest,
  CreateLabelRequest,
  Issue,
  IssueStatus,
  IssueType,
  Label,
  ProjectComponent,
  UpdateComponentRequest,
  UpdateLabelRequest,
} from '../../core/issue/models';
import { IssueService } from '../../core/issue/issue.service';
import {
  WorkflowScheme,
  WorkflowStatusEdit,
  WorkflowTransitionEdit,
} from '../../core/workflow/models';
import { WorkflowService } from '../../core/workflow/workflow.service';
import { WebsocketService } from '../../core/websocket/websocket.service';
import { ComponentChip } from '../../shared/component-chip/component-chip';
import { IssueCard } from '../../shared/issue-card/issue-card';
import { LabelChip } from '../../shared/label-chip/label-chip';
import { IssueDetailPanel } from './issue-detail-panel/issue-detail-panel';

/** `BoardUpdateMessage.eventType` values that change what this project-wide board renders — a
 *  comment or sprint start/complete doesn't move any card here, so those are ignored. */
const BOARD_RELOAD_EVENT_TYPES = new Set([
  'issue.created',
  'issue.status_changed',
  'issue.assignee_changed',
]);

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
  styleUrls: ['./board.css', './board-workflow.css'],
})
export class Board {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly boardService = inject(BoardService);
  private readonly issueService = inject(IssueService);
  private readonly projectService = inject(ProjectService);
  private readonly workflowService = inject(WorkflowService);
  private readonly authService = inject(AuthService);
  private readonly websocketService = inject(WebsocketService);
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
  readonly canManageIssues = computed(() => canManageIssuesPermission(this.myRole()));
  /** Adding members is Owner/Admin; changing roles or removing members is Owner-only (server-enforced). */
  readonly canAddMembers = computed(() => this.canManageIssues());
  readonly canManageWorkflow = computed(() => canManageProjectSettings(this.myRole()));
  /** A Viewer is fully read-only — no drag-and-drop status changes. */
  readonly canDragStatus = computed(() => canChangeStatus(this.myRole()));
  readonly isOwner = computed(() => isOwnerPermission(this.myRole()));
  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

  /** Members/Workflow/Labels & components are project-settings panels, not board content — while one
   *  is open the board grid and its filters are hidden so the panel reads as its own focused view,
   *  rather than one more thing stacked above the still-visible Kanban board. */
  readonly showingPanel = computed(
    () => this.showMembersPanel() || this.showLabelsPanel() || this.showWorkflowPanel(),
  );

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

  /** The project's workflow scheme, loaded lazily on first opening the Workflow panel (not eagerly
   *  like labels/components, which every board render needs for filtering/chips). */
  readonly workflowScheme = signal<WorkflowScheme | null>(null);
  readonly showWorkflowPanel = signal(false);
  readonly workflowLoading = signal(false);
  /** Single in-flight-request flag shared by every status/transition mutation below — the panel only
   *  ever has one save in flight at a time, so a per-row flag (as labels/components don't have
   *  either) would be unnecessary. */
  readonly savingWorkflow = signal(false);
  readonly workflowError = signal<string | null>(null);
  readonly newStatusName = signal('');
  readonly newStatusCategory = signal<IssueStatus>('TODO');
  /** The 4 fixed categories a status can belong to — never itself configurable. */
  readonly statusCategories: readonly IssueStatus[] = ['TODO', 'BLOCKED', 'IN_PROGRESS', 'DONE'];
  readonly sortedWorkflowStatuses = computed(() =>
    [...(this.workflowScheme()?.statuses ?? [])].sort((a, b) => a.sortOrder - b.sortOrder),
  );
  /** `${fromStatusId}:${toStatusId}` pairs currently checked in the transition matrix — a local
   *  staging area distinct from `workflowScheme().transitions` so every checkbox toggle doesn't fire
   *  its own request; only `saveTransitions()` sends the batch as one PATCH. */
  readonly draftTransitionKeys = signal<Set<string>>(new Set());
  readonly transitionsDirty = computed(() => {
    const saved = new Set(
      (this.workflowScheme()?.transitions ?? []).map((transition) =>
        this.transitionKey(transition.fromStatusId, transition.toStatusId),
      ),
    );
    const draft = this.draftTransitionKeys();
    if (saved.size !== draft.size) {
      return true;
    }
    for (const key of saved) {
      if (!draft.has(key)) {
        return true;
      }
    }
    return false;
  });

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

  /** Reactive, unlike a one-time `route.snapshot.queryParamMap` read: switching panels while
   *  already on `/board` is a same-route, query-param-only navigation, so this component instance
   *  is reused and its constructor never re-runs — a snapshot read would go stale after the first
   *  load. */
  private readonly panelParam = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('panel'))),
    { initialValue: this.route.snapshot.queryParamMap.get('panel') },
  );

  constructor() {
    this.loadBoard();
    this.loadProjectRole();
    this.loadMembers();
    this.loadLabels();
    this.loadComponents();

    // Live board sync: a relevant change made by someone else reloads the whole board (the push
    // payload carries no full Issue to merge — see BoardUpdateMessage's doc). The caller's own
    // change is already reflected via its own optimistic local update, so that echo is ignored.
    this.websocketService
      .watchProjectBoard(this.projectKey)
      .pipe(takeUntilDestroyed())
      .subscribe((message) => {
        if (message.actorId === this.currentUserId()) {
          return;
        }
        if (BOARD_RELOAD_EVENT_TYPES.has(message.eventType)) {
          this.loadBoard();
        }
      });

    // The URL is the single source of truth for which panel is open. `canManageWorkflow()` is a
    // tracked dependency here (not just a guard), so this self-heals if the URL is loaded before
    // `myRole` finishes fetching: the effect reruns once the role arrives and opens the Workflow
    // panel retroactively rather than requiring a page reload.
    effect(() => {
      const panel = this.panelParam();

      this.showMembersPanel.set(panel === 'members');
      this.memberError.set(null);

      this.showLabelsPanel.set(panel === 'labels' && this.canManageIssues());
      this.labelError.set(null);
      this.componentError.set(null);

      const showWorkflow = panel === 'workflow' && this.canManageWorkflow();
      this.showWorkflowPanel.set(showWorkflow);
      this.workflowError.set(null);
      if (showWorkflow && !this.workflowScheme()) {
        this.loadWorkflow();
      }
    });
  }

  /** Navigates to reflect the panel change instead of mutating the panel signals directly, so the
   *  URL stays the single source of truth. `replaceUrl: true` keeps back-button behavior sane —
   *  toggling a panel open/closed doesn't spam browser history. */
  private setPanel(panel: 'members' | 'workflow' | 'labels' | null): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { panel }, replaceUrl: true });
  }

  private loadProjectRole(): void {
    this.projectService.get(this.projectKey).subscribe({
      next: (project) => this.myRole.set(project.callerRole),
      // Leave myRole null on failure — every canManage*/canDragStatus gate then stays false, the
      // safe default.
      error: () => {},
    });
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
      next: (members) => this.members.set(members),
      // Non-critical: the Members panel just has no roster to show.
      error: () => {},
    });
  }

  toggleMembersPanel(): void {
    this.setPanel(this.showMembersPanel() ? null : 'members');
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

    this.issueService.changeStatus(issue.key, targetColumn.statusId).subscribe({
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
      if (column.statusId === updated.statusId) {
        column.issues[index] = updated;
      } else {
        column.issues.splice(index, 1);
        board.columns.find((candidate) => candidate.statusId === updated.statusId)?.issues.push(updated);
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
    this.setPanel(this.showLabelsPanel() ? null : 'labels');
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

  toggleWorkflowPanel(): void {
    this.setPanel(this.showWorkflowPanel() ? null : 'workflow');
  }

  private loadWorkflow(): void {
    this.workflowLoading.set(true);
    this.workflowError.set(null);
    this.workflowService.get(this.projectKey).subscribe({
      next: (scheme) => {
        this.workflowLoading.set(false);
        this.applyWorkflowScheme(scheme);
      },
      error: () => {
        this.workflowLoading.set(false);
        this.workflowError.set('Failed to load the workflow.');
      },
    });
  }

  private applyWorkflowScheme(scheme: WorkflowScheme): void {
    this.workflowScheme.set(scheme);
    this.draftTransitionKeys.set(
      new Set(
        scheme.transitions.map((transition) =>
          this.transitionKey(transition.fromStatusId, transition.toStatusId),
        ),
      ),
    );
  }

  private transitionKey(fromStatusId: string, toStatusId: string): string {
    return `${fromStatusId}:${toStatusId}`;
  }

  private statusEditsFromScheme(scheme: WorkflowScheme): WorkflowStatusEdit[] {
    return scheme.statuses.map((status) => ({
      id: status.id,
      name: status.name,
      category: status.category,
      sortOrder: status.sortOrder,
    }));
  }

  private transitionEditsFromScheme(scheme: WorkflowScheme): WorkflowTransitionEdit[] {
    return scheme.transitions.map((transition) => ({
      id: transition.id,
      fromStatusId: transition.fromStatusId,
      toStatusId: transition.toStatusId,
      name: transition.name,
    }));
  }

  /** Filters the scheme's last-*saved* transitions down to only those whose endpoints are both
   *  still present in `statuses` — so a status being removed can't leave a dangling transition
   *  reference in the submitted request (the backend rejects any transition referencing a status
   *  outside the kept set). */
  private transitionEditsForStatuses(
    statuses: WorkflowStatusEdit[],
    scheme: WorkflowScheme,
  ): WorkflowTransitionEdit[] {
    const keptStatusIds = new Set(
      statuses.map((status) => status.id).filter((id): id is string => id !== null),
    );
    return this.transitionEditsFromScheme(scheme).filter(
      (transition) =>
        keptStatusIds.has(transition.fromStatusId) && keptStatusIds.has(transition.toStatusId),
    );
  }

  /** Every status add/rename/reorder/delete sends the full current status list in one PATCH,
   *  alongside the scheme's last-*saved* transitions — filtered down to the statuses being kept,
   *  never the in-progress transition matrix draft, which only `saveTransitions()` sends. */
  private submitStatusEdits(statuses: WorkflowStatusEdit[]): void {
    const scheme = this.workflowScheme();
    if (!scheme || this.savingWorkflow()) {
      return;
    }
    this.savingWorkflow.set(true);
    this.workflowError.set(null);
    this.workflowService
      .update(this.projectKey, {
        statuses,
        transitions: this.transitionEditsForStatuses(statuses, scheme),
      })
      .subscribe({
        next: (updated) => {
          this.savingWorkflow.set(false);
          this.applyWorkflowScheme(updated);
        },
        error: (err) => {
          this.savingWorkflow.set(false);
          this.workflowError.set(this.workflowErrorMessage(err));
        },
      });
  }

  renameWorkflowStatus(statusId: string, name: string): void {
    const scheme = this.workflowScheme();
    const trimmed = name.trim();
    const current = scheme?.statuses.find((status) => status.id === statusId);
    if (!scheme || !current || !trimmed || trimmed === current.name) {
      return;
    }
    this.submitStatusEdits(
      this.statusEditsFromScheme(scheme).map((status) =>
        status.id === statusId ? { ...status, name: trimmed } : status,
      ),
    );
  }

  recategorizeWorkflowStatus(statusId: string, category: IssueStatus): void {
    const scheme = this.workflowScheme();
    if (!scheme) {
      return;
    }
    this.submitStatusEdits(
      this.statusEditsFromScheme(scheme).map((status) =>
        status.id === statusId ? { ...status, category } : status,
      ),
    );
  }

  moveWorkflowStatus(statusId: string, direction: -1 | 1): void {
    const scheme = this.workflowScheme();
    if (!scheme) {
      return;
    }
    const statuses = this.sortedWorkflowStatuses();
    const index = statuses.findIndex((status) => status.id === statusId);
    const target = index + direction;
    if (index === -1 || target < 0 || target >= statuses.length) {
      return;
    }
    const reordered = [...statuses];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    this.submitStatusEdits(
      reordered.map((status, sortOrder) => ({
        id: status.id,
        name: status.name,
        category: status.category,
        sortOrder,
      })),
    );
  }

  removeWorkflowStatus(statusId: string): void {
    const scheme = this.workflowScheme();
    if (!scheme) {
      return;
    }
    this.submitStatusEdits(
      this.statusEditsFromScheme(scheme).filter((status) => status.id !== statusId),
    );
  }

  submitNewWorkflowStatus(): void {
    const scheme = this.workflowScheme();
    const name = this.newStatusName().trim();
    if (!scheme || !name || this.savingWorkflow()) {
      return;
    }
    this.savingWorkflow.set(true);
    this.workflowError.set(null);
    const statuses: WorkflowStatusEdit[] = [
      ...this.statusEditsFromScheme(scheme),
      { id: null, name, category: this.newStatusCategory(), sortOrder: scheme.statuses.length },
    ];
    this.workflowService
      .update(this.projectKey, {
        statuses,
        transitions: this.transitionEditsForStatuses(statuses, scheme),
      })
      .subscribe({
        next: (updated) => {
          this.savingWorkflow.set(false);
          this.applyWorkflowScheme(updated);
          this.newStatusName.set('');
        },
        error: (err) => {
          this.savingWorkflow.set(false);
          this.workflowError.set(this.workflowErrorMessage(err));
        },
      });
  }

  isTransitionChecked(fromStatusId: string, toStatusId: string): boolean {
    return this.draftTransitionKeys().has(this.transitionKey(fromStatusId, toStatusId));
  }

  toggleDraftTransition(fromStatusId: string, toStatusId: string): void {
    const key = this.transitionKey(fromStatusId, toStatusId);
    this.draftTransitionKeys.update((keys) => {
      const next = new Set(keys);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  saveTransitions(): void {
    const scheme = this.workflowScheme();
    if (!scheme || this.savingWorkflow()) {
      return;
    }
    this.savingWorkflow.set(true);
    this.workflowError.set(null);
    const existingByKey = new Map(
      scheme.transitions.map((transition) => [
        this.transitionKey(transition.fromStatusId, transition.toStatusId),
        transition,
      ]),
    );
    const transitions: WorkflowTransitionEdit[] = [...this.draftTransitionKeys()].map((key) => {
      const [fromStatusId, toStatusId] = key.split(':');
      const existing = existingByKey.get(key);
      return { id: existing?.id ?? null, fromStatusId, toStatusId, name: existing?.name ?? null };
    });
    this.workflowService
      .update(this.projectKey, { statuses: this.statusEditsFromScheme(scheme), transitions })
      .subscribe({
        next: (updated) => {
          this.savingWorkflow.set(false);
          this.applyWorkflowScheme(updated);
        },
        error: (err) => {
          this.savingWorkflow.set(false);
          this.workflowError.set(this.workflowErrorMessage(err));
        },
      });
  }

  private workflowErrorMessage(err: { status?: number }): string {
    if (err.status === 409) {
      return "Can't save: that status is still in use, or a duplicate transition already exists.";
    }
    if (err.status === 400) {
      return "Can't save: a transition can only reference a status that already existed before this change.";
    }
    return 'Failed to save the workflow.';
  }
}
