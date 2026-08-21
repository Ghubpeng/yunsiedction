import { request } from '../../shared/api/client'
import type { PageResult } from '../../shared/api/types'
import type { PracticeQuestionView } from '../learn/api'

export interface PublicCourse {
  id: number
  title: string
  description: string
  chapterCount: number
  lessonCount: number
}

export interface Lesson {
  id: number
  title: string
  durationSeconds: number
  sort: number
  status: number
  positionSeconds: number
  finished: number
}

export interface Chapter {
  id: number
  title: string
  sort: number
  status: number
  /** 章节完成（服务端判定：全部小节 finished）——Stage 2.1 学习主线 */
  finished: number
  /** Stage 2.4：章节练习是否完成（1-是） */
  practiceFinished: number
  /** Stage 2.4：最近一次章节练习得分（0~100；未练习为 null） */
  practiceScore: number | null
  lessons: Lesson[]
}

/** Stage 2.4 学习路径（证书→科目→章节→课时 + 四态） */
export interface LearningPath {
  certificateId: number | null
  certificateName: string | null
  totalChapters: number
  finishedChapters: number
  masteredChapters: number
  percent: number
  stage: string
  nextTask: {
    courseId: number
    courseTitle: string
    chapterId: number
    chapterTitle: string
    lessonId: number | null
    lessonTitle: string | null
    type: 'lesson' | 'practice'
  } | null
  subjects: {
    subjectId: number | null
    subjectName: string
    courses: {
      courseId: number
      title: string
      chapters: { chapterId: number; title: string; state: 'todo' | 'learning' | 'done' | 'mastered'; practiceScore: number | null }[]
    }[]
  }[]
}

export interface CourseTree {
  courseId: number
  title: string
  description: string
  /** 内容树归属（Stage 2.1/2.3A）：证书→版本→科目→章节→课程 */
  subjectId: number | null
  versionId: number | null
  chapterId: number | null
  /** 内容树归属名称（Stage 2.2/2.3A：用户端展示 证书→科目→章节） */
  certificateName: string | null
  subjectName: string | null
  chapterName: string | null
  chapters: Chapter[]
}

export interface PlayTicket {
  url: string
  expiresInSeconds: number
  expiresAt: string
}

export interface MyCourse {
  courseId: number
  title: string
  description: string
  lastLessonId: number
  lastLessonTitle: string
  lastPositionSeconds: number
  lastLearnTime: string
}

export const courseApi = {
  publicCourses: (pageNum = 1, pageSize = 20, certificateId?: number, subjectId?: number | null) =>
    request<PageResult<PublicCourse>>('/api/v1/course/public/courses', {
      query: { pageNum, pageSize, certificateId, subjectId },
    }),
  tree: (courseId: number) => request<CourseTree>(`/api/v1/course/public/courses/${courseId}/tree`),
  /** 公开大纲（匿名可读，无进度；登录后优先用 tree 带进度） */
  outline: (courseId: number) => request<CourseTree>(`/api/v1/course/public/courses/${courseId}/outline`),
  play: (lessonId: number) => request<PlayTicket>(`/api/v1/course/lessons/${lessonId}/play`),
  reportProgress: (lessonId: number, positionSeconds: number) =>
    request<void>('/api/v1/course/progress', {
      method: 'POST',
      body: { lessonId, positionSeconds },
    }),
  myCourses: () => request<MyCourse[]>('/api/v1/course/my-courses'),
  /** 章节练习题目（后端按章节关联知识点范围取题；不含答案，判分仍由服务端） */
  chapterPractice: (chapterId: number) =>
    request<PracticeQuestionView[]>(`/api/v1/question/practice/chapter/${chapterId}`),
  /** Stage 2.4：章节练习完成上报（服务端汇总并记录掌握） */
  reportChapterPractice: (chapterId: number, correctCount: number, totalCount: number) =>
    request<{ chapterId: number; correctCount: number; totalCount: number; score: number; finished: number }>(
      `/api/v1/course/chapters/${chapterId}/practice-result`,
      { method: 'POST', body: { correctCount, totalCount } },
    ),
  /** Stage 2.4：学习路径（证书→科目→章节→课时） */
  learningPath: (certificateId?: number) =>
    request<LearningPath>('/api/v1/course/me/learning-path', { query: { certificateId } }),
}
