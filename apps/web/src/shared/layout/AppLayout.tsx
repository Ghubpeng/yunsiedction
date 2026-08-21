import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../shared/auth/AuthContext'
import { notifyApi } from '../../features/notify/api'

const NAV_ITEMS = [
  { to: '/', label: '学习', icon: '▤', end: true },
  { to: '/courses', label: '课程', icon: '▶' },
  { to: '/practice', label: '练习', icon: '✎' },
  { to: '/exams', label: '考试', icon: '⌁' },
  { to: '/profile', label: '档案', icon: '◉' },
]

/**
 * 应用布局：桌面顶部导航 / 移动端底部导航（不能简单缩放桌面布局）。
 * 未读数徽标数据来自真实 notify API。
 */
export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    let cancelled = false
    const refresh = () => {
      notifyApi
        .unreadCount()
        .then((n) => {
          if (!cancelled) setUnread(n)
        })
        .catch(() => {
          /* 徽标失败不打扰 */
        })
    }
    refresh()
    const timer = window.setInterval(refresh, 60_000)
    // 消息页操作后立即刷新徽标
    window.addEventListener('yunsie:unread-changed', refresh)
    return () => {
      cancelled = true
      window.clearInterval(timer)
      window.removeEventListener('yunsie:unread-changed', refresh)
    }
  }, [])

  const onLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell ed-shell">
      <header className="top-nav">
        <div className="top-nav-inner">
          <NavLink to="/" className="brand" aria-label="云思学习平台首页">
            云思学习
          </NavLink>
          <nav className="nav-links" aria-label="主导航">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => `nav-link ${isActive ? 'nav-link-active' : ''}`}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="nav-actions">
            <NavLink to="/messages" className="nav-icon" aria-label={`站内消息，未读 ${unread} 条`}>
              <span aria-hidden="true">🔔</span>
              {unread > 0 && <span className="unread-dot">{unread > 99 ? '99+' : unread}</span>}
            </NavLink>
            <span className="nav-user" title={user?.username ?? ''}>
              {user?.nickname || user?.username || '…'}
            </span>
            <button type="button" className="ed-text-btn" onClick={onLogout}>
              退出
            </button>
          </div>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
      <nav className="bottom-nav" aria-label="移动端主导航">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `bottom-item ${isActive ? 'bottom-item-active' : ''}`}
          >
            <span className="bottom-icon" aria-hidden="true">
              {item.icon}
            </span>
            <span className="bottom-label">{item.label}</span>
          </NavLink>
        ))}
        <NavLink to="/messages" className={({ isActive }) => `bottom-item ${isActive ? 'bottom-item-active' : ''}`}>
          <span className="bottom-icon" aria-hidden="true">
            🔔{unread > 0 && <span className="unread-dot">{unread > 99 ? '99+' : unread}</span>}
          </span>
          <span className="bottom-label">消息</span>
        </NavLink>
      </nav>
    </div>
  )
}
