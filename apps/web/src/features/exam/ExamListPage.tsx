import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { friendlyMessage } from '../../shared/api/types'
import { useGoal } from '../../shared/learn/GoalContext'
import { AiAssistant } from '../../shared/ai/AiAssistant'
import { examApi, type AvailableExam, type SubmitResult } from './api'

/**
 * 考试中心：可参加考试（后端 available 返回；按当前考试目标过滤）+ 历史成绩。
 * 前端不自行实现考试状态机（NEVER）。
 */
export default function ExamListPage() {
  const navigate = useNavigate()
  const { goal } = useGoal()
  const [exams, setExams] = useState<AvailableExam[]>([])
  const [history, setHistory] = useState<SubmitResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [avail, results] = await Promise.all([
        examApi.available(goal?.certificateId),
        examApi.myResults(1, 10),
      ])
      setExams(avail)
      setHistory(results.list)
    } catch (e) {
      setError(friendlyMessage(e))
    } finally {
      setLoading(false)
    }
  }, [goal?.certificateId])

  useEffect(() => {
    void load()
  }, [load])

  const enter = async (exam: AvailableExam) => {
    try {
      const start = await examApi.start(exam.id)
      if (start.status === 2) {
        // 存在进行中 attempt：直接进入
        navigate(`/exams/${exam.id}/session`)
      }
    } catch (e) {
      setError(friendlyMessage(e))
    }
  }

  return (
    <div className="ed-container ed-page">
      <header className="ed-page-head">
        <h1 className="ed-title">考试</h1>
        <p className="ed-lead">模拟考试 · 服务端计时与判分</p>
      </header>

      <div style={{ marginBottom: 32 }}>
        <AiAssistant
          context={{
            scenario: 'exam',
            goal: goal
              ? {
                  certificateId: goal.certificateId,
                  subjectId: goal.subjectId,
                  versionId: goal.versionId,
                  certificateName: goal.certificateName,
                  subjectName: goal.subjectName,
                }
              : undefined,
            exam: exams[0] ? { id: exams[0].id, name: exams[0].name } : null,
          }}
          triggerLabel="考前建议"
        />
      </div>

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ marginBottom: 32 }}>
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => void load()}>
            重试
          </button>
        </div>
      )}

      {loading ? (
        <div className="ed-grid ed-grid-2">
          <div className="ed-skeleton" style={{ height: 180, borderRadius: 'var(--ed-radius-xl)' }} aria-hidden="true" />
          <div className="ed-skeleton" style={{ height: 180, borderRadius: 'var(--ed-radius-xl)' }} aria-hidden="true" />
        </div>
      ) : (
        <>
          <section style={{ marginBottom: 48 }}>
            <div className="ed-section-head" style={{ marginBottom: 24 }}>
              <div>
                <h2 className="ed-h3">可参加考试</h2>
              </div>
            </div>
            {exams.length === 0 ? (
              <div className="ed-empty">
                <div className="ed-empty-icon" aria-hidden="true">
                  ⌁
                </div>
                <p className="ed-empty-title">当前没有可参加的考试</p>
                <p className="ed-empty-hint">考试开放后会出现在这里</p>
              </div>
            ) : (
              <div className="ed-grid ed-grid-3">
                {exams.map((exam) => (
                  <div
                    key={exam.id}
                    className="ed-card ed-card-pad ed-card-hover exam-card"
                    style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
                  >
                    <h3 className="ed-h3" style={{ fontSize: '1.25rem' }}>
                      {exam.name}
                    </h3>
                    <p className="ed-caption">
                      {exam.questionCount} 题 · {exam.durationMinutes} 分钟 · 及格 {exam.passScore} 分
                    </p>
                    {exam.validFrom && <p className="ed-caption">开放时间 {exam.validFrom}</p>}
                    <div
                      style={{
                        marginTop: 'auto',
                        paddingTop: 12,
                        display: 'flex',
                        alignItems: 'center',
                        gap: 12,
                        flexWrap: 'wrap',
                      }}
                    >
                      {exam.inProgressAttemptId ? (
                        <>
                          <span className="ed-badge ed-badge-accent">进行中</span>
                          <button
                            type="button"
                            className="ed-btn ed-btn-sm"
                            onClick={() => navigate(`/exams/${exam.id}/session`)}
                          >
                            继续考试
                          </button>
                        </>
                      ) : (
                        <button type="button" className="ed-btn ed-btn-sm" onClick={() => void enter(exam)}>
                          开始考试
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section>
            <div className="ed-section-head" style={{ marginBottom: 24 }}>
              <div>
                <h2 className="ed-h3">历史成绩</h2>
              </div>
            </div>
            {history.length === 0 ? (
              <div className="ed-empty">
                <div className="ed-empty-icon" aria-hidden="true">
                  ⌁
                </div>
                <p className="ed-empty-title">暂无考试记录</p>
              </div>
            ) : (
              <div className="ed-card ed-card-pad">
                <ul className="ed-list">
                  {history.map((r) => (
                    <li key={r.attemptId} className="ed-row" style={{ padding: '20px 0', flexWrap: 'wrap' }}>
                      <div className="ed-row-main">
                        <div className="ed-row-title">考试 #{r.attemptId}</div>
                        <div className="ed-row-sub">交卷于 {r.submittedAt}</div>
                      </div>
                      <span className={`ed-badge ${r.passed ? 'ed-badge-pass' : 'ed-badge-fail'}`}>
                        <span className="num">{r.score}</span> 分 {r.passed ? '· 通过' : '· 未通过'}
                      </span>
                      <Link to={`/exams/results/${r.attemptId}`} className="ed-arrow-link">
                        详情 →
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
