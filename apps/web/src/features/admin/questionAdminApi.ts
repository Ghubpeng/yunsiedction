import { request, requestForm } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'

export interface QuestionOption {
  optionKey: string
  content: string
  sort?: number
}

export interface QuestionItem {
  id: number
  certificateId: number
  questionType: number
  stem: string
  difficulty: number
  source: number
  status: number
  contentVersion: number
  rejectReason: string | null
  auditBy: number | null
}

export interface QuestionDetail {
  question: QuestionItem
  analysis: string
  answer: string
  options: QuestionOption[]
  nodeIds: number[]
}

export interface ImportResult {
  success: boolean
  total: number
  successCount: number
  errors: { row: number; field: string; message: string }[]
}

export const questionAdminApi = {
  page: (params: {
    pageNum?: number
    pageSize?: number
    certificateId?: number
    status?: number
    questionType?: number
    difficulty?: number
    keyword?: string
  }) => request<PageResult<QuestionItem>>('/api/v1/question/questions', { query: params }),
  detail: (id: number) => request<QuestionDetail>(`/api/v1/question/questions/${id}`),
  create: (body: {
    certificateId: number
    questionType: number
    stem: string
    analysis: string
    answer: string
    difficulty: number
    source: number
    options: { optionKey: string; content: string }[]
    nodeIds: number[]
  }) => request<number>('/api/v1/question/questions', { method: 'POST', body }),
  update: (id: number, body: {
    stem: string
    analysis: string
    answer: string
    difficulty: number
    source: number
    options: { optionKey: string; content: string }[]
  }) => request<void>(`/api/v1/question/questions/${id}`, { method: 'PUT', body }),
  updateKnowledgeNodes: (id: number, nodeIds: number[]) =>
    request<void>(`/api/v1/question/questions/${id}/knowledge-nodes`, { method: 'PUT', body: { nodeIds } }),
  delete: (id: number) => request<void>(`/api/v1/question/questions/${id}`, { method: 'DELETE' }),
  submitReview: (id: number) => request<void>(`/api/v1/question/questions/${id}/submit-review`, { method: 'POST' }),
  withdrawReview: (id: number) => request<void>(`/api/v1/question/questions/${id}/withdraw-review`, { method: 'POST' }),
  approve: (id: number) => request<void>(`/api/v1/question/questions/${id}/approve`, { method: 'POST' }),
  reject: (id: number, reason: string) =>
    request<void>(`/api/v1/question/questions/${id}/reject`, { method: 'POST', body: { reason } }),
  unpublish: (id: number) => request<void>(`/api/v1/question/questions/${id}/unpublish`, { method: 'POST' }),
  restore: (id: number) => request<void>(`/api/v1/question/questions/${id}/restore`, { method: 'POST' }),
}

/** Excel 导入（multipart，统一 client；服务端最终校验） */
export function importQuestions(file: File, certificateId: number): Promise<ImportResult> {
  const form = new FormData()
  form.append('file', file)
  return requestForm<ImportResult>(`/api/v1/question/import?certificateId=${certificateId}`, form)
}
