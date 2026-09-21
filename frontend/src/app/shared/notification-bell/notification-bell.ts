import { DatePipe } from '@angular/common';
import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Notification } from '../../core/notification/models';
import { notificationText, projectKeyFromIssueKey } from '../../core/notification/notification-display';
import { NotificationService } from '../../core/notification/notification.service';

@Component({
  imports: [DatePipe],
  selector: 'app-notification-bell',
  templateUrl: './notification-bell.html',
  styleUrl: './notification-bell.css',
})
export class NotificationBell {
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly elementRef: ElementRef<HTMLElement> = inject(ElementRef);

  readonly notifications = this.notificationService.notifications;
  readonly unreadCount = this.notificationService.unreadCount;
  /** The badge shows a capped "9+" rather than an ever-growing exact count. */
  readonly badgeText = computed(() => (this.unreadCount() > 9 ? '9+' : `${this.unreadCount()}`));

  readonly isOpen = signal(false);

  toggle(): void {
    this.isOpen.update((open) => !open);
  }

  close(): void {
    this.isOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }

  /** A click on the bell button itself is inside this component's own host, so it never triggers
   *  this — only a click genuinely outside the bell/panel closes it. */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.isOpen() && !this.elementRef.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  displayText(notification: Notification): string {
    return notificationText(notification);
  }

  markAllRead(): void {
    this.notificationService.markAllRead();
  }

  selectNotification(notification: Notification): void {
    if (notification.readAt === null) {
      this.notificationService.markRead(notification.id);
    }
    this.close();
    this.router.navigate(['/projects', projectKeyFromIssueKey(notification.issueKey), 'board']);
  }
}
