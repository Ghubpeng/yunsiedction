import { request } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'

/* ================= 管理端 API 模块（全部走统一 client；页面禁止裸 fetch） ================= */

export interface PermissionNode {
  id: number
  parentId: number
  permissionCode: string
  permissionName: string
  permType: number
  path: string
  icon: string
  sort: number
  status: number
  remark: string
  children: PermissionNode[]
}

export interface RoleItem {
  id: number
  roleCode: string
  roleName: string
  roleType: number
  status: number
  remark: string
  sort: number
  createTime: string
}

export interface RoleDetail {
  role: RoleItem
  permissionIds: number[]
  dataScopes: { scopeType: number; resourceType: string; resourceId: number | null }[]
}

export interface AdminUser {
  id: number
  username: string
  mobile: string | null
  nickname: string
  avatar: string | null
  userType: number
  status: number
  lastLoginTime: string | null
  createTime: string
  roleIds: number[]
}

export interface CustomerServiceConfig {
  phone: string | null
  wechat: string | null
  qrCodeUrl: string | null
  serviceTime: string | null
  enabled: number
}

export const sysAdminApi = {
  myPermissionCodes: () => request<string[]>('/api/v1/sys/permissions/me/codes'),
  permissionTree: () => request<PermissionNode[]>('/api/v1/sys/permissions/tree'),
  /** 客服配置（Stage 2.2：配置化联系客服，权限点 sys:service:config） */
  customerService: () => request<CustomerServiceConfig>('/api/v1/sys/customer-service'),
  updateCustomerService: (body: {
    phone?: string | null
    wechat?: string | null
    qrCodeUrl?: string | null
    serviceTime?: string | null
    enabled: number
  }) => request<CustomerServiceConfig>('/api/v1/sys/customer-service', { method: 'PUT', body }),
  roles: (pageNum = 1, pageSize = 20, keyword?: string) =>
    request<PageResult<RoleItem>>('/api/v1/sys/roles', { query: { pageNum, pageSize, keyword } }),
  roleDetail: (id: number) => request<RoleDetail>(`/api/v1/sys/roles/${id}`),
  createRole: (body: {
    roleCode: string
    roleName: string
    roleType: number
    status: number
    remark: string
    sort: number
  }) => request<number>('/api/v1/sys/roles', { method: 'POST', body }),
  deleteRole: (id: number) => request<void>(`/api/v1/sys/roles/${id}`, { method: 'DELETE' }),
  assignPermissions: (id: number, permissionIds: number[]) =>
    request<void>(`/api/v1/sys/roles/${id}/permissions`, { method: 'POST', body: { permissionIds } }),
  users: (pageNum = 1, pageSize = 20, keyword?: string) =>
    request<PageResult<AdminUser>>('/api/v1/user/accounts', { query: { pageNum, pageSize, keyword } }),
  createUser: (body: {
    username: string
    mobile?: string | null
    nickname?: string
    password: string
    userType: number
    roleIds?: number[] | null
  }) => request<number>('/api/v1/user/accounts', { method: 'POST', body }),
  updateUserStatus: (id: number, status: number) =>
    request<void>(`/api/v1/user/accounts/${id}/status`, { method: 'PATCH', body: { status } }),
  resetPassword: (id: number, newPassword: string) =>
    request<void>(`/api/v1/user/accounts/${id}/password`, { method: 'POST', body: { newPassword } }),
  assignUserRoles: (id: number, roleIds: number[]) =>
    request<void>(`/api/v1/user/accounts/${id}/roles`, { method: 'POST', body: { roleIds } }),
  deleteUser: (id: number) => request<void>(`/api/v1/user/accounts/${id}`, { method: 'DELETE' }),
}
