import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../shared/auth/AuthContext'
import { courseApi, type PublicCourse } from '../course/api'
import { friendlyMessage } from '../../shared/api/types'

const PILLARS = [
  { icon: '▶', title: '录播课程', sub: '系统精讲，随时续播' },
  { icon: '✎', title: '智能练习', sub: '薄弱点自动沉淀' },
  { icon: '⌁', title: '模拟考试', sub: '真实组卷，即时判分' },
  { icon: '◉', title: '学习档案', sub: '掌握度一目了然' },
]

/** 封面渐变（确定性，按课程 id 轮换；无外部图片依赖） */
function coverClass(id: number): string {
  return `ed-cover-${(id % 4) + 1}`
}

/**
 * 公开首页（匿名）：Apple 官网式 editorial 首屏。
 * 需要个人数据的功能不在此页出现；「开始学习」引导登录，「进入 Demo」一键直达演示账号。
 */
export default function LandingPage() {
  const { isAuthenticated, demoLogin } = useAuth()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [courses, setCourses] = useState<PublicCourse[]>([])
  const [demoBusy, setDemoBusy] = useState(false)
  const [demoError, setDemoError] = useState('')

  const demoEnabled = import.meta.env.DEV || import.meta.env.VITE_DEMO_ENABLED === 'true'

  useEffect(() => {
    let cancelled = false
    courseApi
      .publicCourses(1, 6)
      .then((page) => {
        if (!cancelled) setCourses(page.list)
      })
      .catch(() => {
        /* 精选课程加载失败不阻塞首屏 */
      })
    return () => {
      cancelled = true
    }
  }, [])

  // ?demo=1 一键进入：真实 demo-login 签发令牌后进入学习首页
  useEffect(() => {
    if (!searchParams.get('demo') || isAuthenticated || !demoEnabled) return
    let cancelled = false
    setDemoBusy(true)
    setDemoError('')
    demoLogin()
      .then(() => {
        if (!cancelled) navigate('/', { replace: true })
      })
      .catch((e) => {
        if (!cancelled) {
          setDemoError(friendlyMessage(e))
          setSearchParams({}, { replace: true })
        }
      })
      .finally(() => {
        if (!cancelled) setDemoBusy(false)
      })
    return () => {
      cancelled = true
    }
  }, [searchParams, isAuthenticated, demoEnabled, demoLogin, navigate, setSearchParams])

  return (
    <div className="ed-container">
      <section className="ed-hero">
        <span className="ed-eyebrow">云思学习 · 护士执业资格考试</span>
        <h1 className="ed-display">学习，从这里开始</h1>
        <p className="ed-lead">
          课程、题库、模拟考试与学习档案，一站式备考体验。
          <br />
          无需注册，先看看课程内容，再决定是否开始。
        </p>
        <div className="ed-hero-cta">
          {demoEnabled && !isAuthenticated && (
            <button
              type="button"
              className="ed-btn ed-btn-lg ed-btn-accent"
              onClick={() => setSearchParams({ demo: '1' })}
              disabled={demoBusy}
            >
              {demoBusy ? '正在进入…' : '进入 Demo'}
            </button>
          )}
          <Link to="/login" className="ed-btn ed-btn-lg ed-btn-ghost">
            {isAuthenticated ? '进入学习' : '登录'}
          </Link>
        </div>
        {demoError && <p className="ed-hero-note" style={{ color: '#d70015' }}>{demoError}</p>}
        {demoEnabled && !demoError && (
          <p className="ed-hero-note">Demo 演示数据一键就绪 · 生产环境不提供该入口</p>
        )}
      </section>

      {courses.length > 0 && (
        <section className="ed-section-sm" aria-label="精选课程">
          <div className="ed-section-head">
            <div>
              <h2 className="ed-h2">精选课程</h2>
              <p className="ed-lead">录播精讲，随到随学，自动续播。</p>
            </div>
            <Link to="/courses" className="ed-arrow-link">
              探索全部课程 →
            </Link>
          </div>
          <div className="ed-grid ed-grid-3">
            {courses.map((c) => (
              <Link key={c.id} to={`/courses/${c.id}`} className="ed-card ed-card-hover">
                <div className={`ed-cover ${coverClass(c.id)}`}>
                  <span className="ed-cover-monogram">{c.title}</span>
                </div>
                <div className="ed-card-body">
                  <h3 className="ed-card-title">{c.title}</h3>
                  <p className="ed-card-sub">
                    {c.description || '系统讲解核心考点，配套练习与模拟考试。'}
                  </p>
                  <div className="ed-card-meta">
                    {c.chapterCount} 章 · {c.lessonCount} 节
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section className="ed-section" aria-label="学习方式">
        <div className="ed-grid ed-grid-4">
          {PILLARS.map((p) => (
            <div key={p.title} className="ed-stat">
              <div className="ed-stat-label" aria-hidden="true">
                {p.icon}
              </div>
              <div className="ed-stat-value" style={{ fontSize: '1.375rem' }}>
                {p.title}
              </div>
              <p className="ed-caption" style={{ marginTop: 6 }}>
                {p.sub}
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
