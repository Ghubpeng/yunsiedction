import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { request } from '../api/client'
import { tokenStore } from './tokenStore'

/**
 * 当前用户权限点（管理端菜单/路由渲染用）。
 * 权限实时解析自 sys 域（/sys/permissions/me/codes）；服务端 @PreAuthorize 仍为最终权威。
 * 通配 *:*:* 视为拥有全部权限（PermissionChecker 同语义，仅 UI 展示用）。
 */
interface PermissionContextValue {
  codes: string[]
  loading: boolean
  has: (code: string) => boolean
  isSuper: boolean
}

const PermissionContext = createContext<PermissionContextValue | null>(null)

export function PermissionProvider({ children }: { children: ReactNode }) {
  const [codes, setCodes] = useState<string[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    // 未登录不请求（登录页无权限概念）；登录后 token 变化由登录/刷新触发重新请求
    if (!tokenStore.getAccess()) {
      setCodes([])
      setLoading(false)
      return
    }
    request<string[]>('/api/v1/sys/permissions/me/codes')
      .then((list) => {
        if (!cancelled) setCodes(list)
      })
      .catch(() => {
        if (!cancelled) setCodes([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const value = useMemo<PermissionContextValue>(() => {
    const isSuper = codes.includes('*:*:*')
    return {
      codes,
      loading,
      isSuper,
      has: (code: string) => isSuper || codes.includes(code),
    }
  }, [codes, loading])

  return <PermissionContext.Provider value={value}>{children}</PermissionContext.Provider>
}

export function usePermissions(): PermissionContextValue {
  const ctx = useContext(PermissionContext)
  if (!ctx) {
    throw new Error('usePermissions must be used within PermissionProvider')
  }
  return ctx
}
