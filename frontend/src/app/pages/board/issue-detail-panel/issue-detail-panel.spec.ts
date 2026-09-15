import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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

  it('saves the title on blur and emits updated', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());
    const updatedSpy = vi.fn();
    component.updated.subscribe(updatedSpy);

    component.saveTitle('New title');

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1`).flush(issue({ title: 'New title' }));

    expect(component.issue()?.title).toBe('New title');
    expect(updatedSpy).toHaveBeenCalled();
  });

  it('does not call the API when the title is unchanged', () => {
    fixture.componentRef.setInput('canManage', true);
    flushLoad(issue());

    component.saveTitle('Title');

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
  });

  it('does not call the API to save the title when the caller cannot manage issues', () => {
    // canManage defaults to false
    flushLoad(issue());

    component.saveTitle('New title');

    httpMock.expectNone(`${environment.apiBaseUrl}/issues/TRK-1`);
  });

  it('changes status via the dedicated status endpoint', () => {
    flushLoad(issue());

    component.changeStatus('DONE');

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/status`).flush(issue({ status: 'DONE' }));

    expect(component.issue()?.status).toBe('DONE');
  });

  it('posts a new comment and appends it to the list', () => {
    flushLoad(issue());

    component.newCommentBody.set('Nice work');
    component.submitComment();

    httpMock.expectOne(`${environment.apiBaseUrl}/issues/TRK-1/comments`).flush({
      id: 'c1',
      issueId: 'p1',
      authorId: 'u1',
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
});
