import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../shared/auth/AuthContext'
import { useGoal } from '../../shared/learn/GoalContext'
import { courseApi, type LearningPath, type MyCourse } from '../course/api'
import { examApi, type AvailableExam } from '../exam/api'
import { nextStepApi } from '../learn/api'
import { AiAssistant } from '../../shared/ai/AiAssistant'
import { AI_READY, type NextStepRules } from '../../shared/ai/protocol'

function coverClass(id: number): string {
  return `ed-cover-${(id % 4) + 1}`
}

/** 最近一场已发布考试的开考时间（用于「距离考试」倒计时；无窗口则诚实展示官方口径） */
function countdownOf(exams: AvailableExam[]): { label: string; days: number | null } {
  const upcoming = exams
    .filter((e) => e.validFrom)
    .map((e) => ({ date: new Date(e.validFrom as string), name: e.name }))
    .filter((e) => e.date.getTime() > Date.now())
    .sort((a, b) => a.date.getTime() - b.date.getTime())[0]
  if (!upcoming) {
    return { label: '以官方公告为准', days: null }
  }
  const days = Math.max(1, Math.ceil((upcoming.date.getTime() - Date.now()) / 86_400_000))
  return { label: `距离「${upcoming.name}」`, days }
}

/**
 * 首页 = 学习入口（Stage 2.2）：
 * 当前考试 + 距离考试 → 今日学习（继续学习 / 章节练习 / 练习）→ 最近学习课程进度。
 * 减少后台指标展示（学习统计已归入「我的」）。
 */
export default function HomePage() {
  const { user } = useAuth()
  const { goal } = useGoal()
  const [myCourses, setMyCourses] = useState<MyCourse[]>([])
  const [exams, setExams] = useState<AvailableExam[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [nextStep, setNextStep] = useState<NextStepRules | null>(null)
  const [path, setPath] = useState<LearningPath | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.allSettled([courseApi.myCourses(), examApi.available(goal?.certificateId)]).then(([c, e]) => {
      if (cancelled) return
      setLoading(false)
      if (c.status === 'fulfilled') setMyCourses(c.value)
      else setError('部分数据加载失败，请刷新重试')
      if (e.status === 'fulfilled') setExams(e.value)
    })
    return () => {
      cancelled = true
    }
  }, [goal?.certificateId])

  // Stage 2.3B/2.4：规则推荐（无真实 LLM）+ 学习路径（学习阶段/今日任务）
  useEffect(() => {
    if (!goal) {
      setNextStep(null)
      setPath(null)
      return
    }
    let cancelled = false
    nextStepApi
      .get(goal.certificateId)
      .then((v) => {
        if (!cancelled) setNextStep(v)
      })
      .catch(() => {
        if (!cancelled) setNextStep(null)
      })
    courseApi
      .learningPath(goal.certificateId)
      .then((p) => {
        if (!cancelled) setPath(p)
      })
      .catch(() => {
        if (!cancelled) setPath(null)
      })
    return () => {
      cancelled = true
    }
  }, [goal?.certificateId])

  if (loading) {
    return (
      <div className="ed-container ed-page">
        <div className="ed-skeleton" style={{ height: 64, maxWidth: 560, marginBottom: 28 }} aria-hidden="true" />
        <div className="ed-skeleton" style={{ aspectRatio: '2.2/1', borderRadius: 'var(--ed-radius-xl)' }} aria-hidden="true" />
      </div>
    )
  }

  const latest = myCourses[0]
  const countdown = countdownOf(exams)

  return (
    <div className="ed-container ed-page">
      <header className="ed-page-head" style={{ marginBottom: 40 }}>
        <span className="ed-eyebrow">学习工作台</span>
        <h1 className="ed-display" style={{ fontSize: 'var(--ed-h1)' }}>
          你好，{user?.nickname || user?.username}
        </h1>
        {goal && (
          <p className="ed-lead" style={{ marginTop: 14 }}>
            当前考试：<strong>{goal.certificateName}</strong>
            {countdown.days != null && (
              <>
                {' '}
                · {countdown.label} · <span className="num" style={{ fontWeight: 600 }}>{countdown.days}</span> 天
              </>
            )}
            {countdown.days == null && <> · {countdown.label}</>}
          </p>
        )}
      </header>

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ marginBottom: 28 }}>
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => window.location.reload()}>
            重试
          </button>
        </div>
      )}

      <section aria-label="今日学习" style={{ marginBottom: 56 }}>
        <div className="ed-section-head" style={{ marginBottom: 24 }}>
          <div>
            <h2 className="ed-h2" style={{ fontSize: '1.5rem' }}>
              今日学习
            </h2>
          </div>
        </div>

        {latest ? (
          <Link to={`/courses/${latest.courseId}`} className="ed-card ed-card-hover ed-continue">
            <div className={`ed-continue-media ${coverClass(latest.courseId)}`}>
              <span className="ed-cover-monogram">{latest.title}</span>
            </div>
            <div className="ed-continue-body">
              <span className="ed-eyebrow" style={{ color: '#86868b', margin: 0 }}>
                继续学习
              </span>
              <h2 className="ed-h2">{latest.title}</h2>
              <p className="ed-lead" style={{ fontSize: '1rem' }}>
                上次学到「{latest.lastLessonTitle || '课时'}」 · {Math.round(latest.lastPositionSeconds / 60)} 分钟处
              </p>
              <span className="ed-continue-cta">继续学习 →</span>
            </div>
          </Link>
        ) : (
          <div className="ed-card ed-card-pad" style={{ textAlign: 'center' }}>
            <p className="ed-empty-title">还没有开始学习</p>
            <p className="ed-empty-hint">系统已按你的考试目标安排课程，从第一门开始。</p>
            <Link to="/courses" className="ed-btn">
              查看我的课程 →
            </Link>
          </div>
        )}

        <div className="ed-grid ed-grid-2" style={{ marginTop: 28 }}>
          <Link to="/learn-path" className="ed-card ed-card-pad ed-card-hover">
            <h3 className="ed-h3">学习路径</h3>
            <p className="ed-card-sub">
              {path
                ? `${path.stage}阶段 · 完成 ${path.finishedChapters}/${path.totalChapters} 章节${path.nextTask ? ` · 下一任务《${path.nextTask.courseTitle}》` : ''}`
                : '按 证书→科目→章节→课时 规划备考'}
            </p>
            <div className="ed-card-meta" style={{ color: 'var(--ed-accent)' }}>
              查看路径 →
            </div>
          </Link>
          <Link to="/practice" className="ed-card ed-card-pad ed-card-hover">
            <h3 className="ed-h3">推荐练习</h3>
            <p className="ed-card-sub">
              {nextStep && nextStep.recommendedPractice.length > 0
                ? `根据掌握度与错题推荐 ${nextStep.recommendedPractice.length} 道针对性练习`
                : '根据你的真实学习数据推荐下一批最值得做的题'}
            </p>
            <div className="ed-card-meta" style={{ color: 'var(--ed-accent)' }}>
              去练习 →
            </div>
          </Link>
          <Link to={latest ? `/courses/${latest.courseId}` : '/courses'} className="ed-card ed-card-pad ed-card-hover">
            <h3 className="ed-h3">章节练习</h3>
            <p className="ed-card-sub">学完一章后检验掌握情况，题目来自本章知识点</p>
            <div className="ed-card-meta" style={{ color: 'var(--ed-accent)' }}>
              去课程 →
            </div>
          </Link>
          {path?.nextTask && (
            <Link
              to={
                path.nextTask.type === 'practice'
                  ? `/courses/${path.nextTask.courseId}/chapters/${path.nextTask.chapterId}/practice`
                  : `/courses/${path.nextTask.courseId}/lessons/${path.nextTask.lessonId}`
              }
              className="ed-card ed-card-pad ed-card-hover"
            >
              <h3 className="ed-h3">今日任务</h3>
              <p className="ed-card-sub">
                {path.nextTask.type === 'practice'
                  ? `完成《${path.nextTask.courseTitle}》· ${path.nextTask.chapterTitle} 的章节练习`
                  : `继续《${path.nextTask.courseTitle}》· ${path.nextTask.chapterTitle} · ${path.nextTask.lessonTitle}`}
              </p>
              <div className="ed-card-meta" style={{ color: 'var(--ed-accent)' }}>
                立即开始 →
              </div>
            </Link>
          )}
        </div>
      </section>

      <section aria-label="今天学什么" className="ed-card ed-card-pad" style={{ marginBottom: 56 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <h2 className="ed-h2" style={{ fontSize: '1.5rem' }}>
            今天学什么？
          </h2>
          <span className="ed-badge ed-badge-accent">✦ 学习建议</span>
        </div>
        <p className="ed-lead" style={{ marginTop: 12, fontSize: '1rem' }}>
          {goal ? (
            <>
              你正在准备 <strong>{goal.certificateName}</strong>
              {latest ? `，建议今天继续「${latest.title}」的第 ${Math.round(latest.lastPositionSeconds / 60)} 分钟处。` : '，从第一门课程开始。'}
            </>
          ) : (
            '选择考试目标后，我们会为你安排每天的学习。'
          )}
        </p>
        <p className="ed-caption" style={{ marginTop: 8 }}>
          {AI_READY
            ? '以上建议由 AI 学习助手基于你的学习数据生成。'
            : '学习建议基于你的学习数据生成 · AI 助手将在 AI 服务启用后提供智能讲解。'}
        </p>
        <div style={{ marginTop: 16 }}>
          <AiAssistant
            nextStep={nextStep}
            context={{
              scenario: 'home',
              userId: user?.id,
              goal: goal
                ? {
                    certificateId: goal.certificateId,
                    subjectId: goal.subjectId,
                    versionId: goal.versionId,
                    certificateName: goal.certificateName,
                    subjectName: goal.subjectName,
                  }
                : undefined,
            }}
          />
        </div>
      </section>

      <section aria-label="最近学习">
        <div className="ed-section-head" style={{ marginBottom: 20 }}>
          <div>
            <h2 className="ed-h2" style={{ fontSize: '1.5rem' }}>
              最近学习
            </h2>
          </div>
          <Link to="/courses" className="ed-arrow-link">
            全部课程 →
          </Link>
        </div>
        {myCourses.length === 0 ? (
          <p className="ed-caption">开始学习后，课程进度会显示在这里。</p>
        ) : (
          <div className="ed-list">
            {myCourses.map((c) => (
              <Link key={c.courseId} to={`/courses/${c.courseId}`} className="ed-row ed-link-muted" style={{ padding: '16px 0' }}>
                <div className="ed-row-main">
                  <div className="ed-row-title">{c.title}</div>
                  <div className="ed-row-sub">
                    「{c.lastLessonTitle || '课时'}」 · {Math.round(c.lastPositionSeconds / 60)} 分钟处
                  </div>
                </div>
                <span className="ed-badge">继续 →</span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
