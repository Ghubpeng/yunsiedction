import { request } from '../../shared/api/client'

export interface SubjectItem {
  id: number
  certificateId: number
  name: string
  code: string
  sort: number
  enabled: number
  source: number
  description: string
  createTime: string
}

export interface VersionItem {
  id: number
  certificateId: number
  versionNo: string
  name: string
  status: number
  enabled: number
  remark: string
  createTime: string
}

export interface NodeItem {
  id: number
  parentId: number
  nodeType: number
  name: string
  description: string
  code: string
  path: string
  level: number
  sort: number
  enabled: number
  source: number
  children: NodeItem[]
}

export interface SubjectTree {
  subjectId: number
  subjectName: string
  children: NodeItem[]
}

export const subjectAdminApi = {
  subjects: (certificateId: number) =>
    request<SubjectItem[]>('/api/v1/subject/subjects', { query: { certificateId } }),
  createSubject: (body: {
    certificateId: number
    name: string
    code: string
    sort?: number
    enabled?: number
    source: number
    description?: string
  }) => request<number>('/api/v1/subject/subjects', { method: 'POST', body }),
  deleteSubject: (id: number) => request<void>(`/api/v1/subject/subjects/${id}`, { method: 'DELETE' }),
  updateSubject: (id: number, body: { name: string; sort?: number; enabled?: number; description?: string }) =>
    request<void>(`/api/v1/subject/subjects/${id}`, { method: 'PUT', body }),

  versions: (certificateId: number) =>
    request<VersionItem[]>('/api/v1/subject/versions', { query: { certificateId } }),
  createVersion: (body: { certificateId: number; versionNo: string; name: string; remark?: string }) =>
    request<number>('/api/v1/subject/versions', { method: 'POST', body }),
  setCurrentVersion: (id: number) => request<void>(`/api/v1/subject/versions/${id}/current`, { method: 'PUT' }),
  archiveVersion: (id: number) => request<void>(`/api/v1/subject/versions/${id}/archive`, { method: 'PUT' }),
  deleteVersion: (id: number) => request<void>(`/api/v1/subject/versions/${id}`, { method: 'DELETE' }),

  tree: (versionId: number) =>
    request<SubjectTree[]>('/api/v1/subject/nodes/tree', { query: { versionId } }),
  children: (id: number) => request<NodeItem[]>(`/api/v1/subject/nodes/${id}/children`),
  createNode: (body: {
    versionId: number
    subjectId: number
    parentId: number
    nodeType: number
    name: string
    description?: string
    code: string
    sort?: number
    enabled?: number
    source: number
    remark?: string
  }) => request<number>('/api/v1/subject/nodes', { method: 'POST', body }),
  updateNode: (id: number, body: { name: string; description?: string; sort?: number; enabled?: number; source?: number; remark?: string }) =>
    request<void>(`/api/v1/subject/nodes/${id}`, { method: 'PUT', body }),
  deleteNode: (id: number) => request<void>(`/api/v1/subject/nodes/${id}`, { method: 'DELETE' }),
}
