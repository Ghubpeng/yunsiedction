import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { friendlyMessage } from '../../shared/api/types'
import {
  goalApi,
  learnApi,
  nextStepApi,
  weeklyReportApi,
  type GoalView,
  type LearnCalendar,
  type LearnSummary,
  type MasteryItem,
  type Prediction,
  type WeaknessItem,
  type WeeklyReport,
} from './api'
import { examApi, type AvailableExam } from '../exam/api'
import { courseApi, type MyCourse } from '../course/api'
import { useAuth } from '../../shared/auth/AuthContext'
import { useGoal } from '../../shared/learn/GoalContext'
import { AiAssistant } from '../../shared/ai/AiAssistant'
import type { NextStepRules } from '../../shared/ai/protocol'

/**
 * 「我的」（Stage 2.1 P0 信息架构）：
 * 当前考试目标 → 我的课程（学习中/已完成）→ 我的资产（已购课程空态，不伪造 pay）
 * → 学习档案 → 消息。全部数据来自真实后端。
 */
export default function ProfilePage() {
  const { user } = useAuth()
  const [summary, setSummary] = useState<LearnSummary | null>(null)
  const [mastery, setMastery] = useState<MasteryItem[]>([])
  const [weakness, setWeakness] = useState<WeaknessItem[]>([])
  const [calendar, setCalendar] = useState<LearnCalendar | null>(null)
  const [prediction, setPrediction] = useState<Prediction | null>(null)
  const [exams, setExams] = useState<AvailableExam[]>([])
  const [goals, setGoals] = useState<GoalView[]>([])
  const [myCourses, setMyCourses] = useState<MyCourse[]>([])
  const [finishedCourseIds, setFinishedCourseIds] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [weekly, setWeekly] = useState<WeeklyReport | null>(null)
  const [nextStep, setNextStep] = useState<NextStepRules | null>(null)

  const month = new Date().toISOString().slice(0, 7)
  const { goal } = useGoal()

  // Stage 2.4：周学习报告 + 固定学习助手入口（rule-based，非 LLM）
  useEffect(() => {
    let cancelled = false
    weeklyReportApi
      .get()
      .then((r) => {
        if (!cancelled) setWeekly(r)
      })
      .catch(() => {})
    if (goal) {
      nextStepApi
        .get(goal.certificateId)
        .then((v) => {
          if (!cancelled) setNextStep(v)
        })
        .catch(() => {})
    }
    return () => {
      cancelled = true
    }
  }, [goal?.certificateId])

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [s, m, w, c, avail, g, mine] = await Promise.all([
        learnApi.summary(),
        learnApi.mastery(),
        learnApi.weakness(10),
        learnApi.calendar(month),
        examApi.available(),
        goalApi.list(),
        courseApi.myCourses(),
      ])
      setSummary(s)
      setMastery(m)
      setWeakness(w)
      setCalendar(c)
      setExams(avail)
      setGoals(g)
      setMyCourses(mine)
      if (avail.length > 0) {
        try {
          setPrediction(await learnApi.prediction(avail[0].id))
        } catch {
          setPrediction(null)
        }
      } else {
        setPrediction(null)
      }
      // 已完成课程：全部章节 finished（服务端 chapter.finished 判定，前端不复制规则）
      const finished = new Set<number>()
      const trees = await Promise.allSettled(mine.map((c) => courseApi.tree(c.courseId)))
      trees.forEach((t, i) => {
        if (t.status === 'fulfilled' && t.value.chapters.length > 0 && t.value.chapters.every((ch) => ch.finished === 1)) {
          finished.add(mine[i].courseId)
        }
      })
      setFinishedCourseIds(finished)
    } catch (e) {
      setError(friendlyMessage(e))
    } finally {
      setLoading(false)
    }
  }, [month])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return (
      <div className="ed-container ed-page">
        <div className="ed-skeleton" style={{ height: 56, maxWidth: 320, marginBottom: 32 }} aria-hidden="true" />
        <div className="ed-grid ed-grid-2">
          {[0, 1].map((i) => (
            <div key={i} className="ed-skeleton" style={{ height: 220, borderRadius: 'var(--ed-radius-xl)' }} aria-hidden="true" />
          ))}
        </div>
      </div>
    )
  }

  const current = goals.find((g) => g.status === 1) ?? null
  const learning = myCourses.filter((c) => !finishedCourseIds.has(c.courseId))
  const finishedCourses = myCourses.filter((c) => finishedCourseIds.has(c.courseId))
  // 考试倒计时：最近一场已发布且带开考时间的考试；无窗口诚实展示官方口径
  const countdown = (() => {
    const upcoming = exams
      .filter((e) => e.validFrom && new Date(e.validFrom).getTime() > Date.now())
      .sort((a, b) => new Date(a.validFrom as string).getTime() - new Date(b.validFrom as string).getTime())[0]
    if (!upcoming) {
      return { name: null, days: null }
    }
    const days = Math.max(1, Math.ceil((new Date(upcoming.validFrom as string).getTime() - Date.now()) / 86_400_000))
    return { name: upcoming.name, days }
  })()

  return (
    <div className="ed-container ed-page">
      <header className="ed-page-head">
        <h1 className="ed-title">我的</h1>
        <p className="ed-lead">{user?.nickname || user?.username} 的学习中心</p>
      </header>

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ marginBottom: 32 }}>
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => void load()}>
            重试
          </button>
        </div>
      )}

      {/* 我的考试：当前目标 + 考试倒计时 + 学习进度 */}
      <section className="ed-card ed-card-pad" style={{ marginBottom: 32 }} aria-label="我的考试">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <div>
            <h2 className="ed-h3" style={{ marginBottom: 8 }}>
              我的考试
            </h2>
            {current ? (
              <p className="ed-lead" style={{ fontSize: '1.0625rem', margin: 0 }}>
                {current.certificateName}
                {current.subjectName ? ` · ${current.subjectName}` : ' · 全部科目'}
              </p>
            ) : (
              <p className="ed-caption">还没有选择考试目标</p>
            )}
            {goals.length > 1 && (
              <p className="ed-caption" style={{ marginTop: 8 }}>
                历史目标：{goals.filter((g) => g.status === 0).map((g) => g.certificateName).join('、')}
                （切换保留历史学习数据）
              </p>
            )}
            <p className="ed-caption" style={{ marginTop: 8 }}>
              {countdown.days != null ? (
                <>距离「{countdown.name}」<span className="num">{countdown.days}</span> 天</>
              ) : (
                '考试时间以官方公告为准'
              )}
              {summary ? ` · 已完成 ${summary.courseFinishedLessons} 课时 · 练习 ${summary.practiceCount} 题` : ''}
            </p>
          </div>
          <Link to="/goal-select" state={{ from: '/profile' }} className="ed-btn ed-btn-sm">
            {current ? '切换考试' : '选择考试'}
          </Link>
        </div>
      </section>

      {/* Stage 2.4：学习助手固定入口（rule-based 推荐，非 LLM 生成） */}
      <section style={{ marginBottom: 32 }} aria-label="学习助手">
        <AiAssistant
          nextStep={nextStep}
          context={{
            scenario: 'home',
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
          triggerLabel="学习助手"
        />
      </section>

      {/* 我的课程 */}
      <section style={{ marginBottom: 40 }} aria-label="我的课程">
        <div className="ed-section-head" style={{ marginBottom: 20 }}>
          <div>
            <h2 className="ed-h2" style={{ fontSize: '1.375rem' }}>
              我的课程
            </h2>
          </div>
          <Link to="/courses" className="ed-arrow-link">
            全部课程 →
          </Link>
        </div>
        {myCourses.length === 0 ? (
          <div className="ed-card ed-card-pad" style={{ textAlign: 'center' }}>
            <p className="ed-empty-title" style={{ fontSize: '1rem' }}>
              还没有开始学习
            </p>
            <p className="ed-empty-hint">挑选一门课程，开始你的备考。</p>
          </div>
        ) : (
          <>
            {learning.length > 0 && (
              <>
                <h3 className="ed-eyebrow" style={{ margin: '0 0 12px' }}>
                  学习中
                </h3>
                <div className="ed-grid ed-grid-2" style={{ marginBottom: 24 }}>
                  {learning.map((c) => (
                    <Link key={c.courseId} to={`/courses/${c.courseId}`} className="ed-card ed-card-pad ed-card-hover">
                      <div className="ed-card-title" style={{ fontSize: '1.125rem' }}>
                        {c.title}
                      </div>
                      <div className="ed-card-sub">
                        上次学到「{c.lastLessonTitle || '课时'}」 · {Math.round(c.lastPositionSeconds / 60)} 分钟处
                      </div>
                      <div className="ed-card-meta" style={{ color: 'var(--ed-accent)' }}>
                        继续学习 →
                      </div>
                    </Link>
                  ))}
                </div>
              </>
            )}
            <h3 className="ed-eyebrow" style={{ margin: '0 0 12px' }}>
              已完成
            </h3>
            {finishedCourses.length === 0 ? (
              <p className="ed-caption">完成课程全部章节后会出现在这里</p>
            ) : (
              <div className="ed-grid ed-grid-2">
                {finishedCourses.map((c) => (
                  <Link key={c.courseId} to={`/courses/${c.courseId}`} className="ed-card ed-card-pad ed-card-hover">
                    <div className="ed-card-title" style={{ fontSize: '1.125rem' }}>
                      {c.title} <span className="ed-badge ed-badge-pass">已完成</span>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </>
        )}
      </section>

      {/* 我的资产（pay 未实现：真实结构 + 空状态，不伪造订单/支付/购买记录） */}
      <section style={{ marginBottom: 40 }} aria-label="我的资产">
        <h2 className="ed-h2" style={{ fontSize: '1.375rem', marginBottom: 20 }}>
          我的资产
        </h2>
        <div className="ed-grid ed-grid-2">
          <div className="ed-card ed-card-pad">
            <div className="ed-card-title" style={{ fontSize: '1.0625rem' }}>
              课程权益 · 已购课程
            </div>
            <p className="ed-caption" style={{ marginTop: 6 }}>
              暂无已购课程
            </p>
            <p className="ed-caption" style={{ marginTop: 4 }}>
              开通课程后将在这里显示。
            </p>
          </div>
          <div className="ed-card ed-card-pad">
            <div className="ed-card-title" style={{ fontSize: '1.0625rem' }}>
              我的证书 · 已获得
            </div>
            <p className="ed-caption" style={{ marginTop: 6 }}>
              暂无已获得证书
            </p>
            <div className="ed-card-title" style={{ fontSize: '1.0625rem', marginTop: 20 }}>
              备考证书
            </div>
            <p className="ed-caption" style={{ marginTop: 6 }}>
              {current ? `备考中：${current.certificateName}` : '选择考试目标后展示备考证书'}
            </p>
          </div>
        </div>
      </section>

      {/* 服务支持 */}
      <section style={{ marginBottom: 40 }} aria-label="服务支持">
        <h2 className="ed-h2" style={{ fontSize: '1.375rem', marginBottom: 20 }}>
          服务支持
        </h2>
        <div className="ed-grid ed-grid-2">
          <Link to="/service" className="ed-card ed-card-pad ed-card-hover">
            <div className="ed-card-title" style={{ fontSize: '1.0625rem' }}>
              联系客服
            </div>
            <p className="ed-caption" style={{ marginTop: 6 }}>
              电话 / 微信 / 服务时间
            </p>
          </Link>
          <Link to="/service" className="ed-card ed-card-pad ed-card-hover">
            <div className="ed-card-title" style={{ fontSize: '1.0625rem' }}>
              帮助中心
            </div>
            <p className="ed-caption" style={{ marginTop: 6 }}>
              常见问题 FAQ
            </p>
          </Link>
        </div>
      </section>

      {/* 学习档案（既有能力，保留全部真实数据） */}
      <section aria-label="学习档案">
        <h2 className="ed-h2" style={{ fontSize: '1.375rem', marginBottom: 20 }}>
          学习档案
        </h2>

        {weekly && (
          <div className="ed-card ed-card-pad" style={{ marginBottom: 32 }} aria-label="周学习报告">
            <h3 className="ed-h3" style={{ marginBottom: 16 }}>
              本周学习报告 · {weekly.startDate} ~ {weekly.endDate}
            </h3>
            <div className="ed-stat-grid">
              <div className="ed-stat">
                <div className="ed-stat-label">学习时间</div>
                <div className="ed-stat-value num">{Math.round(weekly.studySeconds / 60)} 分钟</div>
              </div>
              <div className="ed-stat">
                <div className="ed-stat-label">完成章节</div>
                <div className="ed-stat-value num">{weekly.finishedChapters}</div>
              </div>
              <div className="ed-stat">
                <div className="ed-stat-label">练习</div>
                <div className="ed-stat-value num">{weekly.practiceCount} 题</div>
                <p className="ed-caption" style={{ marginTop: 6 }}>
                  正确 {weekly.practiceCorrectCount} 题
                </p>
              </div>
              <div className="ed-stat">
                <div className="ed-stat-label">掌握变化</div>
                <div className="ed-stat-value num">{weekly.masteredNodes}</div>
                <p className="ed-caption" style={{ marginTop: 6 }}>
                  本周练习 {weekly.practicedNodes} 个知识点，当前掌握≥60 计 {weekly.masteredNodes} 个（无历史快照，按当前掌握度统计）
                </p>
              </div>
            </div>
          </div>
        )}

        {summary && (
          <div className="ed-card ed-card-pad" style={{ marginBottom: 32 }}>
            <h3 className="ed-h3" style={{ marginBottom: 20 }}>
              学习概览
            </h3>
            <div className="ed-stat-grid">
              <div className="ed-stat">
                <div className="ed-stat-label">连续学习</div>
                <div className="ed-stat-value num">{summary.streakDays} 天</div>
              </div>
              <div className="ed-stat">
                <div className="ed-stat-label">累计学习</div>
                <div className="ed-stat-value num">{Math.round(summary.totalStudySeconds / 60)} 分钟</div>
              </div>
              <div className="ed-stat">
                <div className="ed-stat-label">练习</div>
                <div className="ed-stat-value num">{summary.practiceCount}</div>
                <p className="ed-caption" style={{ marginTop: 6 }}>
                  正确 {summary.practiceCorrectCount} 题
                </p>
              </div>
              <div className="ed-stat">
                <div className="ed-stat-label">考试</div>
                <div className="ed-stat-value num">{summary.examCount}</div>
                <p className="ed-caption" style={{ marginTop: 6 }}>
                  最佳 {summary.examBestScore ?? '-'} 分
                </p>
              </div>
              <div className="ed-stat">
                <div className="ed-stat-label">完成课时</div>
                <div className="ed-stat-value num">{summary.courseFinishedLessons}</div>
              </div>
            </div>
          </div>
        )}

        <div className="ed-grid ed-grid-2" style={{ marginBottom: 32 }}>
          <div className="ed-card ed-card-pad" aria-label="知识点掌握度">
            <h3 className="ed-h3" style={{ marginBottom: 16 }}>
              知识点掌握度
            </h3>
            {mastery.length === 0 ? (
              <p className="ed-caption">暂无掌握度数据</p>
            ) : (
              <ul className="ed-list">
                {mastery.map((m) => (
                  <li key={`${m.nodeId}-${m.versionId}`} className="ed-row" style={{ padding: '14px 0' }}>
                    <div className="ed-row-main">
                      <div className="ed-row-title">{m.nodeName ?? `知识点 #${m.nodeId}`}</div>
                      <div className="ed-row-sub">
                        对 {m.correctCount} · 错 {m.wrongCount}
                      </div>
                    </div>
                    <div style={{ width: 140 }}>
                      <div className="ed-progress">
                        <div className="ed-progress-fill" style={{ width: `${m.masteryValue}%` }} />
                      </div>
                      <div className="ed-caption num" style={{ textAlign: 'right', marginTop: 4 }}>
                        {m.masteryValue}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="ed-card ed-card-pad" aria-label="薄弱点">
            <h3 className="ed-h3" style={{ marginBottom: 16 }}>
              薄弱点
            </h3>
            {weakness.length === 0 ? (
              <p className="ed-caption">练习数据积累后自动生成</p>
            ) : (
              <ul className="ed-list">
                {weakness.map((w) => (
                  <li key={w.nodeId} className="ed-row" style={{ padding: '14px 0' }}>
                    <div className="ed-row-main">
                      <div className="ed-row-title">{w.nodeName ?? `知识点 #${w.nodeId}`}</div>
                      <div className="ed-row-sub">
                        错 {w.wrongCount} · 练 {w.practiceCount}
                      </div>
                    </div>
                    <span className={`ed-badge ${w.mastery < 30 ? 'ed-badge-fail' : 'ed-badge-accent'}`}>
                      掌握 {w.mastery}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="ed-card ed-card-pad" aria-label="学习日历" style={{ marginBottom: 32 }}>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap', marginBottom: 20 }}>
            <h3 className="ed-h3">学习日历 · {calendar?.month ?? month}</h3>
            <div style={{ display: 'flex', gap: 8 }}>
              {calendar && (
                <>
                  <span className="ed-badge ed-badge-pass">当前连续 {calendar.currentStreak} 天</span>
                  <span className="ed-badge">历史最长 {calendar.longestStreak} 天</span>
                </>
              )}
            </div>
          </div>
          {calendar && calendar.days.length > 0 ? (
            <ul className="ed-list">
              {calendar.days.map((d) => (
                <li key={d.studyDate} className="ed-row" style={{ padding: '12px 0' }}>
                  <span className="num" style={{ fontWeight: 600 }}>
                    {d.studyDate.slice(8, 10)} 日
                  </span>
                  <span className="ed-caption">{Math.round(d.studySeconds / 60)} 分钟</span>
                  <span className="ed-caption">练习 {d.practiceCount}</span>
                  <span className="ed-caption">考试 {d.examCount}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="ed-caption">本月暂无学习记录</p>
          )}
        </div>

        <div className="ed-card ed-card-pad" aria-label="考试通过率预测">
          <h3 className="ed-h3" style={{ marginBottom: 20 }}>
            考试通过率预测
          </h3>
          {prediction ? (
            <>
              <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                <span style={{ fontSize: '1.125rem', fontWeight: 500 }}>{prediction.examName}</span>
                <span className="num" style={{ fontSize: 'clamp(1.75rem, 4vw, 2.75rem)', fontWeight: 700, letterSpacing: '-0.02em' }}>
                  {prediction.probability}%
                </span>
              </div>
              <p className="ed-lead" style={{ marginTop: 12, fontSize: '1rem' }}>
                {prediction.basis}
              </p>
              <p className="ed-caption" style={{ marginTop: 16 }}>
                本预测为基于学习数据的参考性估算（规则版本 {prediction.ruleVersion}），不构成任何通过保证，请以实际考试为准。
              </p>
            </>
          ) : (
            <p className="ed-caption">存在可参加考试后自动生成参考性预测</p>
          )}
        </div>
        {exams.length === 0 && null}
      </section>

      <div style={{ textAlign: 'center', marginTop: 48 }}>
        <Link to="/messages" className="ed-arrow-link" style={{ fontSize: '1.0625rem' }}>
          站内消息 →
        </Link>
      </div>
    </div>
  )
}
