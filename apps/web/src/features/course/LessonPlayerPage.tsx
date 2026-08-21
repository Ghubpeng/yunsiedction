import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { friendlyMessage } from '../../shared/api/types'
import { AiAssistant } from '../../shared/ai/AiAssistant'
import { courseApi, type PlayTicket } from './api'

const REPORT_INTERVAL_MS = 5000
const REFRESH_AHEAD_MS = 5 * 60 * 1000

/**
 * 课时播放页（Stage 2.1 沉浸式）：
 * 视频为绝对视觉主体（暗色舞台），文案与操作克制。
 * 播放凭证/续播/节流上报逻辑与 Stage 1.8 完全一致，仅呈现层重构。
 */
export default function LessonPlayerPage() {
  const { courseId, lessonId } = useParams()
  const course = Number(courseId)
  const lesson = Number(lessonId)
  const location = useLocation()
  const navState = location.state as { positionSeconds?: number; title?: string } | null

  const [ticket, setTicket] = useState<PlayTicket | null>(null)
  const [error, setError] = useState('')
  const resumePosition = navState?.positionSeconds ?? 0
  const [finished, setFinished] = useState(false)

  const videoRef = useRef<HTMLVideoElement | null>(null)
  const lastReportRef = useRef(0)
  const resumeDoneRef = useRef(false)
  const ticketRef = useRef<PlayTicket | null>(null)

  const fetchTicket = useCallback(async () => {
    try {
      const t = await courseApi.play(lesson)
      ticketRef.current = t
      setTicket(t)
      setError('')
    } catch (e) {
      setError(friendlyMessage(e))
    }
  }, [lesson])

  useEffect(() => {
    void fetchTicket()
    return () => {
      const video = videoRef.current
      if (video && !video.paused && !video.ended) {
        const pos = Math.floor(video.currentTime)
        void courseApi.reportProgress(lesson, pos).catch(() => {})
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetchTicket])

  const reportPosition = useCallback(() => {
    const video = videoRef.current
    if (!video || Number.isNaN(video.currentTime)) {
      return
    }
    const pos = Math.floor(video.currentTime)
    void courseApi.reportProgress(lesson, pos).catch(() => {})
  }, [lesson])

  const scheduleUrlRefresh = useCallback(() => {
    const t = ticketRef.current
    if (!t) return
    const expiresAt = new Date(t.expiresAt).getTime()
    const delay = Math.max(10_000, expiresAt - Date.now() - REFRESH_AHEAD_MS)
    const timer = window.setTimeout(() => {
      void fetchTicket().then(() => {
        const video = videoRef.current
        if (video && !video.paused) {
          const pos = video.currentTime
          video.load()
          const onLoaded = () => {
            video.currentTime = pos
            void video.play()
            video.removeEventListener('loadedmetadata', onLoaded)
          }
          video.addEventListener('loadedmetadata', onLoaded)
        }
      })
    }, delay)
    return timer
  }, [fetchTicket])

  useEffect(() => {
    if (!ticket) return
    const timer = scheduleUrlRefresh()
    return () => window.clearTimeout(timer)
  }, [ticket, scheduleUrlRefresh])

  const onLoadedMetadata = () => {
    const video = videoRef.current
    if (!video || resumeDoneRef.current) return
    const target = Math.min(resumePosition, Number.isFinite(video.duration) ? video.duration : resumePosition)
    if (target > 1) {
      video.currentTime = target
    }
    resumeDoneRef.current = true
  }

  const onTimeUpdate = () => {
    const video = videoRef.current
    if (!video) return
    const now = Date.now()
    if (now - lastReportRef.current >= REPORT_INTERVAL_MS && !video.paused) {
      lastReportRef.current = now
      reportPosition()
    }
    if (video.duration > 0 && video.currentTime / video.duration >= 0.98 && !finished) {
      setFinished(true)
      reportPosition()
    }
  }

  const onVideoError = () => {
    if (ticketRef.current) {
      ticketRef.current = null
      void fetchTicket()
    } else {
      setError('视频加载失败，请稍后重试')
    }
  }

  if (error) {
    return (
      <div className="ed-immersive">
        <Link to={`/courses/${course}`} className="ed-link-muted">
          ← 返回课程
        </Link>
        <div className="ed-error inline-error" role="alert" style={{ marginTop: 24 }}>
          <span>{error}</span>
          <button type="button" className="ed-text-btn" onClick={() => void fetchTicket()}>
            重试
          </button>
        </div>
      </div>
    )
  }

  if (!ticket) {
    return (
      <div className="ed-immersive" style={{ textAlign: 'center', paddingTop: 120 }}>
        <div className="ed-skeleton" style={{ width: 120, height: 20, margin: '0 auto' }} aria-hidden="true" />
        <p className="ed-caption" style={{ marginTop: 16 }}>
          正在获取播放凭证…
        </p>
      </div>
    )
  }

  return (
    <div className="ed-immersive">
      <Link to={`/courses/${course}`} className="ed-link-muted" style={{ marginBottom: 20, display: 'inline-block' }}>
        ← 返回课程
      </Link>
      <header style={{ marginBottom: 24 }}>
        <span className="ed-eyebrow" style={{ marginBottom: 8 }}>
          正在学习
        </span>
        <h1 className="ed-title" style={{ fontSize: 'clamp(1.375rem, 3vw, 2rem)' }}>
          {navState?.title ?? '课时'}
        </h1>
      </header>

      <div className="ed-player">
        <video
          ref={videoRef}
          src={ticket.url}
          controls
          preload="metadata"
          playsInline
          onLoadedMetadata={onLoadedMetadata}
          onTimeUpdate={onTimeUpdate}
          onPause={() => {
            reportPosition()
          }}
          onError={onVideoError}
        />
      </div>

      {resumePosition > 0 && (
        <p className="ed-caption" style={{ marginTop: 16 }}>
          已从上次位置（{Math.round(resumePosition / 60)} 分钟处）继续播放
        </p>
      )}
      {finished ? (
        <div className="ed-empty" style={{ padding: '40px 0 0' }}>
          <div className="ed-empty-icon" aria-hidden="true">
            ✓
          </div>
          <p className="ed-empty-title">本课时已学完</p>
          <p className="ed-empty-hint">学习进度已自动保存，可返回课程继续下一节</p>
          <Link to={`/courses/${course}`} className="ed-btn">
            返回课程
          </Link>
        </div>
      ) : (
        <p className="ed-caption" style={{ marginTop: 16 }}>
          学习进度每 5 秒自动保存，离开页面也会保存。
        </p>
      )}

      <div style={{ marginTop: 24 }}>
        <AiAssistant
          context={{
            scenario: 'course',
            course: { id: course, title: navState?.title ?? '课时' },
            lesson: { id: lesson, title: navState?.title ?? '课时' },
          }}
          triggerLabel="学习助手 · 解释这一节"
        />
      </div>
    </div>
  )
}
