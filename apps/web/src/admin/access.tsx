import type { ReactNode } from 'react'
import { usePermissions } from '../shared/auth/PermissionContext'
import { Spinner } from '../shared/ui/primitives'

/** 管理端入口守卫：登录用户 + 至少拥有任一管理权限点；否则显示阻断页（服务端仍为最终权威） */
const ADMIN_PERMS = [
  'sys:user:view',
  'sys:role:view',
  'certificate:read',
  'subject:read',
  'question:question:read',
  'exam:exam:read',
  'course:course:read',
  'learn:profile:view',
]

export function AdminAccessDenied() {
  return (
    <div className="page">
      <div className="empty-state">
        <div className="empty-icon" aria-hidden="true">
          ⛔
        </div>
        <p className="empty-title">无权访问管理后台</p>
        <p className="empty-hint">当前账号没有管理权限，如有疑问请联系管理员。</p>
        <a href="/" className="btn btn-secondary btn-md">
          返回用户端
        </a>
      </div>
    </div>
  )
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { has, loading } = usePermissions()
  if (loading) {
    return (
      <div className="page">
        <Spinner label="正在校验权限…" />
      </div>
    )
  }
  const allowed = ADMIN_PERMS.some((p) => has(p))
  if (!allowed) {
    return <AdminAccessDenied />
  }
  return <>{children}</>
}
