import { request } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'

export interface CategoryNode {
  id: number
  parentId: number
  name: string
  code: string
  path: string
  level: number
  sort: number
  enabled: number
  description: string
  children: CategoryNode[]
}

export interface CertItem {
  id: number
  categoryId: number
  categoryName: string
  name: string
  code: string
  shortName: string
  description: string
  enabled: number
  sort: number
  createTime: string
}

export interface CopySummary {
  targetCertificateId: number
  subjectCount: number
  nodeCount: number
  courseCount: number
  questionCount: number
}

export const certificateAdminApi = {
  categoryTree: () => request<CategoryNode[]>('/api/v1/certificate/categories/tree'),
  createCategory: (body: {
    parentId: number
    name: string
    code: string
    sort?: number
    enabled?: number
    description?: string
  }) => request<number>('/api/v1/certificate/categories', { method: 'POST', body }),
  updateCategory: (id: number, body: { name: string; code: string; sort?: number; enabled?: number; description?: string }) =>
    request<void>(`/api/v1/certificate/categories/${id}`, { method: 'PUT', body }),
  deleteCategory: (id: number) => request<void>(`/api/v1/certificate/categories/${id}`, { method: 'DELETE' }),
  moveCategory: (id: number, targetParentId: number) =>
    request<void>(`/api/v1/certificate/categories/${id}/move`, { method: 'POST', body: { targetParentId } }),
  certs: (pageNum = 1, pageSize = 20, categoryId?: number, keyword?: string) =>
    request<PageResult<CertItem>>('/api/v1/certificate/certificates', {
      query: { pageNum, pageSize, categoryId, keyword },
    }),
  createCert: (body: {
    categoryId: number
    name: string
    code: string
    shortName?: string
    description?: string
    sort?: number
    enabled?: number
  }) => request<number>('/api/v1/certificate/certificates', { method: 'POST', body }),
  updateCert: (id: number, body: {
    name: string
    code: string
    shortName?: string
    description?: string
    sort?: number
    enabled?: number
  }) => request<void>(`/api/v1/certificate/certificates/${id}`, { method: 'PUT', body }),
  deleteCert: (id: number) => request<void>(`/api/v1/certificate/certificates/${id}`, { method: 'DELETE' }),
  /** Stage 2.3B 复制证书体系（科目/章节/知识点/课程结构/可选题库） */
  copyCert: (sourceId: number, body: { name: string; code: string; categoryId?: number; copyQuestions?: boolean; description?: string }) =>
    request<CopySummary>(`/api/v1/content/certificates/${sourceId}/copy`, { method: 'POST', body }),
}
