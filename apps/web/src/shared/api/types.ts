/** 后端统一响应结构（api-design：Result{code,message,data,traceId,timestamp}；code 0=成功） */
export interface Result<T> {
  code: number
  message: string
  data: T
  traceId: string
  timestamp: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

/** 前端统一 API 错误（含后端错误码与 traceId，可上报与排查） */
export class ApiError extends Error {
  readonly code: number
  readonly traceId: string
  readonly httpStatus: number

  constructor(code: number, message: string, traceId: string, httpStatus: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
    this.httpStatus = httpStatus
  }
}

/** 按错误码给出用户友好文案（兜底用；业务页面可自行覆盖） */
export function friendlyMessage(e: unknown): string {
  if (e instanceof ApiError) {
    switch (e.code) {
      case 10001:
      case 10002:
        return '登录已失效，请重新登录'
      case 20001:
      case 20002:
        return '没有操作权限'
      case 30001:
        return '余额不足'
      default:
        return e.message || '请求失败，请稍后重试'
    }
  }
  return '网络异常，请检查连接后重试'
}
