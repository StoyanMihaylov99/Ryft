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
  canShareFilter as canShareFilterPermission,
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
import { CreateSavedFilterRequest, IssueSearchRequest, SavedFilter } from '../../core/search/models';
import { SearchService } from '../../core/search/search.service';
import { Sprint } from '../../core/sprint/models';
import { SprintService } from '../../core/sprint/sprint.service';
import {
  WorkflowScheme,
  WorkflowStatusEdit,
  WorkflowTransitionEdit,
} from '../../core/workflow/models';
import { WorkflowService } from '../../core/workflow/workflow.service';
import { WebsocketService } from '../../core/websocket/websocket.service';
import { ComponentChip } from '../../shared/component-chip/component-chip';
import { initials } from '../../shared/initials';
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

/** The Filters panel's six collapsible facet groups — see `filterSectionExpanded` below. */
type FilterFacet = 'assignee' | 'status' | 'type' | 'sprint' | 'labels' | 'components';

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
  styleUrls: ['./board.css', './board-workflow.css', './board-filters.css'],
})
export class Board {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly boardService = inject(BoardService);
  private readonly issueService = inject(IssueService);
  private readonly projectService = inject(ProjectService);
  private readonly workflowService = inject(WorkflowService);
  private readonly sprintService = inject(SprintService);
  private readonly searchService = inject(SearchService);
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
  /** A Viewer may still save a private filter for themselves, just not share it project-wide. */
  readonly canShareFilter = computed(() => canShareFilterPermission(this.myRole()));
  readonly isOwner = computed(() => isOwnerPermission(this.myRole()));
  readonly currentUserId = computed(() => this.authService.currentUser()?.id ?? null);

  /** Members/Workflow/Labels & components are project-settings panels, not board content — while one
   *  is open the board grid and its filters are hidden so the panel reads as its own focused view,
   *  rather than one more thing stacked above the still-visible Kanban board. The Filters panel is
   *  included here for a different reason: it renders its own filtered issue list in place of the
   *  board grid, so showing both at once would just be two competing views of overlapping issues. */
  readonly showingPanel = computed(
    () =>
      this.showMembersPanel() ||
      this.showLabelsPanel() ||
      this.showWorkflowPanel() ||
      this.showFiltersPanel(),
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

  /** Structured, server-side, multi-field filter builder (assignee/status/label/type/sprint) hitting
   *  POST /projects/{projectKey}/search — deliberately separate from labelFilter/componentFilter
   *  above (a client-side, single-value quick filter over the already-loaded board): different
   *  request shape, its own results list, no shared state. Don't try to merge the two. */
  readonly showFiltersPanel = signal(false);
  readonly filterAssigneeIds = signal<string[]>([]);
  readonly filterStatusIds = signal<string[]>([]);
  readonly filterLabelIds = signal<string[]>([]);
  readonly filterComponentIds = signal<string[]>([]);
  readonly filterTypes = signal<IssueType[]>([]);
  readonly filterSprintIds = signal<string[]>([]);
  readonly filterText = signal('');
  /** Each of the six facet `<details>` sections' open/closed state — independent of one another
   *  (not a single-open accordion), so filtering by both Assignee and Status doesn't force
   *  re-expanding one after opening the other. Seeded (not just initialized once) by
   *  `seedFilterSectionExpanded()` — a facet with an existing selection opens by default, an empty
   *  one stays collapsed — but freely toggleable afterwards via `onFilterSectionToggle()`. */
  readonly filterSectionExpanded = signal<Record<FilterFacet, boolean>>({
    assignee: false,
    status: false,
    type: false,
    sprint: false,
    labels: false,
    components: false,
  });
  /** Plain field, not a signal: only used to detect the rising edge of `showFiltersPanel` inside the
   *  panel-param effect below, so `seedFilterSectionExpanded()` reseeds once per fresh panel open
   *  rather than on every unrelated signal this effect also happens to read (`workflowScheme` etc.). */
  private wasFiltersPanelOpen = false;
  /** The issue types a search can filter on — SUBTASK is excluded because the backend never returns
   *  subtasks from this endpoint regardless of what `types` contains. */
  readonly filterTypeOptions: readonly IssueType[] = ['STORY', 'TASK', 'BUG', 'EPIC'];
  /** `null` until the first search runs, distinct from `[]` (a search that ran and matched nothing) —
   *  drives whether the results area shows its initial hint, an empty state, or the results list. */
  readonly searchResults = signal<Issue[] | null>(null);
  readonly searching = signal(false);
  readonly searchError = signal<string | null>(null);
  /** This project's sprints, loaded lazily the first time the Filters panel opens — like
   *  `workflowScheme` above, `null` means "not loaded yet", not "no sprints exist". */
  readonly sprints = signal<Sprint[] | null>(null);

  /** Saved, shareable filters visible to the caller — their own (shared or not) plus every other
   *  member's shared ones, exactly as returned by GET .../filters. Loaded lazily the first time the
   *  Filters panel opens, like `workflowScheme`/`sprints` above; `null` means "not loaded yet". */
  readonly savedFilters = signal<SavedFilter[] | null>(null);
  readonly savedFiltersLoading = signal(false);
  readonly savedFiltersError = signal<string | null>(null);
  readonly newSavedFilterName = signal('');
  readonly newSavedFilterShared = signal(false);
  readonly savingFilter = signal(false);
  /** Drives a visually-hidden `aria-live` region announcing Save/Load/Remove outcomes for the
   *  saved-filters list — those actions only otherwise change on-screen state (a new row, a
   *  repopulated filter builder), which a screen reader has no other way to notice. */
  readonly savedFilterStatusMessage = signal<string | null>(null);

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

      const showFilters = panel === 'filters';
      this.showFiltersPanel.set(showFilters);
      this.searchError.set(null);
      this.savedFiltersError.set(null);
      // The status filter needs the workflow scheme too — open to any project member (unlike the
      // Workflow admin panel above, which only loads it for Owner/Admin), so this doesn't gate on
      // canManageWorkflow().
      if (showFilters && !this.workflowScheme()) {
        this.loadWorkflow();
      }
      if (showFilters && this.sprints() === null) {
        this.loadSprints();
      }
      if (showFilters && this.savedFilters() === null) {
        this.loadSavedFilters();
      }
      // Reseed only on the closed-to-open transition, not on every rerun this effect also does for
      // workflowScheme/sprints/savedFilters arriving — otherwise a section the user manually
      // collapsed mid-session would snap back open the moment one of those unrelated loads resolves.
      if (showFilters && !this.wasFiltersPanelOpen) {
        this.seedFilterSectionExpanded();
      }
      this.wasFiltersPanelOpen = showFilters;
    });
  }

  /** Navigates to reflect the panel change instead of mutating the panel signals directly, so the
   *  URL stays the single source of truth. `replaceUrl: true` keeps back-button behavior sane —
   *  toggling a panel open/closed doesn't spam browser history. */
  private setPanel(panel: 'members' | 'workflow' | 'labels' | 'filters' | null): void {
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

  /** Unlike the Kanban board (where the column header already conveys status) or a single issue's
   *  detail panel, the Filters panel's results list spans every status at once — so each row needs
   *  its own status/assignee context. Resolved from `members()` (already loaded for the assignee
   *  filter chips) rather than a field on `Issue`, which only carries `assigneeId`. */
  assigneeNameFor(issue: Issue): string | null {
    if (!issue.assigneeId) {
      return null;
    }
    return this.members().find((member) => member.userId === issue.assigneeId)?.displayName ?? null;
  }

  assigneeInitialsFor(issue: Issue): string | null {
    const name = this.assigneeNameFor(issue);
    return name ? initials(name) : null;
  }

  /** A short excerpt of `issue.description`, shown only while a free-text search is active — the
   *  backend has no `ts_headline`-style match highlighting to point at *where* the term matched, so
   *  this just surfaces enough of the description for the user to see the connection themselves.
   *  `null` (rendering nothing) both when no search text is active and when the issue has none. */
  descriptionSnippetFor(issue: Issue): string | null {
    if (!this.filterText().trim()) {
      return null;
    }
    const description = issue.description?.trim();
    if (!description) {
      return null;
    }
    const maxLength = 120;
    if (description.length <= maxLength) {
      return description;
    }
    // Trim back to the last whitespace boundary within the slice so the snippet never cuts a
    // word in half — e.g. "...verify the trunc…" would read as broken, "...verify the…" doesn't.
    const truncated = description.slice(0, maxLength).replace(/\s+\S*$/, '').trimEnd();
    return `${truncated}…`;
  }

  /** Single-line restatement of every active filter-builder selection, shown alongside the results
   *  count so it stays visible once the filter-builder form itself has scrolled out of view. `null`
   *  when no filter is active, so the template can skip rendering it entirely. */
  readonly activeFiltersSummary = computed<string | null>(() => {
    const text = this.filterText().trim();
    const parts = [
      text ? `"${text}"` : null,
      this.filterGroupSummary(this.filterAssigneeIds(), 'assignee', (id) =>
        this.members().find((member) => member.userId === id)?.displayName,
      ),
      this.filterGroupSummary(
        this.filterStatusIds(),
        'status',
        (id) => this.workflowScheme()?.statuses.find((status) => status.id === id)?.name,
        'statuses',
      ),
      this.filterGroupSummary(this.filterTypes(), 'type', (type) => type),
      this.filterGroupSummary(this.filterSprintIds(), 'sprint', (id) =>
        this.sprints()?.find((sprint) => sprint.id === id)?.name,
      ),
      this.filterGroupSummary(this.filterLabelIds(), 'label', (id) =>
        this.labels().find((label) => label.id === id)?.name,
      ),
      this.filterGroupSummary(this.filterComponentIds(), 'component', (id) =>
        this.components().find((component) => component.id === id)?.name,
      ),
    ].filter((part): part is string => part !== null);

    return parts.length ? `Filtered by: ${parts.join(' · ')}` : null;
  });

  /** One summary token for a chip filter group — the selected value's own name when exactly one is
   *  chosen, else a compact "N <plural>" count so a heavily multi-selected group (e.g. 5 labels)
   *  can't blow up `activeFiltersSummary`'s single line. `plural` defaults to `${noun}s` (correct for
   *  every group except "status", whose irregular plural is "statuses" — that call site passes it
   *  explicitly rather than this helper guessing English pluralization rules). */
  private filterGroupSummary<T>(
    values: T[],
    noun: string,
    nameFor: (value: T) => string | null | undefined,
    plural: string = `${noun}s`,
  ): string | null {
    if (!values.length) {
      return null;
    }
    if (values.length === 1) {
      return nameFor(values[0]) ?? noun;
    }
    return `${values.length} ${plural}`;
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

  toggleFiltersPanel(): void {
    this.setPanel(this.showFiltersPanel() ? null : 'filters');
  }

  private loadSprints(): void {
    this.sprintService.listForProject(this.projectKey).subscribe({
      next: (sprints) => this.sprints.set(sprints),
      // Non-critical: the sprint filter field just has no options.
      error: () => {},
    });
  }

  private loadSavedFilters(): void {
    this.savedFiltersLoading.set(true);
    this.savedFiltersError.set(null);
    this.searchService.listSavedFilters(this.projectKey).subscribe({
      next: (filters) => {
        this.savedFiltersLoading.set(false);
        this.savedFilters.set(filters);
      },
      error: () => {
        this.savedFiltersLoading.set(false);
        this.savedFiltersError.set('Failed to load saved filters.');
      },
    });
  }

  isFilterValueSelected<T>(values: T[], value: T): boolean {
    return values.includes(value);
  }

  /** Derives each facet `<details>` section's initial open/closed state from whether that facet
   *  already has a selection — called once per fresh panel open (see the constructor effect above),
   *  again whenever `loadSavedFilter()` repopulates the filter signals so a saved filter's populated
   *  facets aren't hidden behind a click, and again from `clearFilters()` so emptied-out sections
   *  collapse back down instead of staying open on nothing. */
  private seedFilterSectionExpanded(): void {
    this.filterSectionExpanded.set({
      assignee: this.filterAssigneeIds().length > 0,
      status: this.filterStatusIds().length > 0,
      type: this.filterTypes().length > 0,
      sprint: this.filterSprintIds().length > 0,
      labels: this.filterLabelIds().length > 0,
      components: this.filterComponentIds().length > 0,
    });
  }

  /** Purely presentational — toggling a `<details>` section never touches the filter signals
   *  themselves, just which chip groups happen to be visible. */
  onFilterSectionToggle(facet: FilterFacet, expanded: boolean): void {
    this.filterSectionExpanded.update((state) => ({ ...state, [facet]: expanded }));
  }

  toggleFilterAssignee(userId: string): void {
    this.filterAssigneeIds.update((ids) => this.toggleFilterValue(ids, userId));
  }

  toggleFilterStatus(statusId: string): void {
    this.filterStatusIds.update((ids) => this.toggleFilterValue(ids, statusId));
  }

  toggleFilterLabel(labelId: string): void {
    this.filterLabelIds.update((ids) => this.toggleFilterValue(ids, labelId));
  }

  toggleFilterComponent(componentId: string): void {
    this.filterComponentIds.update((ids) => this.toggleFilterValue(ids, componentId));
  }

  toggleFilterType(type: IssueType): void {
    this.filterTypes.update((types) => this.toggleFilterValue(types, type));
  }

  toggleFilterSprint(sprintId: string): void {
    this.filterSprintIds.update((ids) => this.toggleFilterValue(ids, sprintId));
  }

  private toggleFilterValue<T>(values: T[], value: T): T[] {
    return values.includes(value) ? values.filter((v) => v !== value) : [...values, value];
  }

  /** The current filter-builder selections as an `IssueSearchRequest` body — shared by `runSearch()`
   *  and `submitSaveFilter()` so the two never drift apart on which fields count as "selected". */
  private currentSearchRequest(): IssueSearchRequest {
    const text = this.filterText().trim();
    return {
      ...(this.filterAssigneeIds().length ? { assigneeIds: this.filterAssigneeIds() } : {}),
      ...(this.filterStatusIds().length ? { statusIds: this.filterStatusIds() } : {}),
      ...(this.filterLabelIds().length ? { labelIds: this.filterLabelIds() } : {}),
      ...(this.filterComponentIds().length ? { componentIds: this.filterComponentIds() } : {}),
      ...(this.filterTypes().length ? { types: this.filterTypes() } : {}),
      ...(this.filterSprintIds().length ? { sprintIds: this.filterSprintIds() } : {}),
      ...(text ? { text } : {}),
    };
  }

  /** Runs an ad-hoc structured search against POST /projects/{projectKey}/search. Nothing here is
   *  persisted unless the caller explicitly saves it — see `submitSaveFilter()`. */
  runSearch(): void {
    this.searching.set(true);
    this.searchError.set(null);
    this.searchService.search(this.projectKey, this.currentSearchRequest()).subscribe({
      next: (results) => {
        this.searching.set(false);
        this.searchResults.set(results);
      },
      error: (err) => {
        this.searching.set(false);
        this.searchError.set(this.searchErrorMessage(err));
      },
    });
  }

  /** `text` is the only `IssueSearchRequest` field the server validates (`@Size(max = 200)`), so a
   *  400 here — normally unreachable given the text field's `maxlength` — means the search text
   *  exceeds that cap. */
  private searchErrorMessage(err: { status?: number }): string {
    if (err.status === 400) {
      return 'Search text is too long — keep it under 200 characters.';
    }
    return 'Failed to search issues.';
  }

  clearFilters(): void {
    this.filterAssigneeIds.set([]);
    this.filterStatusIds.set([]);
    this.filterLabelIds.set([]);
    this.filterComponentIds.set([]);
    this.filterTypes.set([]);
    this.filterSprintIds.set([]);
    this.filterText.set('');
    this.searchResults.set(null);
    this.searchError.set(null);
    this.seedFilterSectionExpanded();
  }

  /** Populates the filter builder from a saved filter's query and runs the search immediately, so
   *  loading a saved filter is a single click straight to results — mirrors `clearFilters()`'s set
   *  of fields, just filled in instead of emptied. */
  loadSavedFilter(filter: SavedFilter): void {
    this.filterAssigneeIds.set(filter.query.assigneeIds ?? []);
    this.filterStatusIds.set(filter.query.statusIds ?? []);
    this.filterLabelIds.set(filter.query.labelIds ?? []);
    this.filterComponentIds.set(filter.query.componentIds ?? []);
    this.filterTypes.set(filter.query.types ?? []);
    this.filterSprintIds.set(filter.query.sprintIds ?? []);
    this.filterText.set(filter.query.text ?? '');
    this.seedFilterSectionExpanded();
    this.runSearch();
    this.savedFilterStatusMessage.set(`Loaded '${filter.name}'.`);
  }

  /** Owner-only, server-enforced (403 for anyone else) — the template only renders this action for
   *  filters the caller owns, mirroring `deleteLabel`/`deleteComponent`'s optimistic-remove-then-
   *  revert-on-error convention. */
  deleteSavedFilter(filterId: string): void {
    this.savedFiltersError.set(null);
    const previous = this.savedFilters();
    const removed = previous?.find((filter) => filter.id === filterId);
    this.savedFilters.set((previous ?? []).filter((filter) => filter.id !== filterId));
    this.searchService.deleteSavedFilter(this.projectKey, filterId).subscribe({
      next: () => {
        if (removed) {
          this.savedFilterStatusMessage.set(`Filter '${removed.name}' removed.`);
        }
      },
      error: () => {
        this.savedFilters.set(previous);
        this.savedFiltersError.set('Failed to delete the saved filter.');
      },
    });
  }

  /** Saves the current filter-builder selections as a named, optionally-shared filter. The share
   *  checkbox is hidden for a Viewer (`canShareFilter()`), but this still sends whatever
   *  `newSavedFilterShared()` holds rather than silently coercing it — so a stale `true` (e.g. a
   *  role downgrade mid-session) surfaces the server's 403 instead of failing silently. */
  submitSaveFilter(): void {
    const name = this.newSavedFilterName().trim();
    if (!name || this.savingFilter()) {
      return;
    }
    this.savingFilter.set(true);
    this.savedFiltersError.set(null);
    const request: CreateSavedFilterRequest = {
      name,
      query: this.currentSearchRequest(),
      isShared: this.newSavedFilterShared(),
    };
    this.searchService.createSavedFilter(this.projectKey, request).subscribe({
      next: (filter) => {
        this.savingFilter.set(false);
        this.savedFilters.update((filters) =>
          [...(filters ?? []), filter].sort((a, b) => a.name.localeCompare(b.name)),
        );
        this.savedFilterStatusMessage.set(`Filter '${filter.name}' saved.`);
        this.newSavedFilterName.set('');
        this.newSavedFilterShared.set(false);
      },
      error: (err) => {
        this.savingFilter.set(false);
        this.savedFiltersError.set(this.saveFilterErrorMessage(err));
      },
    });
  }

  private saveFilterErrorMessage(err: { status?: number }): string {
    if (err.status === 403) {
      return 'Only project members other than Viewers can share a filter — save it as private instead.';
    }
    if (err.status === 409) {
      return 'You already have a saved filter with that name.';
    }
    return 'Failed to save the filter.';
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
