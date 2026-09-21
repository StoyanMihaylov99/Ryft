import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { Notification } from '../../core/notification/models';
import { NotificationService } from '../../core/notification/notification.service';
import { NotificationBell } from './notification-bell';

function notification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 'n1',
    type: 'COMMENT',
    issueId: 'i1',
    issueKey: 'TRK-142',
    actorId: 'u1',
    actorDisplayName: 'Ada',
    readAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('NotificationBell', () => {
  let fixture: ComponentFixture<NotificationBell>;
  let component: NotificationBell;
  let notifications: ReturnType<typeof signal<Notification[]>>;
  let markRead: ReturnType<typeof vi.fn>;
  let markAllRead: ReturnType<typeof vi.fn>;
  let router: Router;

  beforeEach(async () => {
    notifications = signal<Notification[]>([]);
    markRead = vi.fn();
    markAllRead = vi.fn();

    await TestBed.configureTestingModule({
      imports: [NotificationBell],
      providers: [
        provideRouter([]),
        {
          provide: NotificationService,
          useValue: {
            notifications,
            unreadCount: () => notifications().filter((n) => n.readAt === null).length,
            markRead,
            markAllRead,
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(NotificationBell);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function bellButton(): HTMLButtonElement {
    return fixture.debugElement.query(By.css('.bell-btn')).nativeElement as HTMLButtonElement;
  }

  it('shows no badge when there are no unread notifications', () => {
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('.badge'))).toBeNull();
  });

  it('shows the unread count in the badge', () => {
    notifications.set([notification({ id: 'n1' }), notification({ id: 'n2' })]);
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.badge')).nativeElement.textContent.trim()).toBe('2');
  });

  it('caps the displayed badge count at "9+"', () => {
    notifications.set(Array.from({ length: 12 }, (_, i) => notification({ id: `n${i}` })));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.badge')).nativeElement.textContent.trim()).toBe('9+');
  });

  it('toggles the panel open and closed on click', () => {
    expect(fixture.debugElement.query(By.css('.panel'))).toBeNull();

    bellButton().click();
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('.panel'))).not.toBeNull();

    bellButton().click();
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('.panel'))).toBeNull();
  });

  it('closes the panel on Escape', () => {
    component.toggle();
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.panel'))).toBeNull();
  });

  it('closes the panel on an outside click', () => {
    component.toggle();
    fixture.detectChanges();

    document.body.click();
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.panel'))).toBeNull();
  });

  it('does not close the panel when clicking inside it', () => {
    notifications.set([notification()]);
    component.toggle();
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.panel')).nativeElement.click();
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.panel'))).not.toBeNull();
  });

  it('shows an empty state when there are no notifications', () => {
    component.toggle();
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.empty-state')).nativeElement.textContent).toContain(
      'No notifications yet.',
    );
  });

  it('disables "Mark all read" when there are no unread notifications', () => {
    notifications.set([notification({ readAt: '2024-01-01T00:00:00Z' })]);
    component.toggle();
    fixture.detectChanges();

    const button = fixture.debugElement.query(By.css('.mark-all-btn')).nativeElement as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  it('calls markAllRead when "Mark all read" is clicked', () => {
    notifications.set([notification()]);
    component.toggle();
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.mark-all-btn')).nativeElement.click();

    expect(markAllRead).toHaveBeenCalled();
  });

  it('marks an unread notification read and navigates to its project board when clicked', () => {
    notifications.set([notification({ issueKey: 'TRK-142' })]);
    component.toggle();
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.notification-item')).nativeElement.click();

    expect(markRead).toHaveBeenCalledWith('n1');
    expect(router.navigate).toHaveBeenCalledWith(['/projects', 'TRK', 'board']);
  });

  it('does not re-mark an already-read notification, but still navigates', () => {
    notifications.set([notification({ readAt: '2024-01-01T00:00:00Z' })]);
    component.toggle();
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.notification-item')).nativeElement.click();

    expect(markRead).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/projects', 'TRK', 'board']);
  });

  it('closes the panel after selecting a notification', () => {
    notifications.set([notification()]);
    component.toggle();
    fixture.detectChanges();

    fixture.debugElement.query(By.css('.notification-item')).nativeElement.click();
    fixture.detectChanges();

    expect(component.isOpen()).toBe(false);
  });
});
