import { request } from '../../shared/api/client'

export interface StudentListItem {
  userId: number
  nickname: string | null
  summary: {
    userId: number
    totalStudySeconds: number
    courseFinishedLessons: number
    practiceCount: number
    practiceCorrectCount: number
    examCount: number
    examBestScore: number | null
    streakDays: number
    lastStudyDate: string | null
    updatedAt: string
  }
}

export interface StudentProfile {
  userId: number
  nickname: string | null
  summary: StudentListItem['summary']
  mastery: {
    nodeId: number
    nodeName: string | null
    nodePath: string | null
    nodeType: number | null
    masteryValue: number
    correctCount: number
    wrongCount: number
    lastPracticeAt: string | null
  }[]
  weakness: {
    nodeId: number
    nodeName: string | null
    nodePath: string | null
    nodeType: number | null
    mastery: number
    wrongCount: number
    practiceCount: number
    lastPracticeAt: string | null
  }[]
}

/** 学员档案（学习数据经 learning-profile；教师 DataScope 由后端 TeacherAccessGuard 裁决） */
export const studentAdminApi = {
  listByCourse: (courseId: number) =>
    request<StudentListItem[]>('/api/v1/learn/students', { query: { courseId } }),
  detail: (userId: number) => request<StudentProfile>(`/api/v1/learn/students/${userId}`),
}
