import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Comment } from '../../../core/comment/models';
import { Issue } from '../../../core/issue/models';
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
    status: 'TODO',
    priority: 'MEDIUM',
    assigneeId: null,
    reporterId: 'u1',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: null,
    resolvedAt: null,
    ...overrides,
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

  function flushLoad(issueValue: Issue, comments: Comment[] = []): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`).flush(issueValue);
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/comments`).flush(comments);
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
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    component.updateDraftTitle('New title');

    expect(component.draftTitle()).toBe('New title');
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
  });

  it('saves the staged title when Save is clicked and emits updated', () => {
    fixture.componentRef.setInput('canManage', true);
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
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    component.save();

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/status`);
  });

  it('does not stage or save title changes when the caller cannot manage issues', () => {
    // canManage defaults to false
    flushLoad(issue());

    component.updateDraftTitle('New title');
    component.save();

    expect(component.draftTitle()).toBe('Title');
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
  });

  it('changes status via the dedicated status endpoint when Save is clicked', () => {
    flushLoad(issue());

    component.updateDraftStatus('DONE');
    component.save();

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`).flush(issue({ status: 'DONE' }));

    expect(component.issue()?.status).toBe('DONE');
  });

  it('saves managed-field and status changes together with a single Save click', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());
    const updatedSpy = vi.fn();
    component.updated.subscribe(updatedSpy);

    component.updateDraftTitle('New title');
    component.updateDraftStatus('DONE');
    component.save();

    // Guards the sequential (switchMap) design: the status request must not fire until the
    // PATCH response comes back, unlike a concurrent (e.g. forkJoin) implementation would.
    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1/status`);
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`).flush(issue({ title: 'New title' }));
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`).flush(issue({ title: 'New title', status: 'DONE' }));

    expect(component.issue()?.title).toBe('New title');
    expect(component.issue()?.status).toBe('DONE');
    expect(updatedSpy).toHaveBeenCalledTimes(1);
  });

  it('clears hasUnsavedChanges when an edited field is reverted back to its original value', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    component.updateDraftTitle('New title');
    expect(component.hasUnsavedChanges()).toBe(true);

    component.updateDraftTitle('Title');
    expect(component.hasUnsavedChanges()).toBe(false);
  });

  it('omits a blank title from the patch while still saving other dirty fields', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    component.updateDraftTitle('   ');
    component.updateDraftPriority('HIGH');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);
    expect(req.request.body).toEqual({ priority: 'HIGH' });
    req.flush(issue({ priority: 'HIGH' }));

    expect(component.issue()?.priority).toBe('HIGH');
  });

  it('shows an inline validation message when the title is blank', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    expect(component.titleIsBlank()).toBe(false);

    component.updateDraftTitle('   ');
    fixture.detectChanges();

    expect(component.titleIsBlank()).toBe(true);
    const message = fixture.debugElement.query(By.css('.field-error'));
    expect(message.nativeElement.textContent).toContain('Title cannot be empty');
  });

  it('applies the persisted PATCH result but keeps the failed status staged when changeStatus errors', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    component.updateDraftTitle('New title');
    component.updateDraftStatus('DONE');
    component.save();

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`).flush(issue({ title: 'New title' }));
    httpMock
      .expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`)
      .flush('Server error', { status: 500, statusText: 'Server Error' });

    expect(component.issue()?.title).toBe('New title');
    expect(component.issue()?.status).toBe('TODO');
    expect(component.draftTitle()).toBe('New title');
    expect(component.draftStatus()).toBe('DONE');
    expect(component.saving()).toBe(false);
    expect(component.errorMessage()).toBe('Status change failed; other changes were saved.');
    expect(component.hasUnsavedChanges()).toBe(true);

    fixture.detectChanges();
    const saveButton = fixture.debugElement.query(By.css('.save-btn')).nativeElement as HTMLButtonElement;
    expect(saveButton.disabled).toBe(false);
  });

  it('ignores a stale save response after the panel has switched to a different issue', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());
    const updatedSpy = vi.fn();
    component.updated.subscribe(updatedSpy);

    component.updateDraftTitle('New title');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);

    fixture.componentRef.setInput('issueKey', 'TRK-2');
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2`).flush(issue({ key: 'TRK-2', title: 'Other title' }));
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2/comments`).flush([]);

    req.flush(issue({ title: 'New title' }));

    expect(component.issue()?.key).toBe('TRK-2');
    expect(component.issue()?.title).toBe('Other title');
    expect(updatedSpy).not.toHaveBeenCalled();
  });

  it('clears the saving state after a stale save response so Save is not stuck disabled', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    component.updateDraftTitle('New title');
    component.save();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`);

    fixture.componentRef.setInput('issueKey', 'TRK-2');
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2`).flush(issue({ key: 'TRK-2', title: 'Other title' }));
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2/comments`).flush([]);

    req.flush(issue({ title: 'New title' }));

    expect(component.saving()).toBe(false);

    component.updateDraftTitle('Yet another title');
    fixture.detectChanges();

    const saveButton = fixture.debugElement.query(By.css('.save-btn')).nativeElement as HTMLButtonElement;
    expect(saveButton.disabled).toBe(false);
  });

  it('leaves Save clickable with no staged changes, like the Comment button, and disables it only while a save is in flight', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());
    fixture.detectChanges();

    const saveButton = fixture.debugElement.query(By.css('.save-btn')).nativeElement as HTMLButtonElement;
    expect(saveButton.disabled).toBe(false);

    component.updateDraftTitle('New title');
    component.save();
    fixture.detectChanges();
    expect(saveButton.disabled).toBe(true);

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`).flush(issue({ title: 'New title' }));
    fixture.detectChanges();
    expect(saveButton.disabled).toBe(false);
  });

  it('resets staged edits when a new issue is loaded', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    component.updateDraftTitle('Unsaved edit');
    expect(component.draftTitle()).toBe('Unsaved edit');

    fixture.componentRef.setInput('issueKey', 'TRK-2');
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2`).flush(issue({ key: 'TRK-2', title: 'Other title' }));
    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-2/comments`).flush([]);

    expect(component.draftTitle()).toBe('Other title');
  });

  it('posts a new comment and appends it to the list', () => {
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

  it('emits deleted after successfully deleting the issue', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const deletedSpy = vi.fn();
    component.deleted.subscribe(deletedSpy);

    component.deleteIssue();

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`).flush(null);

    expect(deletedSpy).toHaveBeenCalledWith('TRK-1');
  });

  it('does not delete the issue when the caller cannot manage issues', () => {
    // canManage defaults to false
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
});
