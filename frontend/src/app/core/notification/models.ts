export type NotificationType = 'MENTION' | 'ASSIGNED' | 'STATUS_CHANGED' | 'COMMENT';

/** Matches `NotificationResponse` exactly — both the `GET /notifications` list shape and the live
 *  push payload on `/user/queue/notifications` (see `NotificationController`/`WebSocketConfig`). */
export interface Notification {
  id: string;
  type: NotificationType;
  issueId: string;
  issueKey: string;
  actorId: string;
  actorDisplayName: string | null;
  readAt: string | null;
  createdAt: string;
}
