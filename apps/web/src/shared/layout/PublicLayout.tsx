import type { ReactNode } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/**
 * 公共布局（匿名浏览）：克制导航（品牌 + 课程 + 登录/Demo），
 * 供 Landing 与公开课程浏览使用；登录后自动切换为 AppLayout。
 */
export function PublicLayout({ children }: { children?: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const demoEnabled = import.meta.env.DEV || import.meta.env.VITE_DEMO_ENABLED === 'true'

  return (
    <div className="ed-shell">
      <header className="ed-nav">
        <div className="ed-nav-inner">
          <Link to="/" className="ed-nav-brand" aria-label="云思学习平台首页">
            云思学习
          </Link>
          <nav className="ed-nav-links" aria-label="公开导航">
            <Link to="/courses" className="ed-nav-link">
              课程
            </Link>
          </nav>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            {demoEnabled && !isAuthenticated && (
              <Link to="/?demo=1" className="ed-btn ed-btn-sm ed-btn-accent">
                进入 Demo
              </Link>
            )}
            <Link to="/login" className="ed-btn ed-btn-sm">
              {isAuthenticated ? '进入学习' : '登录'}
            </Link>
          </div>
        </div>
      </header>
      <main className="ed-main">{children ?? <Outlet />}</main>
      <footer className="ed-footer">
        <Link to="/service" className="ed-link-muted" style={{ marginRight: 16 }}>
          联系客服
        </Link>
        云思学习 · 职业资格考试培训平台
      </footer>
    </div>
  )
}
