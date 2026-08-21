import { request } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'

export interface NotifyMessage {
  id: number
  title: string
  content: string
  messageType: number
  bizId: number
  isRead: number
  readTime: string | null
  createTime: string
}

export const notifyApi = {
  page: (pageNum = 1, pageSize = 20, unreadOnly?: boolean) =>
    request<PageResult<NotifyMessage>>('/api/v1/notify/messages', {
      query: { pageNum, pageSize, unreadOnly },
    }),
  unreadCount: () => request<number>('/api/v1/notify/messages/unread-count'),
  markRead: (id: number) =>
    request<void>(`/api/v1/notify/messages/${id}/read`, { method: 'PUT' }),
  markAllRead: () => request<void>('/api/v1/notify/messages/read-all', { method: 'PUT' }),
  remove: (id: number) => request<void>(`/api/v1/notify/messages/${id}`, { method: 'DELETE' }),
}
