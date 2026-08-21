import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { friendlyMessage } from '../../shared/api/types'
import { examApi, type AttemptResult } from './api'

const TYPE_LABEL: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断' }

/** 成绩详情页：分数/通过 + 逐题对错与解析（数据全部来自服务端结果） */
export default function ExamResultPage() {
  const { attemptId } = useParams()
  const [result, setResult] = useState<AttemptResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    examApi
      .result(Number(attemptId))
      .then((r) => {
        if (!cancelled) setResult(r)
      })
      .catch((e) => {
        if (!cancelled) setError(friendlyMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [attemptId])

  if (loading) {
    return (
      <div className="ed-immersive">
        <div className="ed-skeleton" style={{ height: 48, maxWidth: 360, marginBottom: 24 }} aria-hidden="true" />
        <div className="ed-skeleton" style={{ height: 200, borderRadius: 'var(--ed-radius-xl)' }} aria-hidden="true" />
      </div>
    )
  }

  if (error || !result) {
    return (
      <div className="ed-immersive">
        <div className="ed-error inline-error" role="alert">
          <span>{error || '成绩不存在'}</span>
        </div>
      </div>
    )
  }

  return (
    <div className="ed-immersive">
      <Link to="/exams" className="ed-link-muted" style={{ marginBottom: 20, display: 'inline-block' }}>
        ← 返回考试中心
      </Link>

      <div className="ed-card ed-card-pad" style={{ textAlign: 'center', marginBottom: 32 }}>
        <h1 className="ed-title" style={{ marginBottom: 20 }}>
          {result.examName}
        </h1>
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            justifyContent: 'center',
            gap: 12,
            flexWrap: 'wrap',
            margin: '0 0 20px',
          }}
        >
          <span
            className="result-score-num num"
            style={{ fontSize: 'clamp(3rem, 8vw, 5rem)', fontWeight: 700, letterSpacing: '-0.03em', lineHeight: 1 }}
          >
            {result.score}
          </span>
          <span className="ed-caption" style={{ color: 'var(--ed-ink-2)' }}>
            / {result.passScore} 分 · 答对 {result.correctCount}/{result.questionCount} 题
          </span>
        </div>
        <span
          className={`ed-badge ${result.passed ? 'ed-badge-pass' : 'ed-badge-fail'}`}
          style={{ height: 32, padding: '0 16px', fontSize: '0.9375rem' }}
        >
          {result.passed ? '已通过' : '未通过'}
        </span>
        <p className="ed-caption" style={{ marginTop: 20 }}>
          交卷时间 {result.submittedAt} · 用时 {result.durationMinutes} 分钟
        </p>
      </div>

      {result.items.map((item) => (
        <div key={item.paperQuestionId} className="ed-card ed-card-pad" style={{ marginBottom: 20 }}>
          <div className="q-head" style={{ marginBottom: 20 }}>
            <span className={`ed-badge ${item.correct ? 'ed-badge-pass' : 'ed-badge-fail'}`}>
              {item.correct ? '✓ 正确' : '✗ 错误'} · {TYPE_LABEL[item.questionType] ?? '题'}
            </span>
            <span className="q-progress ed-caption">
              得分 <span className="num">{item.score}</span>
            </span>
          </div>
          <h2
            className="q-stem"
            style={{
              fontSize: 'clamp(1.25rem, 3vw, 1.75rem)',
              lineHeight: 1.45,
              fontWeight: 600,
              letterSpacing: '-0.01em',
              margin: '0 0 20px',
            }}
          >
            {item.stem}
          </h2>
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              color: 'var(--ed-ink-2)',
              fontSize: '1rem',
            }}
          >
            <div>你的答案：{item.submittedAnswer || '（未作答）'}</div>
            <div>正确答案：{item.standardAnswer}</div>
          </div>
          {item.analysis && (
            <p className="ed-caption" style={{ color: 'var(--ed-ink-2)', lineHeight: 1.6, marginTop: 16 }}>
              {item.analysis}
            </p>
          )}
        </div>
      ))}
    </div>
  )
}
