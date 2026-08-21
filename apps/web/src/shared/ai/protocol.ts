/**
 * AI 学习助手 · 场景协议与上下文 DTO（Stage 2.1 P1）。
 * 绝对红线：当前无真实 LLM Provider（ai-service 仅骨架），本层绝不伪造 AI 回复。
 * AI_READY 为唯一开关：真实 Provider + Token/计费/路由就绪后置 true。
 * 场景上下文在此类型化定义，未来经 Java ai 域组装下发 ai-service，前后端共用此协议语义。
 */

export const AI_READY = false

export type AiScenario = 'home' | 'course' | 'question' | 'mistake' | 'exam'

export interface AiGoalContext {
  certificateId: number
  /** 证书级目标为 null（自动覆盖全部科目） */
  subjectId: number | null
  versionId: number
  certificateName: string
  subjectName: string | null
}

export interface AiContext {
  scenario: AiScenario
  userId?: number
  goal?: AiGoalContext
  course?: { id: number; title: string } | null
  chapter?: { id: number; title: string } | null
  lesson?: { id: number; title: string } | null
  /** 做题场景：题目 + 用户答案 + 正确答案 + 解析 + 知识点 */
  question?: {
    id: number
    stem: string
    userAnswer: string
    correctAnswer: string
    analysis: string
    nodeIds: number[]
  } | null
  /** 错题场景：连续出错的题目（未来可扩展为知识点聚合） */
  mistakeQuestionIds?: number[]
  exam?: { id: number; name: string } | null
}

export interface ScenarioAction {
  label: string
  description: string
}

/**
 * Stage 2.3B 规则推荐（无真实 LLM）：
 * 后端 GET /api/v1/learn/me/next-step 基于真实掌握度/错题/课程关联产出；
 * source 固定为 rule-based，UI 必须如实标注「非 AI 生成」，绝不冒充模型输出。
 */
export interface NextStepRules {
  weakKnowledge: { nodeId: number; name: string | null; masteryValue: number | null; reason: string }[]
  recommendedCourses: { courseId: number; title: string; description: string | null }[]
  recommendedPractice: { id: number; stem: string }[]
  nextStep: string
  source: string
}

/** 各场景的快捷操作（协议：真实 AI 就绪后按场景+动作下发 ai-service） */
export const SCENARIO_ACTIONS: Record<AiScenario, ScenarioAction[]> = {
  home: [
    { label: '今天学什么？', description: '基于学习目标与真实进度生成当日建议' },
  ],
  course: [
    { label: '解释这一节', description: '用更简单的方式讲清本节核心概念' },
    { label: '总结重点', description: '提炼本节必背考点' },
    { label: '举一个例子', description: '用临床情境帮助理解' },
    { label: '我还是没理解', description: '换个角度再讲一遍' },
    { label: '出一道题考考我', description: '即时检验本节掌握情况' },
  ],
  question: [
    { label: '让学习助手帮我理解', description: '解释「为什么错」，而非重复标准答案' },
  ],
  mistake: [
    { label: '让学习助手分析我的问题', description: '定位概念混淆/知识缺失/情境判断问题' },
  ],
  exam: [
    { label: '考前建议', description: '基于掌握度/错题/历史考试给出复习顺序' },
  ],
}
