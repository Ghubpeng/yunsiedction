/** Token 集中管理（frontend-development §4.2：token 管理集中封装，页面禁止直接操作） */
const ACCESS_KEY = 'yunsie.accessToken'
const REFRESH_KEY = 'yunsie.refreshToken'

export const tokenStore = {
  getAccess(): string | null {
    return localStorage.getItem(ACCESS_KEY)
  },
  getRefresh(): string | null {
    return localStorage.getItem(REFRESH_KEY)
  },
  set(access: string, refresh: string): void {
    localStorage.setItem(ACCESS_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  },
  setAccess(access: string): void {
    localStorage.setItem(ACCESS_KEY, access)
  },
  clear(): void {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}
