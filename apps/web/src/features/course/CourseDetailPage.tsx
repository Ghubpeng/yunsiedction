import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useAuth } from '../../shared/auth/AuthContext'
import { useGoal } from '../../shared/learn/GoalContext'
import { AiAssistant } from '../../shared/ai/AiAssistant'
import type { NextStepRules } from '../../shared/ai/protocol'
import { nextStepApi } from '../learn/api'
import { courseApi, type CourseTree } from './api'

type ChapterState = 'mastered' | 'done' | 'learning' | 'todo'

/** 章节四态（Stage 2.4）：掌握=视频完成+章节练习完成；服务端返回 finished/practiceFinished，前端仅做展示映射 */
function stateOf(tree: CourseTree, chapterIdx: number): ChapterState {
  const ch = tree.chapters[chapterIdx]
  if (ch.finished === 1) return ch.practiceFinished === 1 ? 'mastered' : 'done'
  if (ch.lessons.some((l) => l.finished === 1 || l.positionSeconds > 0)) return 'learning'
  return 'todo'
}

/**
 * 课程详情 = 学习主线（Stage 2.1）：
 * 章/节带完成状态（✓ 已完成 / ● 学习中 / ○ 未开始）→ 章节完成后出现「章节练习」入口 →
 * 掌握反馈后引导进入下一章节。章节完成状态由后端计算（chapter.finished），前端不复制业务规则。
 */
export default function CourseDetailPage() {
  const { courseId } = useParams()
  const id = Number(courseId)
  const { isAuthenticated } = useAuth()
  const { goal } = useGoal()
  const location = useLocation()
  const [tree, setTree] = useState<CourseTree | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [nextStep, setNextStep] = useState<NextStepRules | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    const fetch = isAuthenticated ? courseApi.tree(id) : courseApi.outline(id)
    fetch
      .then((t) => {
        if (!cancelled) setTree(t)
      })
      .catch(() => {
        if (!cancelled) setError('课程加载失败，可能已下架')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id, isAuthenticated])

  // Stage 2.4：课程页学习助手固定入口带规则推荐（薄弱点/推荐课程/推荐练习，非 LLM）
  useEffect(() => {
    if (!isAuthenticated || !goal) {
      setNextStep(null)
      return
    }
    let cancelled = false
    nextStepApi
      .get(goal.certificateId)
      .then((v) => {
        if (!cancelled) setNextStep(v)
      })
      .catch(() => {
        if (!cancelled) setNextStep(null)
      })
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, goal?.certificateId])

  if (loading) {
    return (
      <div className="ed-container ed-page">
        <div className="ed-skeleton" style={{ height: 48, maxWidth: 420, marginBottom: 20 }} aria-hidden="true" />
        <div className="ed-skeleton" style={{ height: 220, borderRadius: 'var(--ed-radius-xl)' }} aria-hidden="true" />
      </div>
    )
  }

  if (error || !tree) {
    return (
      <div className="ed-container ed-page">
        <div className="ed-error inline-error" role="alert">
          <span>{error || '课程不存在'}</span>
          <Link to="/courses" className="ed-text-btn">
            返回课程
          </Link>
        </div>
      </div>
    )
  }

  const nextTodoIdx = tree.chapters.findIndex((ch) => ch.finished !== 1 && ch.status === 1)
  const nextTodoLesson =
    nextTodoIdx >= 0 ? tree.chapters[nextTodoIdx].lessons.find((l) => l.finished !== 1) ?? null : null

  return (
    <div className="ed-container ed-page">
      <Link to="/courses" className="ed-link-muted" style={{ fontSize: '0.9375rem' }}>
        ← 全部课程
      </Link>
      <header className="ed-page-head">
        {(tree.certificateName || tree.subjectName) && (
          <span className="ed-eyebrow">
            当前学习：{tree.certificateName ?? '考试'}
            {tree.subjectName ? ` · 阶段：${tree.subjectName}` : ''}
            {tree.chapterName ? ` · 章节：${tree.chapterName}` : ''}
          </span>
        )}
        <h1 className="ed-title">{tree.title}</h1>
        <p className="ed-lead">{tree.description}</p>
      </header>

      {isAuthenticated && (
        <div style={{ marginBottom: 32 }}>
          <AiAssistant
            nextStep={nextStep}
            context={{
              scenario: 'course',
              goal: goal
                ? {
                    certificateId: goal.certificateId,
                    subjectId: goal.subjectId,
                    versionId: goal.versionId,
                    certificateName: goal.certificateName,
                    subjectName: goal.subjectName,
                  }
                : undefined,
              course: { id: tree.courseId, title: tree.title },
            }}
            triggerLabel="学习助手"
          />
        </div>
      )}

      {tree.chapters.length === 0 ? (
        <div className="ed-empty">
          <div className="ed-empty-icon" aria-hidden="true">
            ▶
          </div>
          <p className="ed-empty-title">课程内容准备中</p>
          <p className="ed-empty-hint">章节发布后会在这里展示</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
          {tree.chapters.map((chapter, idx) => {
            const st = stateOf(tree, idx)
            const doneCount = chapter.lessons.filter((l) => l.finished === 1).length
            return (
              <section key={chapter.id} className="ed-card ed-card-pad">
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, marginBottom: 4 }}>
                  <span aria-hidden="true" style={{ fontSize: '1.125rem', lineHeight: 1 }}>
                    {st === 'mastered' ? '★' : st === 'done' ? '✓' : st === 'learning' ? '●' : '○'}
                  </span>
                  <h2 className="ed-h3">
                    第 {chapter.sort + 1} 章 · {chapter.title}
                  </h2>
                  {st === 'mastered' ? (
                    <span className="ed-badge ed-badge-mastered">
                      掌握{chapter.practiceScore != null ? ` · 练习 ${chapter.practiceScore} 分` : ''}
                    </span>
                  ) : st === 'done' ? (
                    <span className="ed-badge ed-badge-pass">已完成</span>
                  ) : st === 'learning' ? (
                    <span className="ed-badge ed-badge-accent">
                      学习中 · {doneCount}/{chapter.lessons.length}
                    </span>
                  ) : (
                    <span className="ed-badge">未开始</span>
                  )}
                </div>

                <ul className="ed-list">
                  {chapter.lessons.map((lesson, li) => {
                    const lessonPath = `/courses/${tree.courseId}/lessons/${lesson.id}`
                    const positionSeconds = isAuthenticated ? lesson.positionSeconds : 0
                    const finished = isAuthenticated ? lesson.finished : 0
                    return (
                      <li key={lesson.id} className="ed-row list-row">
                        <div className="ed-row-main">
                          <div className="ed-row-title">
                            {li + 1}. {lesson.title}
                            {finished === 1 && (
                              <span className="ed-badge ed-badge-pass" style={{ marginLeft: 10 }}>
                                ✓ 已完成
                              </span>
                            )}
                          </div>
                          <div className="ed-row-sub">
                            时长 {Math.round(lesson.durationSeconds / 60)} 分钟
                            {positionSeconds > 0 && finished !== 1 && (
                              <> · 已学 {Math.round(positionSeconds / 60)} 分钟</>
                            )}
                          </div>
                        </div>
                        {isAuthenticated ? (
                          <Link
                            to={lessonPath}
                            state={{ positionSeconds, title: lesson.title }}
                            className="ed-btn ed-btn-sm"
                          >
                            {positionSeconds > 0 && finished !== 1 ? '继续播放' : '开始学习'}
                          </Link>
                        ) : (
                          <Link to="/login" state={{ from: lessonPath }} className="ed-btn ed-btn-sm ed-btn-ghost">
                            登录后学习
                          </Link>
                        )}
                      </li>
                    )
                  })}
                  {chapter.lessons.length === 0 && <li className="ed-row-sub">本章节暂无可用课时</li>}
                </ul>

                {(st === 'done' || st === 'mastered') && (
                  <div
                    className="ed-card"
                    style={{
                      marginTop: 12,
                      borderRadius: 'var(--ed-radius-lg)',
                      background: 'var(--ed-bg-subtle)',
                      border: 'none',
                      padding: 20,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 16,
                      flexWrap: 'wrap',
                    }}
                  >
                    <div>
                      <div className="ed-row-title">
                        {st === 'mastered'
                          ? `本章已掌握${chapter.practiceScore != null ? `（练习 ${chapter.practiceScore} 分）` : ''}，可以再练一次巩固。`
                          : '学完这一章了，花几分钟检验一下是否真正掌握。'}
                      </div>
                      <div className="ed-row-sub">题目来自本章知识点，判分由服务端完成。</div>
                    </div>
                    <Link
                      to={`/courses/${tree.courseId}/chapters/${chapter.id}/practice`}
                      className="ed-btn"
                    >
                      {st === 'mastered' ? '再练一次' : '开始章节练习'}
                    </Link>
                  </div>
                )}
              </section>
            )
          })}
        </div>
      )}

      {isAuthenticated && nextTodoLesson && (
        <div style={{ textAlign: 'center', marginTop: 48 }}>
          <p className="ed-caption" style={{ marginBottom: 12 }}>
            继续学习主线
          </p>
          <Link
            to={`/courses/${tree.courseId}/lessons/${nextTodoLesson.id}`}
            state={{ positionSeconds: nextTodoLesson.positionSeconds, title: nextTodoLesson.title }}
            className="ed-btn ed-btn-lg"
          >
            下一章：{nextTodoLesson.title} →
          </Link>
        </div>
      )}

      {!isAuthenticated && (
        <p className="ed-caption" style={{ marginTop: 32, textAlign: 'center' }}>
          登录后可观看视频、保存学习进度 ·{' '}
          <Link to="/login" state={{ from: location.pathname }} className="ed-text-btn">
            开始学习
          </Link>
        </p>
      )}
    </div>
  )
}
