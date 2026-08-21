import { request, requestForm } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'

export interface CourseItem {
  id: number
  certificateId: number
  /** 内容树归属（Stage 2.1/2.3A）：证书→版本→科目→章节→课程 */
  subjectId: number | null
  versionId: number | null
  chapterId: number | null
  chapterName: string | null
  teacherId: number
  title: string
  description: string
  type: number
  status: number
  chapterCount: number
  lessonCount: number
}

export interface AdminChapter {
  id: number
  title: string
  sort: number
  status: number
  lessons: AdminLesson[]
}

export interface AdminLesson {
  id: number
  title: string
  durationSeconds: number
  sort: number
  status: number
  positionSeconds: number
  finished: number
}

export const courseAdminApi = {
  page: (pageNum = 1, pageSize = 20, params?: { certificateId?: number; subjectId?: number; status?: number; keyword?: string }) =>
    request<PageResult<CourseItem>>('/api/v1/course/courses', { query: { pageNum, pageSize, ...params } }),
  detail: (id: number) => request<CourseItem>(`/api/v1/course/courses/${id}`),
  create: (body: { certificateId: number; subjectId?: number | null; versionId?: number | null; chapterId?: number | null; teacherId: number; title: string; description: string }) =>
    request<number>('/api/v1/course/courses', { method: 'POST', body }),
  update: (id: number, body: { title: string; description: string; subjectId?: number | null; versionId?: number | null; chapterId?: number | null }) =>
    request<void>(`/api/v1/course/courses/${id}`, { method: 'PUT', body }),
  delete: (id: number) => request<void>(`/api/v1/course/courses/${id}`, { method: 'DELETE' }),
  publish: (id: number) => request<void>(`/api/v1/course/courses/${id}/publish`, { method: 'POST' }),
  unpublish: (id: number) => request<void>(`/api/v1/course/courses/${id}/unpublish`, { method: 'POST' }),
  chapters: (courseId: number) => request<AdminChapter[]>(`/api/v1/course/courses/${courseId}/chapters`),
  createChapter: (body: { courseId: number; title: string; sort?: number }) =>
    request<number>('/api/v1/course/chapters', { method: 'POST', body }),
  updateChapter: (id: number, body: { title: string; sort?: number }) =>
    request<void>(`/api/v1/course/chapters/${id}`, { method: 'PUT', body }),
  updateChapterStatus: (id: number, status: number) =>
    request<void>(`/api/v1/course/chapters/${id}/status`, { method: 'PUT', body: { status } }),
  deleteChapter: (id: number) => request<void>(`/api/v1/course/chapters/${id}`, { method: 'DELETE' }),
  createLesson: (body: { chapterId: number; title: string; durationSeconds: number; sort?: number }) =>
    request<number>('/api/v1/course/lessons', { method: 'POST', body }),
  updateLesson: (id: number, body: { title: string; durationSeconds?: number; sort?: number }) =>
    request<void>(`/api/v1/course/lessons/${id}`, { method: 'PUT', body }),
  updateLessonStatus: (id: number, status: number) =>
    request<void>(`/api/v1/course/lessons/${id}/status`, { method: 'PUT', body: { status } }),
  deleteLesson: (id: number) => request<void>(`/api/v1/course/lessons/${id}`, { method: 'DELETE' }),
  updateLessonNodes: (id: number, nodeIds: number[]) =>
    request<void>(`/api/v1/course/lessons/${id}/knowledge-nodes`, { method: 'PUT', body: { nodeIds } }),
  /** 视频上传（multipart，MinIO；预签名播放 URL 不在此缓存） */
  uploadVideo: (lessonId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return requestForm<void>(`/api/v1/course/lessons/${lessonId}/video`, form)
  },
}
