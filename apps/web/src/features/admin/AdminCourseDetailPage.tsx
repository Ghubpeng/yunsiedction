import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Badge, Card, Skeleton } from '../../shared/ui/primitives'
import {
  AdminForm,
  ConfirmButton,
  Field,
  Modal,
  PageHeader,
  StatusBadge,
  errText,
  useApiAction,
} from '../../admin/ui'
import { courseAdminApi, type AdminChapter, type CourseItem } from './courseAdminApi'

const COURSE_STATUS: Record<number, string> = { 1: '草稿', 2: '已发布', 3: '已下架' }

/** 课程内容管理：章/节 CRUD + 状态开关 + 视频上传（MinIO）+ 发布（服务端状态机/DataScope 裁决） */
export default function AdminCourseDetailPage() {
  const { id } = useParams()
  const courseId = Number(id)
  const [course, setCourse] = useState<CourseItem | null>(null)
  const [chapters, setChapters] = useState<AdminChapter[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [chapterModal, setChapterModal] = useState(false)
  const [lessonModal, setLessonModal] = useState<{ chapterId: number } | null>(null)
  const [videoLesson, setVideoLesson] = useState<number | null>(null)
  const [videoFile, setVideoFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const run = useApiAction()

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [c, chs] = await Promise.all([courseAdminApi.detail(courseId), courseAdminApi.chapters(courseId)])
      setCourse(c)
      setChapters(chs)
    } catch (e) {
      setError(errText(e))
    } finally {
      setLoading(false)
    }
  }, [courseId])

  useEffect(() => {
    void load()
  }, [load])

  const createChapter = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const fd = new FormData(e.currentTarget)
    const result = await run(() => courseAdminApi.createChapter({ courseId, title: String(fd.get('title')), sort: Number(fd.get('sort') || 0) }), '章节已创建')
    if (result) {
      setChapterModal(false)
      await load()
    }
  }

  const createLesson = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!lessonModal) return
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        courseAdminApi.createLesson({
          chapterId: lessonModal.chapterId,
          title: String(fd.get('title')),
          durationSeconds: Number(fd.get('durationSeconds') || 0),
          sort: Number(fd.get('sort') || 0),
        }),
      '小节已创建',
    )
    if (result) {
      setLessonModal(null)
      await load()
    }
  }

  const uploadVideo = async () => {
    if (!videoFile || videoLesson === null) return
    setUploading(true)
    try {
      await courseAdminApi.uploadVideo(videoLesson, videoFile)
      setVideoLesson(null)
      setVideoFile(null)
      await run(async () => undefined, '视频已上传')
    } catch (e) {
      setError(errText(e))
    } finally {
      setUploading(false)
    }
  }

  if (loading) {
    return (
      <div className="page">
        <Card>
          <Skeleton lines={6} />
        </Card>
      </div>
    )
  }
  if (error || !course) {
    return (
      <div className="page">
        <div className="inline-error">{error || '课程不存在'}</div>
        <Link to="/admin/courses" className="link-btn">
          ← 返回课程管理
        </Link>
      </div>
    )
  }

  return (
    <div className="page">
      <Link to="/admin/courses" className="link-btn">
        ← 返回课程管理
      </Link>
      <PageHeader
        title={course.title}
        sub={`ID ${course.id} · ${course.chapterName ? `章节 ${course.chapterName} · ` : ''}${course.chapterCount} 章 ${course.lessonCount} 节`}
        extra={
          <span className="row-actions">
            <StatusBadge labels={COURSE_STATUS} value={course.status} />
            {(course.status === 1 || course.status === 3) && (
              <button type="button" className="btn btn-primary btn-sm" onClick={() => void run(() => courseAdminApi.publish(courseId), '已发布').then((ok) => { if (ok) void load() })}>
                发布课程
              </button>
            )}
            {course.status === 2 && (
              <button type="button" className="btn btn-secondary btn-sm" onClick={() => void run(() => courseAdminApi.unpublish(courseId), '已下架').then((ok) => { if (ok) void load() })}>
                下架
              </button>
            )}
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setChapterModal(true)}>
              新建章节
            </button>
          </span>
        }
      />
      {error && <div className="inline-error">{error}</div>}

      {chapters.length === 0 ? (
        <Card>
          <div className="empty-state">
            <div className="empty-icon">▶</div>
            <p className="empty-title">还没有章节</p>
            <p className="empty-hint">先创建章节，再在章节下创建小节并上传视频</p>
          </div>
        </Card>
      ) : (
        chapters.map((ch) => (
          <Card
            key={ch.id}
            title={`${ch.title}（章节 #${ch.id}）`}
            extra={
              <span className="row-actions">
                <StatusBadge labels={{ 0: '禁用', 1: '启用' }} value={ch.status} />
                <button type="button" className="link-btn" onClick={() => void run(() => courseAdminApi.updateChapterStatus(ch.id, ch.status === 1 ? 0 : 1), '章节状态已更新').then((ok) => { if (ok) void load() })}>
                  {ch.status === 1 ? '禁用' : '启用'}
                </button>
                <button type="button" className="link-btn" onClick={() => setLessonModal({ chapterId: ch.id })}>
                  新建小节
                </button>
                <ConfirmButton label="删除章节" confirmText="确认删除" onConfirm={() => void run(() => courseAdminApi.deleteChapter(ch.id), '章节已删除').then((ok) => { if (ok) void load() })} />
              </span>
            }
          >
            <ul className="plain-list">
              {ch.lessons.map((lesson) => (
                <li key={lesson.id} className="list-row">
                  <div className="list-row-main">
                    <div className="list-row-title">
                      {lesson.title}（小节 #{lesson.id}）
                      <Badge tone={lesson.status === 1 ? 'success' : 'neutral'}>{lesson.status === 1 ? '启用' : '禁用'}</Badge>
                    </div>
                    <div className="list-row-sub">时长 {lesson.durationSeconds} 秒</div>
                  </div>
                  <span className="row-actions">
                    <button type="button" className="link-btn" onClick={() => setVideoLesson(lesson.id)}>
                      {uploading && videoLesson === lesson.id ? '上传中…' : '上传视频'}
                    </button>
                    <button type="button" className="link-btn" onClick={() => void run(() => courseAdminApi.updateLessonStatus(lesson.id, lesson.status === 1 ? 0 : 1), '小节状态已更新').then((ok) => { if (ok) void load() })}>
                      {lesson.status === 1 ? '禁用' : '启用'}
                    </button>
                    <ConfirmButton label="删除" confirmText="确认删除" onConfirm={() => void run(() => courseAdminApi.deleteLesson(lesson.id), '小节已删除').then((ok) => { if (ok) void load() })} />
                  </span>
                </li>
              ))}
              {ch.lessons.length === 0 && <li className="list-row-sub">本章节暂无小节</li>}
            </ul>
          </Card>
        ))
      )}

      <Modal open={chapterModal} title="新建章节" onClose={() => setChapterModal(false)}>
        <AdminForm onSubmit={createChapter} submitLabel="创建">
          <Field label="章节标题">
            <input className="input" name="title" required />
          </Field>
          <Field label="排序">
            <input className="input" name="sort" type="number" defaultValue={0} />
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={lessonModal !== null} title="新建小节" onClose={() => setLessonModal(null)}>
        <AdminForm onSubmit={createLesson} submitLabel="创建">
          <Field label="小节标题">
            <input className="input" name="title" required />
          </Field>
          <Field label="时长（秒）" hint="进度截断与完成判定依据">
            <input className="input" name="durationSeconds" type="number" min={0} defaultValue={600} />
          </Field>
          <Field label="排序">
            <input className="input" name="sort" type="number" defaultValue={0} />
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={videoLesson !== null} title="上传视频（MinIO）" onClose={() => { setVideoLesson(null); setVideoFile(null) }}>
        <Field label="视频文件" hint="原文件直存 MinIO；播放走短有效期预签名 URL（前端不缓存）">
          <label className={`upload-btn ${uploading ? 'upload-disabled' : ''}`}>
            选择视频文件
            <input
              type="file"
              accept="video/*"
              disabled={uploading}
              onChange={(e) => {
                const f = e.target.files?.[0]
                setVideoFile(f ?? null)
                e.target.value = ''
              }}
            />
          </label>
        </Field>
        {videoFile && <div className="list-row-sub">{videoFile.name}（{Math.round(videoFile.size / 1024)} KB）</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-primary btn-md" disabled={!videoFile || uploading} onClick={() => void uploadVideo()}>
            {uploading ? '上传中…' : '确认上传'}
          </button>
        </div>
      </Modal>
    </div>
  )
}
