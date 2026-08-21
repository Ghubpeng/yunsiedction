import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { request } from '../api/client'
import { tokenStore } from './tokenStore'

export interface CurrentUser {
  id: number
  username: string
  nickname: string
  userType: number
}

interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: CurrentUser
}

interface AuthContextValue {
  user: CurrentUser | null
  /** 初始化中（恢复登录态/拉取当前用户） */
  booting: boolean
  login: (account: string, password: string) => Promise<void>
  /** Demo 一键登录（端点仅 yunsie.demo.enabled=true 时存在；生产构建无入口按钮）。role：learner/teacher/admin */
  demoLogin: (role?: 'learner' | 'teacher' | 'admin') => Promise<void>
  logout: () => Promise<void>
  /** 供路由守卫与 UI 使用 */
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [booting, setBooting] = useState(true)

  // 启动恢复：有 access token → 拉当前用户（401 由 client 自动 refresh；refresh 失败跳登录）
  useEffect(() => {
    let cancelled = false
    async function boot() {
      if (!tokenStore.getAccess()) {
        setBooting(false)
        return
      }
      try {
        const me = await request<CurrentUser>('/api/v1/user/auth/me')
        if (!cancelled) {
          setUser(me)
        }
      } catch {
        // refresh 失败已被 client 清 token 并跳登录；这里兜底清空状态
        if (!cancelled) {
          setUser(null)
        }
      } finally {
        if (!cancelled) {
          setBooting(false)
        }
      }
    }
    void boot()
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (account: string, password: string) => {
    const result = await request<LoginResult>('/api/v1/user/auth/login', {
      method: 'POST',
      body: { account, password },
    })
    tokenStore.set(result.accessToken, result.refreshToken)
    setUser(result.user)
  }, [])

  const demoLogin = useCallback(async (role?: 'learner' | 'teacher' | 'admin') => {
    // 真实鉴权链路：签发真实 JWT/refresh，仅免除输入账号密码（端点由后端开关门控）
    const result = await request<LoginResult>('/api/v1/user/auth/demo-login', {
      method: 'POST',
      body: role ? { role } : undefined,
    })
    tokenStore.set(result.accessToken, result.refreshToken)
    setUser(result.user)
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefresh()
    if (refreshToken) {
      // 登出失败不阻塞本地清理（token 轮换失效由服务端兜底）
      try {
        await request<void>('/api/v1/user/auth/logout', {
          method: 'POST',
          body: { refreshToken },
        })
      } catch {
        // ignore
      }
    }
    tokenStore.clear()
    setUser(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, booting, login, demoLogin, logout, isAuthenticated: user !== null }),
    [user, booting, login, demoLogin, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
