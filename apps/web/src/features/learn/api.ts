import { request } from '../../shared/api/client'

export interface GoalView {
  id: number
  certificateId: number
  /** Stage 2.2：证书级目标为 null（自动覆盖全部科目）；科目级目标为科目 id（能力保留） */
  subjectId: number | null
  versionId: number
  status: number
  certificateName: string
  subjectName: string | null
}

/** 针对性练习推荐（服务端基于真实数据：错题/掌握度/最近练习；绝不 random） */
export interface RecommendationView {
  reason: string
  nodes: { id: number; name: string | null }[]
  questions: PracticeQuestionView[]
}

/** 练习安全视图（不含答案与解析，判分由服务端完成） */
export interface PracticeQuestionView {
  id: number
  certificateId: number
  questionType: number
  stem: string
  difficulty: number
  options: { optionKey: string; content: string; sort: number }[]
  nodeIds: number[]
}

export const goalApi = {
  current: () => request<GoalView | null>('/api/v1/learn/goals/current'),
  list: () => request<GoalView[]>('/api/v1/learn/goals'),
  /** Stage 2.2：用户只选证书（subjectId 不传=证书级目标）；科目级能力保留 */
  select: (certificateId: number, subjectId?: number) =>
    request<GoalView>('/api/v1/learn/goals/select', {
      method: 'POST',
      body: subjectId == null ? { certificateId } : { certificateId, subjectId },
    }),
}

export const recommendationApi = {
  get: (certificateId?: number, limit = 10) =>
    request<RecommendationView>('/api/v1/learn/me/practice-recommendation', {
      query: { certificateId, limit },
    }),
}

/** Stage 2.3B 学习下一步规则推荐（无真实 LLM；source=rule-based） */
export interface NextStepView {
  weakKnowledge: { nodeId: number; name: string | null; masteryValue: number | null; reason: string }[]
  recommendedCourses: { courseId: number; title: string; description: string | null }[]
  recommendedPractice: PracticeQuestionView[]
  nextStep: string
  source: string
}

export const nextStepApi = {
  get: (certificateId?: number) =>
    request<NextStepView>('/api/v1/learn/me/next-step', { query: { certificateId } }),
}

/** Stage 2.4 周学习报告（规则聚合真实数据；无历史掌握度快照，口径如实标注） */
export interface WeeklyReport {
  startDate: string
  endDate: string
  studySeconds: number
  finishedChapters: number
  practiceCount: number
  practiceCorrectCount: number
  masteredNodes: number
  practicedNodes: number
}

export const weeklyReportApi = {
  get: () => request<WeeklyReport>('/api/v1/learn/me/weekly-report'),
}

export interface LearnSummary {
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

export interface MasteryItem {
  nodeId: number
  nodeName: string | null
  nodeCode: string | null
  nodePath: string | null
  nodeType: number | null
  certificateId: number
  subjectId: number
  versionId: number
  masteryValue: number
  correctCount: number
  wrongCount: number
  lastPracticeAt: string | null
}

export interface WeaknessItem {
  nodeId: number
  nodeName: string | null
  nodePath: string | null
  nodeType: number | null
  mastery: number
  wrongCount: number
  practiceCount: number
  lastPracticeAt: string | null
}

export interface CalendarDay {
  studyDate: string
  studySeconds: number
  practiceCount: number
  examCount: number
}

export interface LearnCalendar {
  month: string
  currentStreak: number
  longestStreak: number
  days: CalendarDay[]
}

export interface Prediction {
  examId: number
  examName: string
  probability: number
  ruleVersion: string
  basis: string
  lowSample: boolean
  calculatedAt: string
}

export const learnApi = {
  summary: () => request<LearnSummary>('/api/v1/learn/me/summary'),
  /** certificateId 可省略：省略时返回全部证书当前版本的掌握度（Stage 1.8 后端追加支持） */
  mastery: (certificateId?: number) =>
    request<MasteryItem[]>('/api/v1/learn/me/mastery', { query: { certificateId } }),
  weakness: (limit?: number) =>
    request<WeaknessItem[]>('/api/v1/learn/me/weakness', { query: { limit } }),
  calendar: (month: string) => request<LearnCalendar>('/api/v1/learn/me/calendar', { query: { month } }),
  prediction: (examId: number) =>
    request<Prediction>('/api/v1/learn/me/prediction', { query: { examId } }),
}
