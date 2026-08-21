import { request } from '../../shared/api/client'

export interface AuthUser {
  id: number
  username: string
  nickname: string
  userType: number
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: AuthUser
}

export const authApi = {
  login: (account: string, password: string) =>
    request<LoginResult>('/api/v1/user/auth/login', {
      method: 'POST',
      body: { account, password },
    }),
  me: () => request<AuthUser>('/api/v1/user/auth/me'),
}
