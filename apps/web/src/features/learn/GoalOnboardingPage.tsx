import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useGoal } from '../../shared/learn/GoalContext'
import { certificatePublicApi, type CategoryOption, type CertOption } from './treeApi'
import { friendlyMessage } from '../../shared/api/types'

/**
 * 首次考试目标引导（Stage 2.2 重构，最高优先级）：
 * 用户只选证书——搜索框 + 分类浏览 + 全量列表，点击即「开始准备 XX 考试」。
 * 科目/版本由后端自动关联（用户入口隐藏复杂结构）。
 */
export default function GoalOnboardingPage() {
  const { select } = useGoal()
  const navigate = useNavigate()
  const location = useLocation()
  const backTo = (location.state as { from?: string } | null)?.from ?? '/'
  const [certs, setCerts] = useState<CertOption[]>([])
  const [categories, setCategories] = useState<CategoryOption[]>([])
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [picked, setPicked] = useState<CertOption | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    Promise.allSettled([certificatePublicApi.list(50), certificatePublicApi.categories()]).then(([c, g]) => {
      if (cancelled) return
      if (c.status === 'fulfilled') setCerts(c.value.list)
      if (g.status === 'fulfilled') setCategories(g.value)
      if (c.status === 'rejected') setError('证书列表加载失败，请刷新重试')
    })
    return () => {
      cancelled = true
    }
  }, [])

  // 分类 → 其自身与全部后代分类 id（证书分类过滤用）
  const categoryDescendants = useMemo(() => {
    const map = new Map<number, Set<number>>()
    const collect = (nodes: CategoryOption[], ancestors: number[]) => {
      for (const n of nodes) {
        const set = new Set<number>(ancestors)
        set.add(n.id)
        for (const a of ancestors) {
          map.get(a)?.add(n.id)
        }
        map.set(n.id, set)
        collect(n.children ?? [], [...ancestors, n.id])
      }
    }
    collect(categories, [])
    return map
  }, [categories])

  const filtered = useMemo(() => {
    let list = certs
    if (categoryId != null) {
      const allowed = categoryDescendants.get(categoryId)
      list = list.filter((c) => allowed != null && allowed.has(c.categoryId))
    }
    if (keyword.trim()) {
      const kw = keyword.trim().toLowerCase()
      list = list.filter((c) => c.name.toLowerCase().includes(kw) || c.code.toLowerCase().includes(kw))
    }
    return list
  }, [certs, categoryId, keyword, categoryDescendants])

  const onConfirm = async () => {
    if (!picked) return
    setBusy(true)
    setError('')
    try {
      await select(picked.id)
      navigate(backTo, { replace: true })
    } catch (e) {
      setError(friendlyMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="ed-container ed-page">
      <header className="ed-page-head" style={{ textAlign: 'center' }}>
        <span className="ed-eyebrow">第一步</span>
        <h1 className="ed-display" style={{ fontSize: 'var(--ed-h1)' }}>
          你准备参加什么考试？
        </h1>
        <p className="ed-lead" style={{ margin: '16px auto 0' }}>
          选择考试后，我们会自动为你安排课程、题库与模拟考试。
        </p>
      </header>

      {error && (
        <div className="ed-error inline-error" role="alert" style={{ maxWidth: 560, margin: '0 auto 28px' }}>
          <span>{error}</span>
        </div>
      )}

      <div style={{ maxWidth: 560, margin: '0 auto 40px' }}>
        <input
          className="ed-input"
          aria-label="搜索考试"
          placeholder="搜索考试，例如：护士、教师、会计"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>

      {categories.length > 0 && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'center', marginBottom: 40 }}>
          <button
            type="button"
            className={`ed-btn ed-btn-sm ${categoryId == null ? '' : 'ed-btn-ghost'}`}
            onClick={() => setCategoryId(null)}
          >
            全部
          </button>
          {categories.map((g) => (
            <button
              key={g.id}
              type="button"
              className={`ed-btn ed-btn-sm ${categoryId === g.id ? '' : 'ed-btn-ghost'}`}
              onClick={() => setCategoryId(g.id)}
            >
              {g.name}
            </button>
          ))}
        </div>
      )}

      <section aria-label="选择考试" style={{ maxWidth: 720, margin: '0 auto' }}>
        {filtered.length === 0 ? (
          <p className="ed-caption" style={{ textAlign: 'center' }}>
            没有匹配的考试，换个关键词试试
          </p>
        ) : (
          <div className="ed-list">
            {filtered.map((c) => (
              <div key={c.id} className="ed-row" style={{ padding: '18px 4px' }}>
                <button
                  type="button"
                  className="ed-btn ed-btn-lg"
                  style={{ flex: 1, textAlign: 'left', justifyContent: 'flex-start', padding: '0 24px', height: 56 }}
                  onClick={() => setPicked(c)}
                  aria-pressed={picked?.id === c.id}
                >
                  {c.name}
                </button>
                {picked?.id === c.id && (
                  <button
                    type="button"
                    className="ed-btn ed-btn-lg ed-btn-accent"
                    style={{ marginLeft: 12 }}
                    disabled={busy}
                    onClick={() => void onConfirm()}
                  >
                    {busy ? '正在设置…' : `开始准备${c.name.length > 8 ? '本考试' : c.name} →`}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {picked && (
        <p className="ed-caption" style={{ textAlign: 'center', marginTop: 24 }}>
          科目与版本将自动关联，进入学习后按阶段展示。
        </p>
      )}
    </div>
  )
}
