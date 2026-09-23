import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, RouterLinkActive, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Notification } from '../../core/notification/models';
import { NotificationService } from '../../core/notification/notification.service';
import { ProjectRole } from '../../core/project/models';
import { Sidebar } from './sidebar';

function createSidebar(projectKey: string | null): ComponentFixture<Sidebar> {
  TestBed.configureTestingModule({
    imports: [Sidebar],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { paramMap: convertToParamMap(projectKey ? { projectKey } : {}) },
        },
      },
      { provide: AuthService, useValue: { currentUser: () => null } },
      // NotificationBell (rendered in the footer) injects NotificationService directly, whose real
      // implementation transitively constructs WebsocketService/AuthService — a fake here keeps
      // this an isolated Sidebar test.
      {
        provide: NotificationService,
        useValue: {
          notifications: signal<Notification[]>([]),
          unreadCount: () => 0,
          markRead: () => {},
          markAllRead: () => {},
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(Sidebar);
  fixture.detectChanges();
  return fixture;
}

function subItemLabels(fixture: ComponentFixture<Sidebar>): string[] {
  return fixture.debugElement
    .queryAll(By.css('.nav-project .nav-item--sub'))
    .map((item) => item.nativeElement.textContent.trim());
}

describe('Sidebar', () => {
  it('has no project section outside a project (e.g. /projects, /workspace)', () => {
    const fixture = createSidebar(null);

    expect(fixture.componentInstance.projectKey).toBeNull();
    expect(fixture.debugElement.query(By.css('.nav-project'))).toBeNull();
  });

  it('shows the project key as the section header when a project is in scope', () => {
    const fixture = createSidebar('TRK');

    expect(fixture.componentInstance.projectKey).toBe('TRK');
    expect(fixture.debugElement.query(By.css('.nav-section-header')).nativeElement.textContent).toContain(
      'TRK',
    );
  });

  it('lists Board/Sprints/Backlog/Scrum board/Filters/Members for a role with no elevated permissions', () => {
    const fixture = createSidebar('TRK');
    fixture.componentRef.setInput('myRole', 'VIEWER' satisfies ProjectRole);
    fixture.detectChanges();

    expect(subItemLabels(fixture)).toEqual([
      'Board',
      'Sprints',
      'Backlog',
      'Scrum board',
      'Filters',
      'Members',
    ]);
  });

  it('adds Workflow and Labels & components for an Owner/Admin', () => {
    const fixture = createSidebar('TRK');
    fixture.componentRef.setInput('myRole', 'OWNER' satisfies ProjectRole);
    fixture.detectChanges();

    expect(subItemLabels(fixture)).toEqual([
      'Board',
      'Sprints',
      'Backlog',
      'Scrum board',
      'Filters',
      'Members',
      'Workflow',
      'Labels & components',
    ]);
  });

  it('resolves the Board link to the project board route', () => {
    const fixture = createSidebar('TRK');

    const links = fixture.debugElement.queryAll(By.css('.nav-project .nav-item--sub'));
    const boardLink = links.find((link) => link.nativeElement.textContent.includes('Board'));

    expect(boardLink!.nativeElement.getAttribute('href')).toBe('/projects/TRK/board');
  });

  it("resolves the Members link to the board route with a 'members' panel query param", () => {
    const fixture = createSidebar('TRK');

    const links = fixture.debugElement.queryAll(By.css('.nav-project .nav-item--sub'));
    const membersLink = links.find((link) => link.nativeElement.textContent.includes('Members'));

    expect(membersLink!.nativeElement.getAttribute('href')).toBe('/projects/TRK/board?panel=members');
  });

  it('applies an exact paths/queryParams match to every project nav link, so Board and a panel link never highlight together', () => {
    const fixture = createSidebar('TRK');

    const activeDirectives = fixture.debugElement
      .queryAll(By.css('.nav-project .nav-item--sub'))
      .map((link) => link.injector.get(RouterLinkActive));

    expect(activeDirectives).toHaveLength(6);
    for (const directive of activeDirectives) {
      expect(directive.routerLinkActiveOptions).toEqual({
        paths: 'exact',
        queryParams: 'exact',
        matrixParams: 'ignored',
        fragment: 'ignored',
      });
    }
  });
});
