import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { courseApi } from '../course/api'
import { practiceApi, type PracticeResult } from '../practice/api'
import { learnApi, nextStepApi, type NextStepView, type PracticeQuestionView, type WeaknessItem } from '../learn/api'
import { AiAssistant } from '../../shared/ai/AiAssistant'
import { useGoal } from '../../shared/learn/GoalContext'

/**
 * 章节练习（P0 课程→学习→练习闭环）：
 * 题目范围=章节关联知识点（后端确定），判分走服务端（practice submit），
 * 完成后展示掌握反馈并引导回到课程主线。前端不复制判分与掌握度计算逻辑。
 */
export default function ChapterPracticePage() {
  const { courseId, chapterId } = useParams()
  const course = Number(courseId)
  const chapter = Number(chapterId)
  const { goal } = useGoal()

  const [questions, setQuestions] = useState<PracticeQuestionView[]>([])
  const [idx, setIdx] = useState(0)
  const [selected, setSelected] = useState<string[]>([])
  const [result, setResult] = useState<PracticeResult | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [doneCount, setDoneCount] = useState(0)

  useEffect(() => {
    let cancelled = false
    courseApi
      .chapterPractice(chapter)
      .then((list) => {
        if (!cancelled) setQuestions(list)
      })
      .catch(() => {
        if (!cancelled) setError('章节练习加载失败，请稍后重试')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [chapter])

  const question = questions[idx] ?? null
  const finished = questions.length > 0 && idx >= questions.length
  const chapterNodeIds = useMemo(() => new Set(questions.flatMap((q) => q.nodeIds)), [questions])

  const submit = useCallback(async () => {
    if (!question || selected.length === 0 || submitting) return
    setSubmitting(true)
    setError('')
    try {
      const r = await practiceApi.submit({
        questionId: question.id,
        answer: selected.join('|'),
        mode: question.nodeIds.length > 0 ? 3 : 1,
        nodeId: question.nodeIds[0],
        responseTimeMs: 3000,
      })
      setResult(r)
      if (r.correct) setDoneCount((n) => n + 1)
    } catch {
      setError('提交失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }, [question, selected, submitting])

  const next = () => {
    setIdx((i) => i + 1)
    setSelected([])
    setResult(null)
  }

  if (loading) {
    return (
      <div className="ed-immersive" style={{ textAlign: 'center', paddingTop: 120 }}>
        <div className="ed-skeleton" style={{ width: 140, height: 20, margin: '0 auto' }} aria-hidden="true" />
      </div>
    )
  }

  if (error && questions.length === 0) {
    return (
      <div className="ed-immersive">
        <Link to={`/courses/${course}`} className="ed-link-muted">
          ← 返回课程
        </Link>
        <div className="ed-error inline-error" role="alert" style={{ marginTop: 24 }}>
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => window.location.reload()}>
            重试
          </button>
        </div>
      </div>
    )
  }

  if (questions.length === 0) {
    return (
      <div className="ed-immersive">
        <div className="ed-empty">
          <div className="ed-empty-icon" aria-hidden="true">
            ✎
          </div>
          <p className="ed-empty-title">本章暂无可用练习题</p>
          <p className="ed-empty-hint">章节关联知识点后，题目会自动出现在这里</p>
          <Link to={`/courses/${course}`} className="ed-btn">
            返回课程
          </Link>
        </div>
      </div>
    )
  }

  if (finished) {
    return (
      <ChapterSummary
        courseId={course}
        chapterId={chapter}
        doneCount={doneCount}
        total={questions.length}
        chapterNodeIds={chapterNodeIds}
      />
    )
  }

  return (
    <div className="ed-immersive">
      <Link to={`/courses/${course}`} className="ed-link-muted" style={{ marginBottom: 20, display: 'inline-block' }}>
        ← 返回课程
      </Link>
      <header style={{ marginBottom: 24 }}>
        <span className="ed-eyebrow" style={{ marginBottom: 8 }}>
          章节练习
        </span>
        <h1 className="ed-title" style={{ fontSize: 'clamp(1.375rem, 3vw, 2rem)' }}>
          {question?.stem}
        </h1>
      </header>

      <p className="ed-caption q-progress num" style={{ marginBottom: 24 }}>
        第 {idx + 1} / {questions.length} 题
      </p>

      <div className="q-stem" style={{ display: 'none' }} aria-hidden="true" />

      <div className="option-list" role="group" aria-label="选项" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {question?.options.map((o) => {
          const chosen = selected.includes(o.optionKey)
          const isRight = result != null && o.optionKey === result.standardAnswer
          const isWrongPick = result != null && chosen && o.optionKey !== result.standardAnswer
          return (
            <button
              key={o.optionKey}
              type="button"
              className={`option-item ed-card ${chosen ? 'option-chosen' : ''} ${isRight ? 'correct' : ''} ${
                isWrongPick ? 'wrong' : ''
              }`}
              style={{
                textAlign: 'left',
                padding: '16px 20px',
                borderRadius: 'var(--ed-radius-md)',
                border: isRight
                  ? '1px solid #1d9a6c'
                  : isWrongPick
                    ? '1px solid #d70015'
                    : chosen
                      ? '1px solid var(--ed-ink)'
                      : undefined,
                background: isRight ? '#e6f5ef' : isWrongPick ? '#fdecee' : undefined,
                cursor: result ? 'default' : 'pointer',
              }}
              disabled={result != null}
              onClick={() => {
                if (result) return
                setSelected((s) => (s.includes(o.optionKey) ? s.filter((k) => k !== o.optionKey) : [...s, o.optionKey]))
              }}
            >
              <span style={{ fontWeight: 600, marginRight: 10 }}>{o.optionKey}</span>
              {o.content}
            </button>
          )
        })}
      </div>

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ marginTop: 20 }}>
          <span>{error}</span>
        </div>
      )}

      {result == null ? (
        <button
          type="button"
          className="ed-btn ed-btn-lg"
          style={{ width: '100%', marginTop: 28 }}
          disabled={selected.length === 0 || submitting}
          onClick={() => void submit()}
        >
          {submitting ? '判分中…' : '提交答案'}
        </button>
      ) : (
        <div className="feedback ed-card ed-card-pad" style={{ marginTop: 24, borderRadius: 'var(--ed-radius-lg)' }}>
          <p style={{ fontSize: '1.125rem', fontWeight: 600, margin: '0 0 8px' }}>
            {result.correct ? '✓ 回答正确' : '✗ 回答错误'}
            <span className="ed-caption" style={{ marginLeft: 12 }}>
              正确答案 {result.standardAnswer}
            </span>
          </p>
          <p className="ed-lead" style={{ fontSize: '1rem', margin: 0 }}>
            {result.analysis}
          </p>
          {!result.correct && (
            <div style={{ marginTop: 16 }}>
              <AiAssistant
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
                  question: {
                    id: question?.id ?? 0,
                    stem: question?.stem ?? '',
                    userAnswer: selected.join('|'),
                    correctAnswer: result.standardAnswer,
                    analysis: result.analysis,
                    nodeIds: question?.nodeIds ?? [],
                  },
                }}
                triggerLabel="让学习助手帮我理解"
              />
            </div>
          )}
          <button type="button" className="ed-btn ed-btn-lg" style={{ width: '100%', marginTop: 24 }} onClick={next}>
            {idx + 1 >= questions.length ? '查看本章反馈' : '下一题'}
          </button>
        </div>
      )}
    </div>
  )
}

/** 章节练习完成页（Stage 2.4 强化）：得分/掌握度/薄弱知识点/下一步建议 + 回到主线 */
function ChapterSummary({
  courseId,
  chapterId,
  doneCount,
  total,
  chapterNodeIds,
}: {
  courseId: number
  chapterId: number
  doneCount: number
  total: number
  chapterNodeIds: Set<number>
}) {
  const { goal } = useGoal()
  const [mastery, setMastery] = useState<{ nodeId: number; nodeName: string | null; masteryValue: number }[]>([])
  const [weak, setWeak] = useState<WeaknessItem[]>([])
  const [nextStep, setNextStep] = useState<NextStepView | null>(null)
  const [reported, setReported] = useState(false)

  // 完成上报：服务端记录章节练习完成（掌握=视频完成+本记录），判分结果此前均由服务端逐题返回
  useEffect(() => {
    if (reported) return
    courseApi
      .reportChapterPractice(chapterId, doneCount, total)
      .then(() => setReported(true))
      .catch(() => setReported(true)) // 上报失败不阻塞反馈页
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    let cancelled = false
    learnApi
      .mastery()
      .then((list) => {
        if (!cancelled) setMastery(list.filter((m) => chapterNodeIds.has(m.nodeId)))
      })
      .catch(() => {})
    learnApi
      .weakness(10)
      .then((list) => {
        if (!cancelled) setWeak(list.filter((w) => chapterNodeIds.has(w.nodeId)))
      })
      .catch(() => {})
    nextStepApi
      .get(goal?.certificateId)
      .then((v) => {
        if (!cancelled) setNextStep(v)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const pct = total === 0 ? 0 : Math.round((doneCount / total) * 100)

  return (
    <div className="ed-immersive" style={{ textAlign: 'center' }}>
      <div className="ed-empty-icon" aria-hidden="true" style={{ fontSize: '2.5rem' }}>
        {pct >= 80 ? '★' : '✓'}
      </div>
      <h1 className="ed-title" style={{ fontSize: 'clamp(1.5rem, 3vw, 2.25rem)' }}>
        本章练习 {doneCount}/{total} 正确 · 得分 {pct}
      </h1>
      <p className="ed-lead" style={{ margin: '12px auto 0' }}>
        {pct >= 80 ? '掌握得不错，可以进入下一章了。' : '建议回顾本章课时，再来检验一次。'}
      </p>

      <div className="ed-grid ed-grid-2" style={{ maxWidth: 720, margin: '32px auto 0', textAlign: 'left' }}>
        {(mastery.length > 0 || weak.length > 0) && (
          <div className="ed-card ed-card-pad">
            <h2 className="ed-h3" style={{ marginBottom: 12 }}>
              掌握度与薄弱点
            </h2>
            <ul className="ed-list">
              {mastery.map((m) => (
                <li key={m.nodeId} className="ed-row" style={{ padding: '10px 0' }}>
                  <div className="ed-row-main">
                    <div className="ed-row-title">{m.nodeName ?? `知识点 #${m.nodeId}`}</div>
                  </div>
                  <span className="ed-badge ed-badge-accent num">掌握 {m.masteryValue}</span>
                </li>
              ))}
              {weak.map((w) => (
                <li key={w.nodeId} className="ed-row" style={{ padding: '10px 0' }}>
                  <div className="ed-row-main">
                    <div className="ed-row-title">{w.nodeName ?? `知识点 #${w.nodeId}`}</div>
                  </div>
                  <span className="ed-badge ed-badge-fail num">薄弱 · 掌握 {w.mastery}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
        <div className="ed-card ed-card-pad">
          <h2 className="ed-h3" style={{ marginBottom: 12 }}>
            下一步建议
          </h2>
          <p className="ed-row-sub" style={{ margin: 0, lineHeight: 1.7 }}>
            {nextStep && nextStep.nextStep ? nextStep.nextStep : '返回课程主线，继续下一章学习。'}
          </p>
          <p className="ed-caption" style={{ marginTop: 10 }}>
            基于真实学习数据的规则推荐 · 非 AI 生成
          </p>
        </div>
      </div>

      <div style={{ marginTop: 32 }}>
        <Link to={`/courses/${courseId}`} className="ed-btn ed-btn-lg">
          返回课程，继续下一章 →
        </Link>
      </div>
    </div>
  )
}
