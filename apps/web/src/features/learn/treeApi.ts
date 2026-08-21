import { request } from '../../shared/api/client'

export interface CertOption {
  id: number
  categoryId: number
  name: string
  code: string
}

export interface SubjectOption {
  id: number
  certificateId: number
  name: string
  code: string
}

export const certificatePublicApi = {
  list: (pageSize = 50) =>
    request<{ list: CertOption[]; total: number }>('/api/v1/certificate/public/certificates', {
      query: { pageNum: 1, pageSize },
    }),
  categories: () =>
    request<CategoryOption[]>('/api/v1/certificate/public/categories/tree'),
}

export interface CategoryOption {
  id: number
  parentId: number
  name: string
  code: string
  enabled: number
  children: CategoryOption[]
}

export const subjectPublicApi = {
  listByCertificate: (certificateId: number) =>
    request<SubjectOption[]>('/api/v1/subject/public/subjects', { query: { certificateId } }),
}
