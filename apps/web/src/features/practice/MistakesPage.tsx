import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { friendlyMessage } from '../../shared/api/types'
import { AiAssistant } from '../../shared/ai/AiAssistant'
import { practiceApi, type Mistake } from './api'

const TYPE_LABEL: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断' }

/** 错题本：本人未解决/已解决错题 + 标记已解决（服务端状态为准） */
export default function MistakesPage() {
  const [items, setItems] = useState<Mistake[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const page = await practiceApi.mistakes(1, 50)
      setItems(page.list)
    } catch (e) {
      setError(friendlyMessage(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const resolve = async (id: number) => {
    try {
      await practiceApi.resolveMistake(id)
      await load()
    } catch (e) {
      setError(friendlyMessage(e))
    }
  }

  return (
    <div className="ed-container ed-page">
      <Link to="/practice" className="ed-link-muted" style={{ fontSize: '0.9375rem' }}>
        ← 返回练习
      </Link>
      <header className="ed-page-head">
        <h1 className="ed-title">错题本</h1>
        <p className="ed-lead">答错的题目会自动收录，已解决后可标记移除</p>
      </header>

      {items.length > 0 && (
        <div style={{ marginBottom: 32 }}>
          <AiAssistant
            context={{
              scenario: 'mistake',
              mistakeQuestionIds: items.map((m) => m.questionId),
            }}
            triggerLabel="让学习助手分析我的问题"
          />
        </div>
      )}

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ marginBottom: 32 }}>
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => void load()}>
            重试
          </button>
        </div>
      )}

      {loading ? (
        <div className="ed-card ed-card-pad">
          <div className="ed-skeleton" style={{ height: 24, maxWidth: '60%', marginBottom: 20 }} aria-hidden="true" />
          <div className="ed-skeleton" style={{ height: 20, marginBottom: 12 }} aria-hidden="true" />
          <div className="ed-skeleton" style={{ height: 20, maxWidth: '80%' }} aria-hidden="true" />
        </div>
      ) : items.length === 0 ? (
        <div className="ed-empty">
          <div className="ed-empty-icon" aria-hidden="true">
            ✎
          </div>
          <p className="ed-empty-title">错题本是空的</p>
          <p className="ed-empty-hint">练习中答错的题目会自动出现在这里</p>
        </div>
      ) : (
        <div className="ed-card ed-card-pad">
          <ul className="ed-list">
            {items.map((m) => (
              <li key={m.id} className="ed-row" style={{ padding: '20px 0', flexWrap: 'wrap' }}>
                <div className="ed-row-main">
                  <div className="ed-row-title" style={{ marginBottom: 6 }}>
                    <span className={`ed-badge ${m.status === 1 ? 'ed-badge-fail' : ''}`} style={{ marginRight: 8 }}>
                      {TYPE_LABEL[m.questionType] ?? '题'}
                    </span>
                    {m.stem}
                  </div>
                  <div className="ed-row-sub">
                    累计答错 {m.mistakeCount} 次{m.status === 2 ? ' · 已解决' : ''}
                  </div>
                </div>
                {m.status === 1 && (
                  <button type="button" className="ed-btn ed-btn-sm ed-btn-ghost" onClick={() => void resolve(m.id)}>
                    标记已解决
                  </button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
