import { ApiError, friendlyMessage, type Result } from './types'
import { tokenStore } from '../auth/tokenStore'

/**
 * 统一 API 客户端（frontend-development §4.2 强制）：
 * - 集中注入 Authorization
 * - 401 时单飞（single-flight）refresh 后重放原请求一次
 * - refresh 失败 → 清 token + 跳登录
 * - 业务错误（code!=0）统一转 ApiError（携带后端错误码与 traceId）
 * - API base URL 配置化：VITE_API_BASE_URL（默认空 = 同源代理 /api）
 * 页面禁止裸写 fetch（NEVER）。
 */

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

/** refresh 结果：ok=已轮换可重放；rejected=服务端明确拒绝（token 确已失效）；unavailable=网络瞬断（结果未知） */
type RefreshResult = 'ok' | 'rejected' | 'unavailable'

let refreshing: Promise<RefreshResult> | null = null
/** 页面会话内是否已完成一次成功刷新（token 已轮换；后续 401 不再发起第二次轮换，防旧 refresh token 互踢） */
let refreshedInPage = false
/** 网络瞬断重载限额（sessionStorage 跨重载存活，防重载循环） */
const RELOAD_ON_ABORT_KEY = 'yunsie.refresh.abortReloaded'

function toLogin(): void {
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

async function doRefresh(): Promise<RefreshResult> {
  if (refreshedInPage) {
    // 本页面会话已成功轮换过 token：store 中即为最新有效 token，直接复用。
    // 防止并发 401 各自携带旧 refresh token 触发第二次轮换（后端轮换后旧 token 失效 → 互踢误登出）
    return 'ok'
  }
  const refreshToken = tokenStore.getRefresh()
  if (!refreshToken) {
    tokenStore.clear()
    return 'rejected'
  }
  try {
    const resp = await fetch(`${BASE_URL}/api/v1/user/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!resp.ok) {
      // 服务端明确拒绝（refresh 已失效/重放）才清 token
      tokenStore.clear()
      return 'rejected'
    }
    const result = (await resp.json()) as Result<{ accessToken: string; refreshToken: string }>
    if (result.code !== 0 || !result.data?.accessToken) {
      tokenStore.clear()
      return 'rejected'
    }
    tokenStore.set(result.data.accessToken, result.data.refreshToken)
    refreshedInPage = true
    // 成功收敛后重置瞬断重载限额
    sessionStorage.removeItem(RELOAD_ON_ABORT_KEY)
    return 'ok'
  } catch {
    // 网络瞬断：服务端可能已处理轮换但响应丢失（token 生死未知）。
    // 保留 token 并整页重载一次重新收敛：后端轮换宽限期（60s）保证同一旧 token 重试仍成功；
    // 确已失效则由服务端明确拒绝后跳登录。不在此处清 token 或跳登录（避免误登出/互踢）。
    if (!sessionStorage.getItem(RELOAD_ON_ABORT_KEY)) {
      sessionStorage.setItem(RELOAD_ON_ABORT_KEY, '1')
      window.location.reload()
    }
    return 'unavailable'
  }
}

async function rawRequest<T>(path: string, init: RequestInit, retryOn401: boolean): Promise<Result<T>> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  const access = tokenStore.getAccess()
  if (access) {
    headers.set('Authorization', `Bearer ${access}`)
  }
  const resp = await fetch(`${BASE_URL}${path}`, { ...init, headers })
  if (resp.status === 401 && retryOn401) {
    // 单飞刷新：并发 401 只触发一次 refresh
    const outcome = await refreshOnce()
    if (outcome === 'ok') {
      return rawRequest<T>(path, init, false)
    }
    if (outcome === 'rejected') {
      toLogin()
      throw new ApiError(10001, friendlyMessage(null), '', 401)
    }
    // unavailable：网络瞬断（token 已保留，可能已触发页面重载重新收敛）——不跳登录，UI 可重试
    throw new ApiError(50001, '网络异常，请稍后重试', '', 0)
  }
  if (resp.status === 403) {
    const body = (await resp.json().catch(() => null)) as Result<T> | null
    throw new ApiError(body?.code ?? 20001, body?.message ?? '没有操作权限', body?.traceId ?? '', 403)
  }
  const body = (await resp.json().catch(() => null)) as Result<T> | null
  if (!body) {
    throw new ApiError(50001, '服务响应异常，请稍后重试', '', resp.status)
  }
  if (body.code !== 0) {
    throw new ApiError(body.code, body.message, body.traceId, resp.status)
  }
  return body
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  body?: unknown
  query?: Record<string, string | number | boolean | undefined | null>
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  let url = path
  if (options.query) {
    const params = new URLSearchParams()
    for (const [k, v] of Object.entries(options.query)) {
      if (v !== undefined && v !== null) {
        params.set(k, String(v))
      }
    }
    const qs = params.toString()
    if (qs) {
      url += `${path.includes('?') ? '&' : '?'}${qs}`
    }
  }
  const result = await rawRequest<T>(
    url,
    {
      method,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    },
    true,
  )
  return result.data
}

/**
 * multipart 上传（FormData）：统一走本客户端（token 注入/401 刷新重放/错误码转 ApiError）。
 * 页面禁止自行 fetch 上传。
 */
export async function requestForm<T>(path: string, form: FormData): Promise<T> {
  const headers = new Headers()
  const access = tokenStore.getAccess()
  if (access) {
    headers.set('Authorization', `Bearer ${access}`)
  }
  const resp = await fetch(`${BASE_URL}${path}`, { method: 'POST', headers, body: form })
  if (resp.status === 401) {
    const outcome = await refreshOnce()
    if (outcome === 'ok') {
      return requestForm<T>(path, form)
    }
    if (outcome === 'rejected') {
      toLogin()
      throw new ApiError(10001, '登录已失效，请重新登录', '', 401)
    }
    throw new ApiError(50001, '网络异常，请稍后重试', '', 0)
  }
  const body = (await resp.json().catch(() => null)) as Result<T> | null
  if (!body) {
    throw new ApiError(50001, '服务响应异常，请稍后重试', '', resp.status)
  }
  if (resp.status === 403 || body.code !== 0) {
    throw new ApiError(body.code || 20001, body.message || '没有操作权限', body.traceId ?? '', resp.status)
  }
  return body.data
}

/** 单飞 refresh（供 rawRequest 与 requestForm 共用） */
function refreshOnce(): Promise<RefreshResult> {
  refreshing = refreshing ?? doRefresh().finally(() => {
    refreshing = null
  })
  return refreshing
}
