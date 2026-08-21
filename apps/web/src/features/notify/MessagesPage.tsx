import { useCallback, useEffect, useState } from 'react'
import { friendlyMessage } from '../../shared/api/types'
import { notifyApi, type NotifyMessage } from './api'

const TYPE_LABEL: Record<number, string> = { 1: '成绩通知', 2: '开考提醒', 3: '系统消息' }

/** 站内消息：列表/未读数/单条已读/全部已读/删除/空状态/未读视觉。严格本人数据（后端 @CurrentUser）。 */
export default function MessagesPage() {
  const [items, setItems] = useState<NotifyMessage[]>([])
  const [unread, setUnread] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [page, count] = await Promise.all([notifyApi.page(1, 50), notifyApi.unreadCount()])
      setItems(page.list)
      setUnread(count)
      // 通知全局导航刷新未读徽标（避免 60s 轮询延迟）
      window.dispatchEvent(new Event('yunsie:unread-changed'))
    } catch (e) {
      setError(friendlyMessage(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const markRead = async (id: number) => {
    try {
      await notifyApi.markRead(id)
      await load()
    } catch (e) {
      setError(friendlyMessage(e))
    }
  }

  const markAll = async () => {
    try {
      await notifyApi.markAllRead()
      await load()
    } catch (e) {
      setError(friendlyMessage(e))
    }
  }

  const remove = async (id: number) => {
    try {
      await notifyApi.remove(id)
      await load()
    } catch (e) {
      setError(friendlyMessage(e))
    }
  }

  return (
    <div className="ed-container ed-page">
      <header
        className="ed-page-head"
        style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 24, flexWrap: 'wrap' }}
      >
        <div>
          <h1 className="ed-title">站内消息</h1>
          <p className="ed-lead">成绩通知与开考提醒 · 未读 {unread} 条</p>
        </div>
        {unread > 0 && (
          <button type="button" className="ed-btn ed-btn-sm ed-btn-ghost" onClick={() => void markAll()}>
            全部已读
          </button>
        )}
      </header>

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ marginBottom: 32 }}>
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => void load()}>
            重试
          </button>
        </div>
      )}

      {loading ? (
        <div className="ed-card ed-card-pad">
          <div className="ed-skeleton" style={{ height: 24, maxWidth: '55%', marginBottom: 20 }} aria-hidden="true" />
          <div className="ed-skeleton" style={{ height: 20, marginBottom: 12 }} aria-hidden="true" />
          <div className="ed-skeleton" style={{ height: 20, maxWidth: '80%' }} aria-hidden="true" />
        </div>
      ) : items.length === 0 ? (
        <div className="ed-empty">
          <div className="ed-empty-icon" aria-hidden="true">
            🔔
          </div>
          <p className="ed-empty-title">暂无消息</p>
          <p className="ed-empty-hint">考试成绩与开考提醒会出现在这里</p>
        </div>
      ) : (
        <div className="ed-card">
          <ul className="ed-list">
            {items.map((m) => (
              <li
                key={m.id}
                className="ed-row"
                style={{
                  padding: '24px 28px',
                  alignItems: 'flex-start',
                  gap: 20,
                  flexWrap: 'wrap',
                  background: m.isRead === 0 ? 'var(--ed-bg-subtle)' : 'transparent',
                }}
              >
                <div className="ed-row-main">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8, flexWrap: 'wrap' }}>
                    <span
                      className={`ed-badge ${m.messageType === 1 ? 'ed-badge-pass' : m.messageType === 2 ? 'ed-badge-accent' : ''}`}
                    >
                      {TYPE_LABEL[m.messageType] ?? '消息'}
                    </span>
                    <span className="ed-row-title" style={{ margin: 0 }}>
                      {m.title}
                    </span>
                    {m.isRead === 0 && <span className="msg-dot" aria-label="未读" />}
                  </div>
                  <p className="ed-row-sub" style={{ margin: 0 }}>
                    {m.content}
                  </p>
                  <span className="ed-caption" style={{ display: 'block', marginTop: 8 }}>
                    {m.createTime}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 8, flexShrink: 0, flexWrap: 'wrap' }}>
                  {m.isRead === 0 && (
                    <button type="button" className="ed-btn ed-btn-sm ed-btn-ghost" onClick={() => void markRead(m.id)}>
                      标记已读
                    </button>
                  )}
                  <button type="button" className="ed-btn ed-btn-sm ed-btn-ghost" onClick={() => void remove(m.id)}>
                    删除
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
