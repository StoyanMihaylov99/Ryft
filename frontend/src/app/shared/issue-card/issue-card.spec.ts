import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Issue } from '../../core/issue/models';
import { IssueCard } from './issue-card';

function issue(overrides: Partial<Issue> = {}): Issue {
  return {
    id: 'i1',
    projectId: 'p1',
    key: 'TRK-1',
    type: 'TASK',
    title: 'Fix the thing',
    description: null,
    status: 'TODO',
    priority: 'MEDIUM',
    storyPoints: null,
    assigneeId: null,
    reporterId: 'u1',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: null,
    resolvedAt: null,
    sprintId: null,
    parentId: null,
    ...overrides,
  };
}

describe('IssueCard', () => {
  let fixture: ComponentFixture<IssueCard>;

  function render(value: Issue): void {
    fixture = TestBed.createComponent(IssueCard);
    fixture.componentRef.setInput('issue', value);
    fixture.detectChanges();
  }

  it('renders the key, type, title and priority', () => {
    render(issue());

    expect(fixture.debugElement.query(By.css('.issue-key')).nativeElement.textContent).toContain(
      'TRK-1',
    );
    expect(
      fixture.debugElement.query(By.css('.issue-type-badge')).nativeElement.textContent,
    ).toContain('TASK');
    expect(fixture.debugElement.query(By.css('.issue-title')).nativeElement.textContent).toContain(
      'Fix the thing',
    );
    expect(
      fixture.debugElement.query(By.css('.issue-priority')).nativeElement.textContent,
    ).toContain('MEDIUM');
  });

  it('renders the story-points badge when storyPoints is set', () => {
    render(issue({ storyPoints: 5 }));

    const badge = fixture.debugElement.query(By.css('.issue-story-points-badge'));
    expect(badge.nativeElement.textContent).toContain('5 pts');
  });

  it('does not render the story-points badge when storyPoints is null', () => {
    render(issue({ storyPoints: null }));

    expect(fixture.debugElement.query(By.css('.issue-story-points-badge'))).toBeNull();
  });

  it('gives an Epic-typed issue a distinct badge treatment', () => {
    render(issue({ type: 'EPIC' }));

    const badge = fixture.debugElement.query(By.css('.issue-type-badge'));
    expect(badge.classes['type-epic']).toBe(true);
    expect(badge.nativeElement.textContent).toContain('EPIC');
  });

  it('renders the Epic chip when epicTitle is set', () => {
    fixture = TestBed.createComponent(IssueCard);
    fixture.componentRef.setInput('issue', issue({ parentId: 'e1' }));
    fixture.componentRef.setInput('epicTitle', 'Big epic');
    fixture.detectChanges();

    const chip = fixture.debugElement.query(By.css('.epic-chip'));
    expect(chip.nativeElement.textContent).toContain('Big epic');
  });

  it('does not render the Epic chip when epicTitle is null', () => {
    render(issue({ parentId: null }));

    expect(fixture.debugElement.query(By.css('.epic-chip'))).toBeNull();
  });
});
