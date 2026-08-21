import { request } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'

export interface AvailableExam {
  id: number
  name: string
  durationMinutes: number
  questionCount: number
  totalScore: number
  passScore: number
  validFrom: string | null
  validUntil: string | null
  inProgressAttemptId: number | null
}

export interface AttemptOption {
  optionKey: string
  content: string
}

export interface AttemptQuestion {
  paperQuestionId: number
  sort: number
  questionType: number
  stem: string
  options: AttemptOption[]
  savedAnswer: string | null
}

export interface AttemptStart {
  attemptId: number
  examId: number
  status: number
  examName: string
  durationMinutes: number
  questionCount: number
  startedAt: string
  expiredAt: string
  questions: AttemptQuestion[]
}

export interface SubmitResult {
  attemptId: number
  status: number
  score: number
  correctCount: number
  questionCount: number
  passScore: number
  passed: boolean
  submittedAt: string
}

export interface AttemptResult {
  attemptId: number
  examId: number
  examName: string
  score: number
  correctCount: number
  questionCount: number
  passScore: number
  passed: boolean
  durationMinutes: number
  startedAt: string
  submittedAt: string
  items: {
    paperQuestionId: number
    sort: number
    questionType: number
    stem: string
    standardAnswer: string
    submittedAnswer: string
    correct: boolean
    score: number
    analysis: string
    options: AttemptOption[]
  }[]
}

export const examApi = {
  available: (certificateId?: number) =>
    request<AvailableExam[]>('/api/v1/exam/available', { query: { certificateId } }),
  start: (examId: number) =>
    request<AttemptStart>(`/api/v1/exam/exams/${examId}/attempts`, { method: 'POST' }),
  getAttempt: (attemptId: number) => request<AttemptStart>(`/api/v1/exam/attempts/${attemptId}`),
  saveAnswers: (attemptId: number, answers: { paperQuestionId: number; answer: string }[]) =>
    request<void>(`/api/v1/exam/attempts/${attemptId}/answers`, {
      method: 'PUT',
      body: { answers },
    }),
  submit: (attemptId: number) =>
    request<SubmitResult>(`/api/v1/exam/attempts/${attemptId}/submit`, { method: 'POST' }),
  result: (attemptId: number) => request<AttemptResult>(`/api/v1/exam/attempts/${attemptId}/result`),
  myResults: (pageNum = 1, pageSize = 10) =>
    request<PageResult<SubmitResult>>('/api/v1/exam/my-results', { query: { pageNum, pageSize } }),
}
