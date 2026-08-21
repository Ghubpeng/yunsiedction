import { request } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'

export interface PracticeOption {
  optionKey: string
  content: string
}

export interface PracticeQuestion {
  id: number
  questionType: number
  stem: string
  options: PracticeOption[]
}

export interface PracticeResult {
  questionId: number
  correct: boolean
  standardAnswer: string
  analysis: string
}

export interface Mistake {
  id: number
  questionId: number
  questionType: number
  stem: string
  mistakeCount: number
  status: number
}

export interface CertificateItem {
  id: number
  name: string
  code: string
}

export const practiceApi = {
  /** 启用证书列表（练习顺序/知识点模式的证书上下文） */
  certificates: () =>
    request<PageResult<CertificateItem>>('/api/v1/certificate/public/certificates', {
      query: { pageNum: 1, pageSize: 100 },
    }),
  next: (params: {
    mode: number
    certificateId?: number
    nodeId?: number
    cursor?: number
    size?: number
  }) =>
    request<PracticeQuestion[]>('/api/v1/question/practice/next', { query: params }),
  submit: (body: {
    questionId: number
    answer: string
    mode: number
    nodeId?: number
    responseTimeMs?: number
  }) => request<PracticeResult>('/api/v1/question/practice/submit', { method: 'POST', body }),
  mistakes: (pageNum = 1, pageSize = 20, status?: number) =>
    request<PageResult<Mistake>>('/api/v1/question/mistakes', {
      query: { pageNum, pageSize, status },
    }),
  resolveMistake: (id: number) =>
    request<void>(`/api/v1/question/mistakes/${id}/resolve`, { method: 'POST' }),
}
