import { request } from '../../shared/api/client'

/** Stage 2.3B 内容运营看板（boot 聚合接口） */

export interface ContentOpsVO {
  assets: {
    certificates: number
    courses: number
    videos: number
    questions: number
  }
  reminders: {
    pendingReviewQuestions: number
    emptyChapters: { courseId: number; courseTitle: string | null; chapterId: number; chapterTitle: string }[]
    orphanChapters: { nodeId: number; nodeName: string; certificateId: number; certificateName: string }[]
  }
}

export const contentOpsApi = {
  dashboard: () => request<ContentOpsVO>('/api/v1/content/dashboard'),
}
