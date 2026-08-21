import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, friendlyMessage } from '../../shared/api/types'
import { examApi, type AttemptStart } from './api'

const TYPE_LABEL: Record<number, string> = { 1: '单选题', 2: '多选题', 3: '判断题' }

/**
 * 考试作答页：
 * - 计时唯一依据 = 服务端 expiredAt（客户端仅展示倒计时，不参与判定）
 * - 答案选择后节流自动暂存（不重复创建数据：saveAnswers 为 upsert）
 * - 交卷结果以服务端返回为准；后端错误（重复交卷/已结束等）友好提示
 */
export default function ExamSessionPage() {
  const { examId } = useParams()
  const navigate = useNavigate()
  const [attempt, setAttempt] = useState<AttemptStart | null>(null)
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [remaining, setRemaining] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const pendingSaveRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const answersRef = useRef<Record<number, string>>({})

  const loadAttempt = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const a = await examApi.start(Number(examId))
      if (a.status !== 2) {
        // 已交卷等非进行中状态：直接看结果
        navigate(`/exams/results/${a.attemptId}`, { replace: true })
        return
      }
      setAttempt(a)
      const initial: Record<number, string> = {}
      for (const q of a.questions) {
        if (q.savedAnswer) initial[q.paperQuestionId] = q.savedAnswer
      }
      answersRef.current = initial
      setAnswers(initial)
    } catch (e) {
      const err = e as ApiError
      if (err.code === 30414 || err.code === 30415) {
        // 已交卷/已结束：友好提示
        setError(err.message)
      } else {
        setError(friendlyMessage(err))
      }
    } finally {
      setLoading(false)
    }
  }, [examId, navigate])

  useEffect(() => {
    void loadAttempt()
  }, [loadAttempt])

  // 倒计时：仅展示（计时权威在服务端 expired_at）
  useEffect(() => {
    if (!attempt) return
    const tick = () => {
      const left = new Date(attempt.expiredAt).getTime() - Date.now()
      setRemaining(Math.max(0, Math.floor(left / 1000)))
      if (left <= 0) {
        // 超时：服务端懒结算，交卷由服务端判定
        window.clearInterval(timer)
        void submitNow()
      }
    }
    tick()
    const timer = window.setInterval(tick, 1000)
    return () => window.clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt])

  const select = (pqId: number, questionType: number, key: string) => {
    if (submitted) return
    setAnswers((prev) => {
      let next: string
      if (questionType === 2) {
        const cur = (prev[pqId] ?? '').split('|').filter(Boolean)
        next = cur.includes(key) ? cur.filter((k) => k !== key).join('|') : [...cur, key].sort().join('|')
      } else {
        next = key
      }
      const updated = { ...prev, [pqId]: next }
      answersRef.current = updated
      scheduleSave()
      return updated
    })
  }

  const scheduleSave = useCallback(() => {
    if (pendingSaveRef.current) window.clearTimeout(pendingSaveRef.current)
    pendingSaveRef.current = setTimeout(() => {
      void saveNow()
    }, 1500)
  }, [])

  const saveNow = useCallback(async () => {
    if (!attempt) return
    const items = Object.entries(answersRef.current)
      .filter(([, v]) => v)
      .map(([pqId, answer]) => ({ paperQuestionId: Number(pqId), answer }))
    if (items.length === 0) return
    try {
      await examApi.saveAnswers(attempt.attemptId, items)
    } catch {
      // 暂存失败不打断作答；下次变更重试
    }
  }, [attempt])

  const submitNow = useCallback(async () => {
    if (!attempt || submitting || submitted) return
    setSubmitting(true)
    setError('')
    try {
      if (pendingSaveRef.current) window.clearTimeout(pendingSaveRef.current)
      await saveNow()
      const result = await examApi.submit(attempt.attemptId)
      setSubmitted(true)
      navigate(`/exams/results/${result.attemptId}`, { replace: true })
    } catch (e) {
      const err = e as ApiError
      setError(friendlyMessage(err))
      setSubmitting(false)
    }
  }, [attempt, submitting, submitted, saveNow, navigate])

  if (loading) {
    return (
      <div className="ed-immersive" style={{ textAlign: 'center', paddingTop: 120 }}>
        <div className="ed-skeleton" style={{ width: 180, height: 20, margin: '0 auto' }} aria-hidden="true" />
        <p className="ed-caption" style={{ marginTop: 16 }}>
          正在进入考试…
        </p>
      </div>
    )
  }

  if (error && !attempt) {
    return (
      <div className="ed-immersive">
        <div className="ed-error inline-error" role="alert">
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => void loadAttempt()}>
            重试
          </button>
        </div>
      </div>
    )
  }

  if (!attempt) {
    return null
  }

  const answeredCount = Object.values(answers).filter((v) => v).length
  const mm = remaining === null ? '--' : Math.floor(remaining / 60)
  const ss = remaining === null ? '--' : String(remaining % 60).padStart(2, '0')

  return (
    <div className="ed-immersive">
      <header className="exam-bar" style={{ marginBottom: 32 }}>
        <div>
          <span className="ed-eyebrow" style={{ marginBottom: 8 }}>
            考试中
          </span>
          <h1 className="ed-title" style={{ fontSize: 'clamp(1.375rem, 3vw, 2rem)' }}>
            {attempt.examName}
          </h1>
          <p className="ed-caption" style={{ marginTop: 8 }}>
            已答 <span className="num">{answeredCount}</span> / {attempt.questionCount} 题
          </p>
        </div>
        <div
          className={`exam-timer ${remaining !== null && remaining < 300 ? 'exam-timer-warn' : ''}`}
          style={{ padding: '10px 18px', borderRadius: 'var(--ed-radius-pill)', fontSize: '1.25rem', whiteSpace: 'nowrap' }}
        >
          <span className="num">
            {mm}:{ss}
          </span>
        </div>
      </header>

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ marginBottom: 24 }}>
          <span>{error}</span>
        </div>
      )}

      {attempt.questions.map((q, i) => {
        const chosen = (answers[q.paperQuestionId] ?? '').split('|').filter(Boolean)
        return (
          <div key={q.paperQuestionId} className="ed-card ed-card-pad" style={{ marginBottom: 24 }}>
            <div className="q-head" style={{ marginBottom: 20 }}>
              <span className="ed-badge">{TYPE_LABEL[q.questionType] ?? '题'}</span>
              <span className="q-progress ed-caption">
                第 <span className="num">{i + 1}</span> 题
              </span>
            </div>
            <h2
              className="q-stem"
              style={{
                fontSize: 'clamp(1.25rem, 3vw, 1.75rem)',
                lineHeight: 1.45,
                fontWeight: 600,
                letterSpacing: '-0.01em',
                margin: '0 0 24px',
              }}
            >
              {q.stem}
            </h2>
            <div
              className="option-list"
              role="group"
              aria-label={`第 ${i + 1} 题选项`}
              style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
            >
              {q.options.map((opt) => (
                <button
                  key={opt.optionKey}
                  type="button"
                  className={`option-item ${chosen.includes(opt.optionKey) ? 'option-chosen' : ''}`}
                  onClick={() => select(q.paperQuestionId, q.questionType, opt.optionKey)}
                  style={{ padding: '18px 20px', borderRadius: 'var(--ed-radius-lg)', fontSize: '1.0625rem', gap: 14 }}
                >
                  <span className="option-key" style={{ width: 30, height: 30, fontSize: '0.9375rem' }}>
                    {opt.optionKey}
                  </span>
                  <span style={{ flex: 1, lineHeight: 1.5 }}>{opt.content}</span>
                </button>
              ))}
            </div>
          </div>
        )
      })}

      <div className="exam-submit-bar" style={{ marginTop: 8 }}>
        <button
          type="button"
          className="ed-btn"
          onClick={() => void submitNow()}
          disabled={submitting}
          style={{ minWidth: 160 }}
        >
          {submitting ? '交卷中…' : '交卷'}
        </button>
      </div>
    </div>
  )
}
