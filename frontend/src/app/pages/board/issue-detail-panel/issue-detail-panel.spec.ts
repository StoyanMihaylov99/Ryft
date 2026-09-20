import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Comment } from '../../../core/comment/models';
import { EpicProgress, Issue, Label, ProjectComponent } from '../../../core/issue/models';
import { WorkflowScheme } from '../../../core/workflow/models';
import { environment } from '../../../../environments/environment';
import { IssueDetailPanel } from './issue-detail-panel';

function issue(overrides: Partial<Issue> = {}): Issue {
  return {
    id: 'i1',
    projectId: 'p1',
    key: 'TRK-1',
    type: 'TASK',
    title: 'Title',
    description: null,
    statusId: 'todo',
    statusName: 'To Do',
    statusCategory: 'TODO',
    callerCanEdit: true,
    priority: 'MEDIUM',
    storyPoints: null,
    assigneeId: null,
    reporterId: 'u1',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: null,
    resolvedAt: null,
    sprintId: null,
    parentId: null,
    labels: [],
    components: [],
    ...overrides,
  };
}

function label(overrides: Partial<Label> = {}): Label {
  return { id: 'l1', projectId: 'p1', name: 'Frontend', color: '#4287f5', ...overrides };
}

function projectComponent(overrides: Partial<ProjectComponent> = {}): ProjectComponent {
  return { id: 'c1', projectId: 'p1', name: 'API', ...overrides };
}

function comment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 'c1',
    issueId: 'p1',
    authorId: 'u1',
    authorDisplayName: 'Ada',
    authorAvatarUrl: null,
    body: 'Nice work',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: null,
    ...overrides,
  };
}

function workflowScheme(): WorkflowScheme {
  return {
    id: 'scheme-1',
    projectId: 'p1',
    name: 'Default',
    statuses: [
      { id: 'todo', name: 'To Do', category: 'TODO', sortOrder: 0 },
      { id: 'inprogress', name: 'In Progress', category: 'IN_PROGRESS', sortOrder: 1 },
      { id: 'done', name: 'Done', category: 'DONE', sortOrder: 2 },
    ],
    transitions: [
      {
        id: 't1',
        fromStatusId: 'todo',
        fromStatusName: 'To Do',
        toStatusId: 'inprogress',
        toStatusName: 'In Progress',
        name: null,
      },
      {
        id: 't2',
        fromStatusId: 'inprogress',
        fromStatusName: 'In Progress',
        toStatusId: 'done',
        toStatusName: 'Done',
        name: null,
      },
    ],
  };
}

describe('IssueDetailPanel', () => {
  let fixture: ComponentFixture<IssueDetailPanel>;
  let component: IssueDetailPanel;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IssueDetailPanel],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(IssueDetailPanel);
    fixture.componentRef.setInput('issueKey', 'TRK-1');
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  /** A STORY/TASK/BUG issue (the default `issue()` type) also triggers a subtasks fetch, and an
   *  EPIC triggers a progress fetch — both flushed here with an empty/zeroed default unless the
   *  caller passes one. */
  function flushLoad(
    issueValue: Issue,
    comments: Comment[] = [],
    subtasks: Issue[] = [],
    epicProgress: EpicProgress = { totalCount: 0, doneCount: 0, percentDone: 0 },
  ): void {
    const key = issueValue.key;
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/${key}`).flush(issueValue);
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/${key}/comments`).flush(comments);
    if (issueValue.type === 'STORY' || issueValue.type === 'TASK' || issueValue.type === 'BUG') {
      httpMock.expectOne(`${environment.apiBaseUrl}/issues/${key}/subtasks`).flush(subtasks);
    }
    if (issueValue.type === 'EPIC') {
      httpMock.expectOne(`${environment.apiBaseUrl}/issues/${key}/progress`).flush(epicProgress);
    }
  }

  function flushEpics(
    epics: Issue[] = [],
    labels: Label[] = [],
    components: ProjectComponent[] = [],
    workflow: WorkflowScheme = workflowScheme(),
  ): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/issues`).flush(epics);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/labels`).flush(labels);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/components`).flush(components);
    httpMock.expectOne(`${environment.apiBaseUrl}/projects/TRK/workflow`).flush(workflow);
  }

  it('loads the issue and its comments on creation', () => {
    flushLoad(issue());

    expect(component.loading()).toBe(false);
    expect(component.issue()?.key).toBe('TRK-1');
  });

  it('emits closed when close is called', () => {
    flushLoad(issue());
    const closedSpy = vi.fn();
    component.closed.subscribe(closedSpy);

    component.close();

    expect(closedSpy).toHaveBeenCalled();
  });

  it('stages the title edit locally without calling the API until Save is clicked', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    component.updateDraftTitle('New title');

    expect(component.draftTitle()).toBe('New title');
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
  });

  it('saves the staged title when Save is clicked and emits updated', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());
    const updatedSpy = vi.fn();
    component.updated.subscribe(updatedSpy);

    component.updateDraftTitle('New title');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
    expect(req.request.body).toEqual({ title: 'New title' });
    req.flush(issue({ title: 'New title' }));

    expect(component.issue()?.title).toBe('New title');
    expect(updatedSpy).toHaveBeenCalled();
  });

  it('does not call the API when there are no staged changes', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    component.save();

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/status`);
  });

  it('does not stage or save title changes when the caller cannot manage issues', () => {
    flushLoad(issue({ callerCanEdit: false }));

    component.updateDraftTitle('New title');
    component.save();

    expect(component.draftTitle()).toBe('Title');
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
  });

  it('changes status via the dedicated status endpoint when Save is clicked', () => {
    fixture.componentRef.setInput('myRole', 'MEMBER');
    flushLoad(issue());

    component.updateDraftStatus('done');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`);
    expect(req.request.body).toEqual({ statusId: 'done' });
    req.flush(issue({ statusId: 'done', statusName: 'Done', statusCategory: 'DONE' }));

    expect(component.issue()?.statusId).toBe('done');
  });

  it('a Viewer cannot stage a status change', () => {
    fixture.componentRef.setInput('myRole', 'VIEWER');
    flushLoad(issue());

    component.updateDraftStatus('done');

    expect(component.draftStatusId()).toBe('todo');
    expect(component.hasUnsavedChanges()).toBe(false);
  });

  it('saves managed-field and status changes together with a single Save click', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());
    const updatedSpy = vi.fn();
    component.updated.subscribe(updatedSpy);

    component.updateDraftTitle('New title');
    component.updateDraftStatus('done');
    component.save();

    // Guards the sequential (switchMap) design: the status request must not fire until the
    // PATCH response comes back, unlike a concurrent (e.g. forkJoin) implementation would.
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/status`);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1`)
      .flush(issue({ title: 'New title' }));
    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`)
      .flush(issue({ title: 'New title', statusId: 'done', statusName: 'Done', statusCategory: 'DONE' }));

    expect(component.issue()?.title).toBe('New title');
    expect(component.issue()?.statusId).toBe('done');
    expect(updatedSpy).toHaveBeenCalledTimes(1);
  });

  it('clears hasUnsavedChanges when an edited field is reverted back to its original value', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    component.updateDraftTitle('New title');
    expect(component.hasUnsavedChanges()).toBe(true);

    component.updateDraftTitle('Title');
    expect(component.hasUnsavedChanges()).toBe(false);
  });

  it('omits a blank title from the patch while still saving other dirty fields', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    component.updateDraftTitle('   ');
    component.updateDraftPriority('HIGH');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
    expect(req.request.body).toEqual({ priority: 'HIGH' });
    req.flush(issue({ priority: 'HIGH' }));

    expect(component.issue()?.priority).toBe('HIGH');
  });

  it('saves the staged story points when Save is clicked and emits updated', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());
    const updatedSpy = vi.fn();
    component.updated.subscribe(updatedSpy);

    component.updateDraftStoryPoints(5);
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
    expect(req.request.body).toEqual({ storyPoints: 5 });
    req.flush(issue({ storyPoints: 5 }));

    expect(component.issue()?.storyPoints).toBe(5);
    expect(updatedSpy).toHaveBeenCalled();
  });

  it('omits storyPoints from the patch when saving other fields without changing it', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    component.updateDraftPriority('HIGH');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
    expect(req.request.body).toEqual({ priority: 'HIGH' });
    expect(req.request.body.storyPoints).toBeUndefined();
    req.flush(issue({ priority: 'HIGH' }));
  });

  it('shows an inline validation message when the title is blank', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    expect(component.titleIsBlank()).toBe(false);

    component.updateDraftTitle('   ');
    fixture.detectChanges();

    expect(component.titleIsBlank()).toBe(true);
    const message = fixture.debugElement.query(By.css('.field-error'));
    expect(message.nativeElement.textContent).toContain('Title cannot be empty');
  });

  it('applies the persisted PATCH result but keeps the failed status staged when changeStatus errors', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    component.updateDraftTitle('New title');
    component.updateDraftStatus('done');
    component.save();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1`)
      .flush(issue({ title: 'New title' }));
    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`)
      .flush('Server error', { status: 500, statusText: 'Server Error' });

    expect(component.issue()?.title).toBe('New title');
    expect(component.issue()?.statusId).toBe('todo');
    expect(component.draftTitle()).toBe('New title');
    expect(component.draftStatusId()).toBe('done');
    expect(component.saving()).toBe(false);
    expect(component.errorMessage()).toBe('Status change failed; other changes were saved.');
    expect(component.hasUnsavedChanges()).toBe(true);

    fixture.detectChanges();
    const saveButton = fixture.debugElement.query(By.css('.save-btn'))
      .nativeElement as HTMLButtonElement;
    expect(saveButton.disabled).toBe(false);
  });

  it('ignores a stale save response after the panel has switched to a different issue', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());
    const updatedSpy = vi.fn();
    component.updated.subscribe(updatedSpy);

    component.updateDraftTitle('New title');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);

    fixture.componentRef.setInput('issueKey', 'TRK-2');
    fixture.detectChanges();
    flushLoad(issue({ key: 'TRK-2', title: 'Other title' }));

    req.flush(issue({ title: 'New title' }));

    expect(component.issue()?.key).toBe('TRK-2');
    expect(component.issue()?.title).toBe('Other title');
    expect(updatedSpy).not.toHaveBeenCalled();
  });

  it('clears the saving state after a stale save response so Save is not stuck disabled', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    component.updateDraftTitle('New title');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);

    fixture.componentRef.setInput('issueKey', 'TRK-2');
    fixture.detectChanges();
    flushLoad(issue({ key: 'TRK-2', title: 'Other title' }));

    req.flush(issue({ title: 'New title' }));

    expect(component.saving()).toBe(false);

    component.updateDraftTitle('Yet another title');
    fixture.detectChanges();

    const saveButton = fixture.debugElement.query(By.css('.save-btn'))
      .nativeElement as HTMLButtonElement;
    expect(saveButton.disabled).toBe(false);
  });

  it('leaves Save clickable with no staged changes, like the Comment button, and disables it only while a save is in flight', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());
    fixture.detectChanges();

    const saveButton = fixture.debugElement.query(By.css('.save-btn'))
      .nativeElement as HTMLButtonElement;
    expect(saveButton.disabled).toBe(false);

    component.updateDraftTitle('New title');
    component.save();
    fixture.detectChanges();
    expect(saveButton.disabled).toBe(true);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1`)
      .flush(issue({ title: 'New title' }));
    fixture.detectChanges();
    expect(saveButton.disabled).toBe(false);
  });

  it('resets staged edits when a new issue is loaded', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());

    component.updateDraftTitle('Unsaved edit');
    expect(component.draftTitle()).toBe('Unsaved edit');

    fixture.componentRef.setInput('issueKey', 'TRK-2');
    fixture.detectChanges();
    flushLoad(issue({ key: 'TRK-2', title: 'Other title' }));

    expect(component.draftTitle()).toBe('Other title');
  });

  it('posts a new comment and appends it to the list', () => {
    fixture.componentRef.setInput('myRole', 'MEMBER');
    flushLoad(issue());

    component.newCommentBody.set('Nice work');
    component.submitComment();

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/comments`).flush({
      id: 'c1',
      issueId: 'p1',
      authorId: 'u1',
      authorDisplayName: 'Ada',
      authorAvatarUrl: null,
      body: 'Nice work',
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: null,
    });

    expect(component.comments()).toHaveLength(1);
    expect(component.newCommentBody()).toBe('');
  });

  it('a Viewer cannot post a comment', () => {
    fixture.componentRef.setInput('myRole', 'VIEWER');
    flushLoad(issue());

    component.newCommentBody.set('Nice work');
    component.submitComment();

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/comments`);
    expect(component.comments()).toHaveLength(0);
  });

  it('a Viewer cannot save an edited comment', () => {
    fixture.componentRef.setInput('myRole', 'VIEWER');
    flushLoad(issue(), [comment()]);

    component.startEditComment(comment());
    component.editingCommentBody.set('Edited body');
    component.saveComment('c1');

    httpMock.expectNone(`${environment.apiBaseUrl}/comments/c1`);
    expect(component.comments()).toEqual([comment()]);
  });

  it('a Viewer cannot delete a comment', () => {
    fixture.componentRef.setInput('myRole', 'VIEWER');
    flushLoad(issue(), [comment()]);

    component.deleteComment('c1');

    httpMock.expectNone(`${environment.apiBaseUrl}/comments/c1`);
    expect(component.comments()).toEqual([comment()]);
  });

  it('emits deleted after successfully deleting the issue', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue());
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const deletedSpy = vi.fn();
    component.deleted.subscribe(deletedSpy);

    component.deleteIssue();

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`).flush(null);

    expect(deletedSpy).toHaveBeenCalledWith('TRK-1');
  });

  it('does not delete the issue when the caller cannot manage issues', () => {
    // myRole defaults to null, so canDeleteIssue() is false
    flushLoad(issue());
    const deletedSpy = vi.fn();
    component.deleted.subscribe(deletedSpy);

    component.deleteIssue();

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
    expect(deletedSpy).not.toHaveBeenCalled();
  });

  it('toggles the inline delete-issue confirmation instead of a native confirm()', () => {
    flushLoad(issue());

    expect(component.confirmingDeleteIssue()).toBe(false);
    component.requestDeleteIssue();
    expect(component.confirmingDeleteIssue()).toBe(true);
    component.cancelDeleteIssue();
    expect(component.confirmingDeleteIssue()).toBe(false);
  });

  it('toggles the inline delete-comment confirmation instead of a native confirm()', () => {
    flushLoad(issue());

    expect(component.confirmingDeleteCommentId()).toBeNull();
    component.requestDeleteComment('c1');
    expect(component.confirmingDeleteCommentId()).toBe('c1');
    component.cancelDeleteComment();
    expect(component.confirmingDeleteCommentId()).toBeNull();
  });

  it("does not request the project's epics when no projectKey is provided", () => {
    flushLoad(issue());

    expect(component.epics()).toEqual([]);
  });

  it("loads the project's epics when projectKey is provided", () => {
    fixture.componentRef.setInput('projectKey', 'TRK');
    fixture.detectChanges();
    flushLoad(issue());
    flushEpics([issue({ id: 'e1', key: 'TRK-9', type: 'EPIC', title: 'Epic 1' })]);

    expect(component.epics().map((e) => e.key)).toEqual(['TRK-9']);
  });

  it('shows the Epic field for a Story/Task/Bug issue and hides it for an Epic issue', () => {
    fixture.componentRef.setInput('projectKey', 'TRK');
    fixture.detectChanges();
    flushLoad(issue({ type: 'STORY' }));
    flushEpics();
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.epic-label'))).not.toBeNull();

    fixture.componentRef.setInput('issueKey', 'TRK-2');
    fixture.detectChanges();
    flushLoad(issue({ key: 'TRK-2', type: 'EPIC' }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.epic-label'))).toBeNull();
  });

  it('shows the linked-epic chip once the linked epic is resolved from the loaded epics', () => {
    fixture.componentRef.setInput('projectKey', 'TRK');
    fixture.detectChanges();
    flushLoad(issue({ type: 'STORY', parentId: 'e1' }));
    flushEpics([issue({ id: 'e1', key: 'TRK-9', type: 'EPIC', title: 'Epic 1' })]);
    fixture.detectChanges();

    const chip = fixture.debugElement.query(By.css('.epic-chip'));
    expect(chip.nativeElement.textContent).toContain('Epic 1');
  });

  it('saves the staged Epic link when Save is clicked and emits updated', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    fixture.componentRef.setInput('projectKey', 'TRK');
    fixture.detectChanges();
    flushLoad(issue({ type: 'STORY' }));
    flushEpics([issue({ id: 'e1', key: 'TRK-9', type: 'EPIC', title: 'Epic 1' })]);
    const updatedSpy = vi.fn();
    component.updated.subscribe(updatedSpy);

    component.updateDraftParentId('e1');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
    expect(req.request.body).toEqual({ parentId: 'e1' });
    req.flush(issue({ type: 'STORY', parentId: 'e1' }));

    expect(component.issue()?.parentId).toBe('e1');
    expect(updatedSpy).toHaveBeenCalled();
  });

  it('does not stage or save Epic link changes when the caller cannot manage issues', () => {
    flushLoad(issue({ type: 'STORY', callerCanEdit: false }));

    component.updateDraftParentId('e1');
    component.save();

    expect(component.draftParentId()).toBeNull();
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
  });

  it('surfaces a failed save the same way as any other rejected field, when the backend rejects the Epic link', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue({ type: 'STORY' }));

    component.updateDraftParentId('e1');
    component.save();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1`)
      .flush(
        { message: 'Epic must belong to the same project' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(component.errorMessage()).toBe('Failed to save changes.');
  });

  it('always offers "No epic" as an option, including on an issue that already has one linked', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    fixture.componentRef.setInput('projectKey', 'TRK');
    fixture.detectChanges();
    flushLoad(issue({ type: 'STORY', parentId: 'e1' }));
    flushEpics([issue({ id: 'e1', key: 'TRK-9', type: 'EPIC', title: 'Epic 1' })]);
    fixture.detectChanges();

    const options = fixture.debugElement.queryAll(By.css('.epic-label select option'));
    expect(options.map((option) => option.nativeElement.value)).toEqual(['', 'e1']);
  });

  it('does not send a no-op parentId clear when the user picks "No epic" on an already-linked issue, and warns instead', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    fixture.componentRef.setInput('projectKey', 'TRK');
    fixture.detectChanges();
    flushLoad(issue({ type: 'STORY', parentId: 'e1', priority: 'MEDIUM' }));
    flushEpics([issue({ id: 'e1', key: 'TRK-9', type: 'EPIC', title: 'Epic 1' })]);

    component.updateDraftParentId(null);
    expect(component.attemptingToUnlinkEpic()).toBe(true);

    fixture.detectChanges();
    const warning = fixture.debugElement.query(By.css('#epic-unlink-warning'));
    expect(warning.nativeElement.textContent).toContain("Epics can't be unlinked yet");

    // A real, savable change alongside the unlink attempt must still be sent, without parentId.
    component.updateDraftPriority('HIGH');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
    expect(req.request.body).toEqual({ priority: 'HIGH' });
    req.flush(issue({ type: 'STORY', parentId: 'e1', priority: 'HIGH' }));

    expect(component.issue()?.parentId).toBe('e1');
  });

  it('hides the Subtasks section for an Epic issue', () => {
    flushLoad(issue({ type: 'EPIC' }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.subtasks'))).toBeNull();
  });

  it('fetches and renders progress for an Epic issue', () => {
    flushLoad(issue({ type: 'EPIC' }), [], [], {
      totalCount: 7,
      doneCount: 3,
      percentDone: 300 / 7,
    });
    fixture.detectChanges();

    expect(component.epicProgressLabel()).toBe('3/7 done');
    expect(component.epicProgressPercent()).toBe(43);

    const bar = fixture.debugElement.query(By.css('.progress-track'));
    expect(bar.attributes['aria-valuenow']).toBe('43');
    expect(bar.attributes['aria-valuemin']).toBe('0');
    expect(bar.attributes['aria-valuemax']).toBe('100');
    expect(bar.attributes['aria-valuetext']).toBe('3 of 7 done');
    const label = fixture.debugElement.query(By.css('.epic-progress-label'));
    expect(label.nativeElement.textContent).toContain('3/7 done');
  });

  it('shows a zeroed progress bar and "0/0 done" for an Epic with no linked issues, without NaN', () => {
    flushLoad(issue({ type: 'EPIC' }), [], [], { totalCount: 0, doneCount: 0, percentDone: 0 });
    fixture.detectChanges();

    expect(component.epicProgressLabel()).toBe('0/0 done');
    expect(component.epicProgressPercent()).toBe(0);
    const fill = fixture.debugElement.query(By.css('.progress-fill')).nativeElement as HTMLElement;
    expect(fill.style.width).toBe('0%');
  });

  it('surfaces an error and does not crash when fetching Epic progress fails', () => {
    const key = 'TRK-1';
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/${key}`).flush(issue({ type: 'EPIC' }));
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/${key}/comments`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/${key}/progress`)
      .flush('Server error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(component.epicProgressError()).toBe('Failed to load progress.');
    expect(component.epicProgress()).toBeNull();
    const error = fixture.debugElement.query(By.css('.epic-progress .field-error'));
    expect(error.nativeElement.textContent).toContain('Failed to load progress.');
  });

  it('does not request progress for a non-Epic issue', () => {
    flushLoad(issue({ type: 'STORY' }));

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/progress`);
    expect(component.epicProgress()).toBeNull();
  });

  it('does not show the progress section for a non-Epic issue', () => {
    flushLoad(issue({ type: 'STORY' }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.epic-progress'))).toBeNull();
  });

  it('hides the Subtasks section for a Subtask issue', () => {
    flushLoad(issue({ type: 'SUBTASK', parentId: 'p1' }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.subtasks'))).toBeNull();
  });

  it('hides the Epic field for a Subtask issue, showing the parent chip instead', () => {
    flushLoad(issue({ type: 'SUBTASK', parentId: 'p1' }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.epic-label'))).toBeNull();
  });

  it('loads and lists subtasks for a Story/Task/Bug issue', () => {
    flushLoad(
      issue({ type: 'STORY' }),
      [],
      [issue({ id: 's1', key: 'TRK-2', type: 'SUBTASK', title: 'Sub one', parentId: 'i1' })],
    );
    fixture.detectChanges();

    const rows = fixture.debugElement.queryAll(By.css('.subtask-row'));
    expect(rows.length).toBe(1);
    expect(rows[0].nativeElement.textContent).toContain('TRK-2');
    expect(rows[0].nativeElement.textContent).toContain('Sub one');
  });

  it('shows a done/total progress label once subtasks are loaded', () => {
    flushLoad(
      issue({ type: 'STORY' }),
      [],
      [
        issue({
          id: 's1',
          key: 'TRK-2',
          type: 'SUBTASK',
          statusId: 'done',
          statusName: 'Done',
          statusCategory: 'DONE',
        }),
        issue({ id: 's2', key: 'TRK-3', type: 'SUBTASK' }),
      ],
    );

    expect(component.subtaskProgressLabel()).toBe('1/2 done');
  });

  it('creates a subtask with just a title and appends it to the list', () => {
    fixture.componentRef.setInput('myRole', 'OWNER');
    flushLoad(issue({ type: 'STORY' }));
    fixture.detectChanges();

    component.newSubtaskTitle.set('New subtask');
    component.submitSubtask();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/subtasks`);
    expect(req.request.body).toEqual({ title: 'New subtask' });
    req.flush(
      issue({ id: 's1', key: 'TRK-2', type: 'SUBTASK', title: 'New subtask', parentId: 'i1' }),
    );

    expect(component.subtasks().map((subtask) => subtask.key)).toEqual(['TRK-2']);
    expect(component.newSubtaskTitle()).toBe('');
  });

  it('hides the add-subtask form when the caller cannot manage issues', () => {
    flushLoad(issue({ type: 'STORY' }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.add-subtask'))).toBeNull();
  });

  it('does not create a subtask when the caller cannot manage issues', () => {
    flushLoad(issue({ type: 'STORY' }));

    component.newSubtaskTitle.set('Nope');
    component.submitSubtask();

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/subtasks`);
  });

  it("changes a subtask's status via the status endpoint using its own key", () => {
    fixture.componentRef.setInput('myRole', 'MEMBER');
    const subtask = issue({
      id: 's1',
      key: 'TRK-2',
      type: 'SUBTASK',
      parentId: 'i1',
    });
    flushLoad(issue({ type: 'STORY' }), [], [subtask]);
    fixture.detectChanges();

    component.changeSubtaskStatus(subtask, 'done');

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2/status`);
    expect(req.request.body).toEqual({ statusId: 'done' });
    req.flush({ ...subtask, statusId: 'done', statusName: 'Done', statusCategory: 'DONE' });

    expect(
      component.subtasks().find((candidate) => candidate.key === 'TRK-2')?.statusId,
    ).toBe('done');
  });

  it('reverts the optimistic status update when changing a subtask status fails', () => {
    fixture.componentRef.setInput('myRole', 'MEMBER');
    const subtask = issue({
      id: 's1',
      key: 'TRK-2',
      type: 'SUBTASK',
      parentId: 'i1',
    });
    flushLoad(issue({ type: 'STORY' }), [], [subtask]);

    component.changeSubtaskStatus(subtask, 'done');
    expect(component.subtasks()[0].statusId).toBe('done');

    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-2/status`)
      .flush('Server error', { status: 500, statusText: 'Server Error' });

    expect(component.subtasks()[0].statusId).toBe('todo');
    expect(component.subtaskError()).toContain('TRK-2');
  });

  it('a Viewer cannot change a subtask status', () => {
    fixture.componentRef.setInput('myRole', 'VIEWER');
    const subtask = issue({ id: 's1', key: 'TRK-2', type: 'SUBTASK', parentId: 'i1' });
    flushLoad(issue({ type: 'STORY' }), [], [subtask]);

    component.changeSubtaskStatus(subtask, 'done');

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-2/status`);
    expect(component.subtasks()[0].statusId).toBe('todo');
  });

  it('drills into a subtask when its row is clicked, replacing the currently displayed issue', () => {
    const subtask = issue({
      id: 's1',
      key: 'TRK-2',
      type: 'SUBTASK',
      title: 'Sub one',
      parentId: 'i1',
    });
    flushLoad(issue({ type: 'STORY' }), [], [subtask]);
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.subtask-link')).nativeElement.click();

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2`).flush(subtask);
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2/comments`).flush([]);

    expect(component.issue()?.key).toBe('TRK-2');
    expect(component.issue()?.title).toBe('Sub one');
  });

  it("shows the parent chip for a Subtask once the parent is resolved from the project's loaded issues", () => {
    fixture.componentRef.setInput('projectKey', 'TRK');
    fixture.detectChanges();
    flushLoad(issue({ type: 'SUBTASK', parentId: 'p1' }));
    flushEpics([issue({ id: 'p1', key: 'TRK-5', type: 'STORY', title: 'Parent story' })]);
    fixture.detectChanges();

    const chip = fixture.debugElement.query(By.css('.parent-chip'));
    expect(chip.nativeElement.textContent).toContain('Parent story');
    expect(chip.nativeElement.textContent).toContain('TRK-5');
  });

  it("hides the parent chip for a Subtask when the parent isn't among the project's loaded issues", () => {
    fixture.componentRef.setInput('projectKey', 'TRK');
    fixture.detectChanges();
    flushLoad(issue({ type: 'SUBTASK', parentId: 'p1' }));
    flushEpics();
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.parent-chip'))).toBeNull();
  });

  describe('labels and components', () => {
    it("renders every project label/component as a toggle chip, pre-selecting the issue's own", () => {
      fixture.componentRef.setInput('myRole', 'OWNER');
      fixture.componentRef.setInput('projectKey', 'TRK');
      fixture.detectChanges();
      flushLoad(issue({ labels: [label()], components: [] }));
      flushEpics([], [label(), label({ id: 'l2', name: 'Backend' })], [projectComponent()]);
      fixture.detectChanges();

      const labelChips = fixture.debugElement.queryAll(By.css('app-label-chip button'));
      expect(labelChips.length).toBe(2);
      expect(labelChips[0].attributes['aria-pressed']).toBe('true');
      expect(labelChips[1].attributes['aria-pressed']).toBe('false');

      const componentChip = fixture.debugElement.query(By.css('app-component-chip button'));
      expect(componentChip.attributes['aria-pressed']).toBe('false');
    });

    it('stages a label toggle locally without calling the API until Save is clicked', () => {
      fixture.componentRef.setInput('myRole', 'OWNER');
      fixture.componentRef.setInput('projectKey', 'TRK');
      fixture.detectChanges();
      flushLoad(issue());
      flushEpics([], [label()]);

      component.toggleDraftLabel('l1');

      expect(component.isLabelSelected('l1')).toBe(true);
      httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
    });

    it('sends the newly selected labelIds when Save is clicked', () => {
      fixture.componentRef.setInput('myRole', 'OWNER');
      fixture.componentRef.setInput('projectKey', 'TRK');
      fixture.detectChanges();
      flushLoad(issue());
      flushEpics([], [label()]);

      component.toggleDraftLabel('l1');
      component.save();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
      expect(req.request.body).toEqual({ labelIds: ['l1'] });
      req.flush(issue({ labels: [label()] }));

      expect(component.issue()?.labels.map((l) => l.id)).toEqual(['l1']);
    });

    it('sends componentIds: [] (not omitted) when the user clears a previously-set selection', () => {
      fixture.componentRef.setInput('myRole', 'OWNER');
      fixture.componentRef.setInput('projectKey', 'TRK');
      fixture.detectChanges();
      flushLoad(issue({ components: [projectComponent()] }));
      flushEpics([], [], [projectComponent()]);

      component.toggleDraftComponent('c1');
      component.save();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
      expect(req.request.body).toEqual({ componentIds: [] });
      req.flush(issue({ components: [] }));

      expect(component.issue()?.components).toEqual([]);
    });

    it('omits labelIds/componentIds entirely when the user never touches the selection', () => {
      fixture.componentRef.setInput('myRole', 'OWNER');
      fixture.componentRef.setInput('projectKey', 'TRK');
      fixture.detectChanges();
      flushLoad(issue({ labels: [label()] }));
      flushEpics([], [label()]);

      component.updateDraftPriority('HIGH');
      component.save();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
      expect(req.request.body).toEqual({ priority: 'HIGH' });
      expect(req.request.body.labelIds).toBeUndefined();
      expect(req.request.body.componentIds).toBeUndefined();
      req.flush(issue({ labels: [label()], priority: 'HIGH' }));
    });

    it('does not toggle labels/components when the caller cannot manage issues', () => {
      fixture.componentRef.setInput('projectKey', 'TRK');
      fixture.detectChanges();
      flushLoad(issue({ callerCanEdit: false }));
      flushEpics([], [label()]);

      component.toggleDraftLabel('l1');

      expect(component.isLabelSelected('l1')).toBe(false);
    });
  });
});
