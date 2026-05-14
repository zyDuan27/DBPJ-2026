import request from '../utils/request'

export function getNotifications(params: Record<string, unknown>) {
  return request.get('/notifications', { params }) as unknown as Promise<any>
}

export function getUnreadCount() {
  return request.get('/notifications/unread-count') as unknown as Promise<any>
}

export function markNotificationRead(notificationId: string | number) {
  return request.patch(`/notifications/${notificationId}/read`) as unknown as Promise<any>
}

export function markAllNotificationsRead() {
  return request.patch('/notifications/read-all') as unknown as Promise<any>
}
