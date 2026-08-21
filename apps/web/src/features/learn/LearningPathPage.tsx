import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useGoal } from '../../shared/learn/GoalContext'
import { courseApi, type LearningPath } from '../course/api'

const CHAPTER_STATE: Record<string, { label: string; icon: string; cls: string }> = {
  todo: { label: '未开始', icon: '○', cls: '' },
  learning: { label: '学习中', icon: '●', cls: 'ed-badge-accent' },
  done: { label: '已完成', icon: '✓', cls: 'ed-badge-pass' },
  mastered: { label: '掌握', icon: '★', cls: 'ed-badge-mastered' },
}

/**
 * 学习路径（Stage 2.4）：以考试目标驱动的课程学习路径主页。
 * 当前考试 / 总完成度 / 学习阶段 / 下一学习任务；课程按 证书→科目→章节→课时 展示。
 * 章节四态与完成度均由服务端计算（前端不复制业务规则）。
 */
export default function LearningPathPage() {
  const { goal } = useGoal()
  const [path, setPath] = useState<LearningPath | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    courseApi
      .learningPath(goal?.certificateId)
      .then((p) => {
        if (!cancelled) setPath(p)
      })
      .catch(() => {
        if (!cancelled) setPath(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [goal?.certificateId])

  if (!goal) {
    return (
      <div className="ed-container ed-page">
        <div className="ed-empty">
          <div className="ed-empty-icon" aria-hidden="true">
            🎯
          </div>
          <p className="ed-empty-title">先选择考试目标</p>
          <p className="ed-empty-hint">选择考试后，系统按 证书→科目→章节→课时 为你生成学习路径</p>
          <Link to="/goal-select" state={{ from: '/learn-path' }} className="ed-btn ed-btn-lg">
            选择考试
          </Link>
        </div>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="ed-container ed-page">
        <div className="ed-skeleton" style={{ height: 64, maxWidth: 480, marginBottom: 24 }} aria-hidden="true" />
        <div className="ed-skeleton" style={{ height: 220, borderRadius: 'var(--ed-radius-xl)' }} aria-hidden="true" />
      </div>
    )
  }

  if (!path) {
    return (
      <div className="ed-container ed-page">
        <div className="ed-error inline-error" role="alert">
          <span>学习路径加载失败，请稍后重试</span>
          <button type="button" className="ed-text-btn" onClick={() => window.location.reload()}>
            重试
          </button>
        </div>
      </div>
    )
  }

  const stateOf = (s: string) => CHAPTER_STATE[s] ?? CHAPTER_STATE.todo
  const nextTask = path.nextTask

  return (
    <div className="ed-container ed-page">
      <header className="ed-page-head" style={{ marginBottom: 32 }}>
        <span className="ed-eyebrow">学习路径</span>
        <h1 className="ed-display" style={{ fontSize: 'var(--ed-h1)' }}>
          {path.certificateName ?? '备考路径'}
        </h1>
        <p className="ed-lead" style={{ marginTop: 12 }}>
          以考试目标驱动：证书 → 科目 → 章节 → 课时
        </p>
      </header>

      {/* 总完成度 + 学习阶段 */}
      <section className="ed-card ed-card-pad" style={{ marginBottom: 28 }} aria-label="学习进度">
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <div>
            <h2 className="ed-h3" style={{ marginBottom: 4 }}>
              总完成度
            </h2>
            <p className="ed-caption">
              已完成 {path.finishedChapters}/{path.totalChapters} 章节 · 掌握 {path.masteredChapters} 章节
            </p>
          </div>
          <span className="ed-badge ed-badge-accent" style={{ fontSize: '1rem', padding: '8px 16px' }}>
            {path.stage}阶段 · {path.percent}%
          </span>
        </div>
        <div className="ed-progress" style={{ marginTop: 16, height: 10 }}>
          <div className="ed-progress-fill" style={{ width: `${path.percent}%` }} />
        </div>
      </section>

      {/* 下一学习任务 */}
      <section className="ed-card ed-card-pad" style={{ marginBottom: 32 }} aria-label="下一学习任务">
        <h2 className="ed-h3" style={{ marginBottom: 8 }}>
          下一学习任务
        </h2>
        {nextTask ? (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
            <div>
              <div className="ed-row-title">
                《{nextTask.courseTitle}》 · {nextTask.chapterTitle}
                {nextTask.lessonTitle ? ` · ${nextTask.lessonTitle}` : ''}
              </div>
              <p className="ed-row-sub">
                {nextTask.type === 'practice' ? '视频已学完，完成章节练习即可掌握本章' : '按路径继续下一课时'}
              </p>
            </div>
            {nextTask.type === 'practice' ? (
              <Link
                to={`/courses/${nextTask.courseId}/chapters/${nextTask.chapterId}/practice`}
                className="ed-btn"
              >
                开始章节练习
              </Link>
            ) : (
              <Link to={`/courses/${nextTask.courseId}/lessons/${nextTask.lessonId}`} className="ed-btn">
                继续学习 →
              </Link>
            )}
          </div>
        ) : (
          <p className="ed-caption">
            {path.totalChapters === 0 ? '目标下暂无课程内容，管理员正在准备中' : '全部章节已完成，等待下一批内容或复习巩固'}
          </p>
        )}
      </section>

      {/* 证书→科目→章节→课时 */}
      {path.subjects.length === 0 ? (
        <div className="ed-card ed-card-pad" style={{ textAlign: 'center' }}>
          <p className="ed-empty-title" style={{ fontSize: '1rem' }}>
            暂无课程
          </p>
          <p className="ed-empty-hint">目标证书下还没有已发布课程</p>
        </div>
      ) : (
        path.subjects.map((subject) => (
          <section key={subject.subjectId ?? 'none'} style={{ marginBottom: 36 }} aria-label={subject.subjectName}>
            <h2 className="ed-h2" style={{ fontSize: '1.25rem', marginBottom: 16 }}>
              {subject.subjectName}
            </h2>
            {subject.courses.map((course) => (
              <div key={course.courseId} className="ed-card ed-card-pad" style={{ marginBottom: 16 }}>
                <Link to={`/courses/${course.courseId}`} className="ed-arrow-link" style={{ fontSize: '1.0625rem' }}>
                  《{course.title}》 →
                </Link>
                <ul className="ed-list" style={{ marginTop: 12 }}>
                  {course.chapters.map((ch) => {
                    const st = stateOf(ch.state)
                    return (
                      <li key={ch.chapterId} className="ed-row" style={{ padding: '12px 0' }}>
                        <div className="ed-row-main">
                          <div className="ed-row-title">
                            <span aria-hidden="true" style={{ marginRight: 8 }}>
                              {st.icon}
                            </span>
                            {ch.title}
                          </div>
                        </div>
                        <span className={`ed-badge ${st.cls}`} style={{ marginRight: 10 }}>
                          {st.label}
                        </span>
                        {ch.practiceScore != null && (
                          <span className="ed-caption num" style={{ minWidth: 56, textAlign: 'right' }}>
                            练习 {ch.practiceScore} 分
                          </span>
                        )}
                      </li>
                    )
                  })}
                </ul>
              </div>
            ))}
          </section>
        ))
      )}
    </div>
  )
}
