import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../shared/auth/AuthContext'
import { useGoal } from '../../shared/learn/GoalContext'
import { courseApi, type MyCourse, type PublicCourse } from '../course/api'

/** 课程封面渐变（确定性轮换，与 Landing 一致） */
function coverClass(id: number): string {
  return `ed-cover-${(id % 4) + 1}`
}

/**
 * 课程中心（Stage 2.1 编辑式）：
 * - 匿名：公开课程展示（封面为视觉主体），点击进大纲；
 * - 登录：追加「继续学习」续播入口（个人数据，仅登录可见）。
 */
export default function CourseListPage() {
  const { isAuthenticated } = useAuth()
  const { goal } = useGoal()
  const [courses, setCourses] = useState<PublicCourse[]>([])
  const [myCourses, setMyCourses] = useState<MyCourse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    // 当前考试目标为主要上下文：课程按目标过滤（目标未选时展示全部）
    const tasks: Promise<unknown>[] = [
      courseApi.publicCourses(1, 50, goal?.certificateId, goal?.subjectId).then((p) => p.list),
    ]
    if (isAuthenticated) {
      tasks.push(courseApi.myCourses())
    }
    Promise.allSettled(tasks).then(([pub, mine]) => {
      if (cancelled) return
      setLoading(false)
      if (pub.status === 'fulfilled') setCourses(pub.value as PublicCourse[])
      else setError('课程列表加载失败，请刷新重试')
      if (mine && mine.status === 'fulfilled') setMyCourses(mine.value as MyCourse[])
    })
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, goal?.certificateId, goal?.subjectId])

  if (loading) {
    return (
      <div className="ed-container ed-page">
        <div className="ed-grid ed-grid-3" aria-hidden="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="ed-skeleton" style={{ aspectRatio: '16/9' }} />
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="ed-container ed-page">
      <header className="ed-page-head">
        <h1 className="ed-title">课程</h1>
        <p className="ed-lead">录播精讲，随到随学，自动续播。</p>
      </header>

      {error && (
        <div className="ed-error" role="alert">
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => window.location.reload()}>
            重试
          </button>
        </div>
      )}

      {isAuthenticated && myCourses.length > 0 && (
        <section aria-label="继续学习" style={{ marginBottom: 64 }}>
          <div className="ed-section-head">
            <div>
              <h2 className="ed-h2">继续学习</h2>
              <p className="ed-lead">上次学到哪里，接着学。</p>
            </div>
          </div>
          <div className="ed-grid ed-grid-3">
            {myCourses.map((c) => (
              <Link key={c.courseId} to={`/courses/${c.courseId}`} className="ed-card ed-card-hover">
                <div className={`ed-cover ${coverClass(c.courseId)}`}>
                  <span className="ed-cover-monogram">{c.title}</span>
                </div>
                <div className="ed-card-body">
                  <h3 className="ed-card-title">{c.title}</h3>
                  <p className="ed-card-sub">
                    上次学到「{c.lastLessonTitle || '课时'}」 · {Math.round(c.lastPositionSeconds / 60)} 分钟处
                  </p>
                  <div className="ed-card-meta" style={{ color: 'var(--ed-accent)' }}>
                    继续学习 →
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section aria-label="全部课程">
        <div className="ed-section-head">
          <div>
            <h2 className="ed-h2">{isAuthenticated ? '全部课程' : '公开课程'}</h2>
            <p className="ed-lead">
              {isAuthenticated ? '挑选一门，开始今天的进度。' : '先看大纲，满意再开始学习。'}
            </p>
          </div>
        </div>
        {courses.length === 0 ? (
          <div className="ed-empty">
            <div className="ed-empty-icon" aria-hidden="true">
              ▶
            </div>
            <p className="ed-empty-title">暂无课程</p>
            <p className="ed-empty-hint">课程发布后会在这里展示</p>
          </div>
        ) : (
          <div className="ed-grid ed-grid-3">
            {courses.map((c) => (
              <Link key={c.id} to={`/courses/${c.id}`} className="ed-card ed-card-hover">
                <div className={`ed-cover ${coverClass(c.id)}`}>
                  <span className="ed-cover-monogram">{c.title}</span>
                </div>
                <div className="ed-card-body">
                  <h3 className="ed-card-title">{c.title}</h3>
                  <p className="ed-card-sub">{c.description || '系统讲解核心考点。'}</p>
                  <div className="ed-card-meta">
                    {c.chapterCount} 章 · {c.lessonCount} 节
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
