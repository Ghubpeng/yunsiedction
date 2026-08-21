import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, friendlyMessage } from '../../shared/api/types'
import { practiceApi, type CertificateItem, type PracticeQuestion, type PracticeResult } from './api'
import { nextStepApi, recommendationApi, type PracticeQuestionView } from '../learn/api'
import { useGoal } from '../../shared/learn/GoalContext'
import { AiAssistant } from '../../shared/ai/AiAssistant'
import type { NextStepRules } from '../../shared/ai/protocol'

const MODES = [
  { mode: 0, label: '针对性练习' },
  { mode: 1, label: '顺序练习' },
  { mode: 4, label: '错题练习' },
]

const TYPE_LABEL: Record<number, string> = { 1: '单选题', 2: '多选题', 3: '判断题' }

/**
 * 练习中心（Stage 2.1 P0 重定位）：
 * 默认「针对性练习」= 服务端基于真实数据推荐（错题/掌握度/最近练习，绝不 random）；
 * 顺序/错题保留为次级模式。判分权威在后端（前端仅展示服务端返回的正误/解析）。
 */
export default function PracticePage() {
  const { goal } = useGoal()
  const [mode, setMode] = useState(0)
  const [certificates, setCertificates] = useState<CertificateItem[]>([])
  const [certificateId, setCertificateId] = useState<number | undefined>(undefined)
  const [questions, setQuestions] = useState<PracticeQuestion[]>([])
  const [questionNodes, setQuestionNodes] = useState<number[][]>([])
  const [reason, setReason] = useState('')
  const [index, setIndex] = useState(0)
  const [selected, setSelected] = useState<string[]>([])
  const [result, setResult] = useState<PracticeResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const startedAtRef = useRef(0)
  const [nextStep, setNextStep] = useState<NextStepRules | null>(null)

  // Stage 2.4：练习页固定学习助手入口（rule-based，非 LLM）
  useEffect(() => {
    if (!goal) return
    let cancelled = false
    nextStepApi
      .get(goal.certificateId)
      .then((v) => {
        if (!cancelled) setNextStep(v)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [goal?.certificateId])

  const loadQuestions = useCallback(
    async (m: number, certId?: number) => {
      setLoading(true)
      setError('')
      setResult(null)
      setIndex(0)
      setSelected([])
      setReason('')
      try {
        if (m === 0) {
          // 针对性推荐：服务端基于真实学习数据（目标上下文 + 错题/掌握度/最近练习）
          const rec = await recommendationApi.get(certId, 10)
          setReason(rec.reason)
          setQuestions(rec.questions.map((q: PracticeQuestionView) => ({ id: q.id, questionType: q.questionType, stem: q.stem, options: q.options })))
          setQuestionNodes(rec.questions.map((q: PracticeQuestionView) => q.nodeIds))
        } else {
          const list = await practiceApi.next({ mode: m, certificateId: m === 1 ? certId : undefined, size: 10 })
          setQuestions(list)
          setQuestionNodes(list.map(() => []))
        }
      } catch (e) {
        setError(friendlyMessage(e))
      } finally {
        setLoading(false)
      }
    },
    [],
  )

  // 证书上下文（顺序/推荐模式用）
  useEffect(() => {
    let cancelled = false
    practiceApi
      .certificates()
      .then((page) => {
        if (cancelled) return
        setCertificates(page.list)
        if (page.list.length > 0) {
          // 有考试目标时默认使用目标证书（目标=主要上下文）
          setCertificateId(goal?.certificateId ?? page.list[0].id)
        }
      })
      .catch(() => {
        /* 证书列表失败仅影响顺序练习 */
      })
    return () => {
      cancelled = true
    }
  }, [goal?.certificateId])

  useEffect(() => {
    if ((mode === 1 || mode === 0) && certificateId === undefined) {
      setQuestions([])
      setLoading(false)
      return
    }
    void loadQuestions(mode, mode === 4 ? undefined : certificateId)
  }, [mode, certificateId, loadQuestions])

  const question = questions[index]

  const toggleOption = (key: string) => {
    if (result) return
    if (!question) return
    if (question.questionType === 2) {
      setSelected((prev) => (prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key].sort()))
    } else {
      setSelected([key])
    }
  }

  const submit = async () => {
    if (!question || selected.length === 0) return
    setSubmitting(true)
    setError('')
    try {
      const nodes = questionNodes[index] ?? []
      const r = await practiceApi.submit({
        questionId: question.id,
        answer: selected.join('|'),
        mode: nodes.length > 0 ? 3 : mode,
        nodeId: nodes[0],
        responseTimeMs: Date.now() - startedAtRef.current,
      })
      setResult(r)
    } catch (e) {
      const err = e as ApiError
      setError(friendlyMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const next = () => {
    if (index + 1 < questions.length) {
      setIndex(index + 1)
      setSelected([])
      setResult(null)
      startedAtRef.current = Date.now()
    } else {
      void loadQuestions(mode, mode === 4 ? undefined : certificateId)
    }
  }

  useEffect(() => {
    startedAtRef.current = Date.now()
  }, [question])

  return (
    <div className="ed-immersive">
      <header className="ed-page-head" style={{ marginBottom: 32 }}>
        <span className="ed-eyebrow">练习中心</span>
        <h1 className="ed-title">针对性练习</h1>
        <p className="ed-lead">
          {mode === 0
            ? `系统根据你的真实学习数据推荐下一批最值得做的题${goal ? `（${goal.certificateName} · ${goal.subjectName}）` : ''}。`
            : '即时判分 · 错题自动进入错题本'}
        </p>
      </header>

      <div style={{ marginBottom: 28 }}>
        <AiAssistant
          nextStep={nextStep}
          context={{
            scenario: 'question',
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
      </div>

      <div role="tablist" aria-label="练习模式" style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center', marginBottom: 28 }}>
        {MODES.map((m) => (
          <button
            key={m.mode}
            type="button"
            role="tab"
            aria-selected={mode === m.mode}
            className={`ed-btn ed-btn-sm ${mode === m.mode ? '' : 'ed-btn-ghost'}`}
            onClick={() => setMode(m.mode)}
          >
            {m.label}
          </button>
        ))}
        <Link to="/mistakes" className="ed-btn ed-btn-sm ed-btn-ghost">
          错题本
        </Link>
        {(mode === 1 || mode === 0) && certificates.length > 1 && (
          <select
            aria-label="选择证书"
            value={certificateId}
            onChange={(e) => setCertificateId(Number(e.target.value))}
            style={{
              height: 36,
              padding: '0 16px',
              borderRadius: 'var(--ed-radius-pill)',
              border: '1px solid var(--ed-line)',
              background: 'var(--ed-bg)',
              color: 'var(--ed-ink)',
              fontSize: '0.9375rem',
              fontFamily: 'inherit',
              cursor: 'pointer',
            }}
          >
            {certificates.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        )}
      </div>

      {mode === 0 && reason && (
        <p className="ed-caption" style={{ marginBottom: 24, padding: '12px 16px', borderRadius: 12, background: 'var(--ed-bg-subtle)' }}>
          ✦ {reason}
        </p>
      )}

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ marginBottom: 24 }}>
          <span>{error}</span>
          <button
            type="button"
            className="ed-text-btn"
            onClick={() => void loadQuestions(mode, mode === 4 ? undefined : certificateId)}
          >
            重试
          </button>
        </div>
      )}

      {loading ? (
        <div className="ed-card ed-card-pad">
          <div className="ed-skeleton" style={{ height: 20, maxWidth: 120, marginBottom: 24 }} aria-hidden="true" />
          <div className="ed-skeleton" style={{ height: 32, maxWidth: '92%', marginBottom: 12 }} aria-hidden="true" />
          <div className="ed-skeleton" style={{ height: 32, maxWidth: '78%', marginBottom: 32 }} aria-hidden="true" />
          <div className="ed-skeleton" style={{ height: 60, borderRadius: 'var(--ed-radius-lg)' }} aria-hidden="true" />
        </div>
      ) : !question ? (
        <div className="ed-empty">
          <div className="ed-empty-icon" aria-hidden="true">
            ✎
          </div>
          <p className="ed-empty-title">
            {mode === 0 ? '还没有足够的练习数据' : mode === 1 && certificates.length === 0 ? '暂无可练习的证书题库' : mode === 4 ? '没有未解决的错题' : '暂时没有可练习的题目'}
          </p>
          <p className="ed-empty-hint">
            {mode === 0 ? '完成一些练习后，这里会推荐最适合你的下一批题' : '题目发布后会出现在这里'}
          </p>
          {mode === 0 && (
            <Link to="/courses" className="ed-btn ed-btn-sm" style={{ marginTop: 8 }}>
              先去学习课程
            </Link>
          )}
        </div>
      ) : (
        <div className="ed-card ed-card-pad">
          <div className="q-head" style={{ marginBottom: 20 }}>
            <span className="ed-badge">{TYPE_LABEL[question.questionType] ?? '题目'}</span>
            <span className="q-progress ed-caption">
              第 <span className="num">{index + 1}</span> / {questions.length} 题
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
            {question.stem}
          </h2>
          {question.questionType === 2 && (
            <p className="q-hint ed-caption" style={{ margin: '-12px 0 20px' }}>
              多选题：可选择多个选项
            </p>
          )}
          <div
            className="option-list"
            role="group"
            aria-label="选项"
            style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 28 }}
          >
            {question.options.map((opt) => {
              const chosen = selected.includes(opt.optionKey)
              let cls = 'option-item'
              if (result) {
                const standard = result.standardAnswer.split('|')
                if (standard.includes(opt.optionKey)) cls += ' option-correct'
                else if (chosen) cls += ' option-wrong'
              } else if (chosen) {
                cls += ' option-chosen'
              }
              return (
                <button
                  key={opt.optionKey}
                  type="button"
                  className={cls}
                  onClick={() => toggleOption(opt.optionKey)}
                  style={{ padding: '18px 20px', borderRadius: 'var(--ed-radius-lg)', fontSize: '1.0625rem', gap: 14 }}
                >
                  <span className="option-key" style={{ width: 30, height: 30, fontSize: '0.9375rem' }}>
                    {opt.optionKey}
                  </span>
                  <span style={{ flex: 1, lineHeight: 1.5 }}>{opt.content}</span>
                </button>
              )
            })}
          </div>

          {!result ? (
            <button
              type="button"
              className="ed-btn"
              onClick={submit}
              disabled={selected.length === 0 || submitting}
              style={{ width: '100%' }}
            >
              {submitting ? '提交中…' : '提交答案'}
            </button>
          ) : (
            <div
              className="feedback"
              role="status"
              style={{
                padding: '20px 24px',
                borderRadius: 'var(--ed-radius-lg)',
                background: result.correct ? '#e6f5ef' : '#fdecee',
                gap: 16,
              }}
            >
              <div style={{ color: result.correct ? '#1d9a6c' : '#d70015', fontWeight: 600 }}>
                {result.correct ? '✓ 回答正确' : '✗ 回答错误'} · 正确答案 {result.standardAnswer}
              </div>
              {result.analysis && (
                <p className="ed-caption" style={{ color: 'var(--ed-ink-2)', lineHeight: 1.6, margin: 0 }}>
                  {result.analysis}
                </p>
              )}
              <button type="button" className="ed-btn" onClick={next} style={{ width: '100%' }}>
                {index + 1 < questions.length ? '下一题' : '再来一组'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
