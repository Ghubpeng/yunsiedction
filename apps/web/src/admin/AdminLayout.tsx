import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../shared/auth/AuthContext'
import { usePermissions } from '../shared/auth/PermissionContext'

/** 管理端菜单（permission 驱动显示；含子路径前缀匹配高亮） */
const MENU = [
  { to: '/admin', label: '仪表盘', icon: '▦', perm: null, end: true },
  { to: '/admin/users', label: '用户管理', icon: '👤', perm: 'sys:user:view' },
  { to: '/admin/rbac', label: '角色与权限', icon: '⚿', perm: 'sys:role:view' },
  { to: '/admin/certificates', label: '证书与分类', icon: '▣', perm: 'certificate:read' },
  { to: '/admin/subjects', label: '知识体系', icon: '⌘', perm: 'subject:read' },
  { to: '/admin/questions', label: '题库管理', icon: '✎', perm: 'question:question:read' },
  { to: '/admin/exams', label: '考试管理', icon: '⌁', perm: 'exam:exam:read' },
  { to: '/admin/courses', label: '课程管理', icon: '▶', perm: 'course:course:read' },
  { to: '/admin/students', label: '学员档案', icon: '◉', perm: 'learn:profile:view' },
  { to: '/admin/service', label: '客服配置', icon: '✉', perm: 'sys:service:config' },
]

export function AdminLayout() {
  const { user, logout } = useAuth()
  const { has, loading } = usePermissions()
  const navigate = useNavigate()

  const visible = MENU.filter((m) => !m.perm || has(m.perm))

  const onLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="admin-shell">
      <aside className="admin-side">
        <NavLink to="/admin" className="admin-brand">
          云思管理台
        </NavLink>
        {!loading && (
          <nav className="admin-nav" aria-label="管理导航">
            {visible.map((m) => (
              <NavLink
                key={m.to}
                to={m.to}
                end={m.end}
                className={({ isActive }) => `admin-nav-item ${isActive ? 'admin-nav-active' : ''}`}
              >
                <span aria-hidden="true">{m.icon}</span>
                {m.label}
              </NavLink>
            ))}
          </nav>
        )}
        <div className="admin-side-foot">
          <NavLink to="/" className="admin-nav-item">
            <span aria-hidden="true">↩</span>
            返回用户端
          </NavLink>
          <button type="button" className="admin-nav-item" onClick={onLogout}>
            <span aria-hidden="true">⏻</span>
            退出登录（{user?.nickname || user?.username}）
          </button>
        </div>
      </aside>
      <main className="admin-main">
        <Outlet />
      </main>
    </div>
  )
}
