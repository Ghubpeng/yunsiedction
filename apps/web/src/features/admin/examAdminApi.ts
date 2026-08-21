import { request } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'

export interface ExamItem {
  id: number
  certificateId: number
  name: string
  durationMinutes: number
  totalScore: number
  passScore: number
  validFrom: string | null
  validUntil: string | null
  status: number
  rule: { questionCount: number; questionTypes: number[]; nodeIds: number[] | null; difficulty: number | null }
}

export interface PaperSummary {
  paperId: number
  title: string
  durationMinutes: number
  totalScore: number
  questionCount: number
  status: number
  assembleSeed: number
  createTime: string
}

export interface PaperQuestion {
  paperQuestionId: number
  questionId: number
  sort: number
  score: number
  questionType: number
  stem: string
  analysis: string
  standardAnswer: string
  difficulty: number
  source: number
  contentVersion: number
  nodeIds: string
  options: { optionKey: string; content: string }[]
}

export interface ExamResultItem {
  attemptId: number
  status: number
  score: number
  correctCount: number
  questionCount: number
  passScore: number
  passed: boolean
  submittedAt: string
}

export const examAdminApi = {
  page: (pageNum = 1, pageSize = 20, params?: { certificateId?: number; status?: number; keyword?: string }) =>
    request<PageResult<ExamItem>>('/api/v1/exam/exams', { query: { pageNum, pageSize, ...params } }),
  detail: (id: number) => request<ExamItem>(`/api/v1/exam/exams/${id}`),
  create: (body: {
    certificateId: number
    /** 内容树归属（Stage 2.1）：证书→版本→科目→考试 */
    subjectId?: number | null
    versionId?: number | null
    name: string
    durationMinutes: number
    passScore?: number
    validFrom?: string | null
    validUntil?: string | null
    rule: { questionCount: number; questionTypes: number[]; nodeIds?: number[] | null; difficulty?: number | null }
  }) => request<number>('/api/v1/exam/exams', { method: 'POST', body }),
  update: (id: number, body: {
    name: string
    durationMinutes: number
    passScore?: number
    validFrom?: string | null
    validUntil?: string | null
    rule: { questionCount: number; questionTypes: number[] }
  }) => request<void>(`/api/v1/exam/exams/${id}`, { method: 'PUT', body }),
  delete: (id: number) => request<void>(`/api/v1/exam/exams/${id}`, { method: 'DELETE' }),
  assemble: (id: number) => request<number>(`/api/v1/exam/exams/${id}/assemble`, { method: 'POST' }),
  publish: (id: number) => request<void>(`/api/v1/exam/exams/${id}/publish`, { method: 'POST' }),
  unpublish: (id: number) => request<void>(`/api/v1/exam/exams/${id}/unpublish`, { method: 'POST' }),
  paperSummary: (id: number) => request<PaperSummary>(`/api/v1/exam/exams/${id}/paper`),
  paperQuestions: (id: number) => request<PaperQuestion[]>(`/api/v1/exam/exams/${id}/paper/questions`),
  results: (id: number, pageNum = 1, pageSize = 20) =>
    request<PageResult<ExamResultItem>>(`/api/v1/exam/exams/${id}/results`, { query: { pageNum, pageSize } }),
}
