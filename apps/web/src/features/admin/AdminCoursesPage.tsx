import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Card } from '../../shared/ui/primitives'
import {
  AdminForm,
  ConfirmButton,
  DataTable,
  Field,
  Modal,
  PageHeader,
  Pagination,
  StatusBadge,
  errText,
  useApiAction,
} from '../../admin/ui'
import { certificateAdminApi, type CertItem } from './certificateAdminApi'
import { courseAdminApi, type CourseItem } from './courseAdminApi'
import { subjectAdminApi, type NodeItem, type SubjectItem, type VersionItem } from './subjectAdminApi'
import { sysAdminApi, type AdminUser } from './sysApi'

const COURSE_STATUS: Record<number, string> = { 1: '草稿', 2: '已发布', 3: '已下架' }

/** 课程管理（教师 DataScope 由后端 CourseAccessGuard 裁决；前端仅展示服务端返回范围） */
export default function AdminCoursesPage() {
  const [certs, setCerts] = useState<CertItem[]>([])
  const [teachers, setTeachers] = useState<AdminUser[]>([])
  const [certId, setCertId] = useState<number | ''>('')
  const [subjects, setSubjects] = useState<SubjectItem[]>([])
  const [versions, setVersions] = useState<VersionItem[]>([])
  const [formSubjectId, setFormSubjectId] = useState<number | ''>('')
  const [formVersionId, setFormVersionId] = useState<number | ''>('')
  const [chapters, setChapters] = useState<NodeItem[]>([])
  const [subjectNames, setSubjectNames] = useState<Map<number, string>>(new Map())
  const [rows, setRows] = useState<CourseItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const run = useApiAction()

  useEffect(() => {
    Promise.allSettled([certificateAdminApi.certs(1, 100), sysAdminApi.users(1, 100)]).then(([c, u]) => {
      if (c.status === 'fulfilled') setCerts(c.value.list)
      if (u.status === 'fulfilled') setTeachers(u.value.list.filter((x) => x.userType === 2 || x.userType === 3))
    })
  }, [])

  // 科目名称表（列表列「内容树」展示用）
  useEffect(() => {
    Promise.allSettled(certs.map((c) => subjectAdminApi.subjects(c.id))).then((results) => {
      const map = new Map<number, string>()
      results.forEach((r, i) => {
        if (r.status === 'fulfilled') {
          r.value.forEach((s) => map.set(s.id, s.name))
        } else {
          void i
        }
      })
      setSubjectNames(map)
    })
  }, [certs])

  // 新建课程：选择证书后联动加载版本与科目（内容树：证书→版本→科目→章节→课程）
  useEffect(() => {
    if (certId === '') {
      setSubjects([])
      setVersions([])
      setFormSubjectId('')
      setFormVersionId('')
      setChapters([])
      return
    }
    let cancelled = false
    Promise.allSettled([subjectAdminApi.subjects(Number(certId)), subjectAdminApi.versions(Number(certId))]).then(
      ([s, v]) => {
        if (cancelled) return
        if (s.status === 'fulfilled') setSubjects(s.value)
        if (v.status === 'fulfilled') {
          setVersions(v.value)
          const cur = v.value.find((x) => x.status === 2) ?? v.value[0]
          if (cur) setFormVersionId(cur.id)
        }
      },
    )
    return () => {
      cancelled = true
    }
  }, [certId])

  // 选择科目后加载其章节（node_type=1；所选版本或当前版本树）
  useEffect(() => {
    if (certId === '' || formSubjectId === '') {
      setChapters([])
      return
    }
    let cancelled = false
    const versionId = formVersionId !== '' ? Number(formVersionId) : versions.find((v) => v.status === 2)?.id ?? versions[0]?.id
    if (versionId === undefined) {
      setChapters([])
      return
    }
    subjectAdminApi
      .tree(versionId)
      .then((trees) => {
        if (cancelled) return
        const t = trees.find((x) => x.subjectId === Number(formSubjectId))
        setChapters((t?.children ?? []).filter((n) => n.nodeType === 1 && n.enabled === 1))
      })
      .catch(() => {
        if (!cancelled) setChapters([])
      })
    return () => {
      cancelled = true
    }
  }, [certId, formSubjectId, formVersionId, versions])

  const load = useCallback(async (page = pageNum) => {
    setLoading(true)
    setError('')
    try {
      const p = await courseAdminApi.page(page, 20)
      setRows(p.list)
      setTotal(p.total)
    } catch (e) {
      setError(errText(e))
    } finally {
      setLoading(false)
    }
  }, [pageNum])

  useEffect(() => {
    void load(pageNum)
  }, [pageNum, load])

  const create = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const fd = new FormData(e.currentTarget)
    const chapterRaw = fd.get('chapterId')
    const result = await run(
      () =>
        courseAdminApi.create({
          certificateId: Number(certId),
          subjectId: Number(formSubjectId),
          versionId: formVersionId === '' ? null : Number(formVersionId),
          chapterId: chapterRaw ? Number(chapterRaw) : null,
          teacherId: Number(fd.get('teacherId')),
          title: String(fd.get('title')),
          description: String(fd.get('description') || ''),
        }),
      '课程已创建（请补充章节内容后发布）',
    )
    if (result) {
      setCreateOpen(false)
      setCertId('')
      setFormSubjectId('')
      setFormVersionId('')
      setChapters([])
      void load()
    }
  }

  return (
    <div className="page">
      <PageHeader
        title="课程管理"
        sub="教师仅可见/管理自己名下课程（服务端 DataScope）"
        extra={
          <button type="button" className="btn btn-primary btn-md" onClick={() => setCreateOpen(true)}>
            新建课程
          </button>
        }
      />
      <Card>
        {error && <div className="inline-error">{error}</div>}
        <DataTable<CourseItem>
          loading={loading}
          rows={rows}
          rowKey={(r) => r.id}
          columns={[
            { key: 'id', title: 'ID', width: '60px', render: (r) => <span className="num">{r.id}</span> },
            { key: 'title', title: '标题', render: (r) => <Link className="link-btn" to={`/admin/courses/${r.id}`}>{r.title}</Link> },
            {
              key: 'tree',
              title: '内容树',
              render: (r) => (
                <span className="caption">
                  {r.subjectId ? subjectNames.get(r.subjectId) ?? `科目#${r.subjectId}` : '未指定科目'}
                  {r.chapterName ? ` / ${r.chapterName}` : ''}
                </span>
              ),
            },
            { key: 'chapterCount', title: '章', render: (r) => <span className="num">{r.chapterCount}</span> },
            { key: 'lessonCount', title: '节', render: (r) => <span className="num">{r.lessonCount}</span> },
            { key: 'status', title: '状态', render: (r) => <StatusBadge labels={COURSE_STATUS} value={r.status} /> },
            {
              key: 'actions',
              title: '操作',
              render: (r) => (
                <span className="row-actions">
                  {r.status === 1 && (
                    <button type="button" className="link-btn" onClick={() => void run(() => courseAdminApi.publish(r.id), '已发布').then((ok) => { if (ok) void load() })}>
                      发布
                    </button>
                  )}
                  {r.status === 2 && (
                    <button type="button" className="link-btn" onClick={() => void run(() => courseAdminApi.unpublish(r.id), '已下架').then((ok) => { if (ok) void load() })}>
                      下架
                    </button>
                  )}
                  <Link className="link-btn" to={`/admin/courses/${r.id}`}>
                    内容管理
                  </Link>
                  <ConfirmButton label="删除" confirmText="确认删除" onConfirm={() => void run(() => courseAdminApi.delete(r.id), '已删除').then((ok) => { if (ok) void load() })} />
                </span>
              ),
            },
          ]}
        />
        <Pagination pageNum={pageNum} pageSize={20} total={total} onChange={(p) => setPageNum(p)} />
      </Card>

      <Modal open={createOpen} title="新建课程" onClose={() => { setCreateOpen(false); setCertId(''); setFormSubjectId(''); setFormVersionId(''); setChapters([]) }} wide>
        <AdminForm onSubmit={create} submitLabel="创建">
          <Field label="所属证书（内容树：证书 → 版本 → 科目 → 章节 → 课程）">
            <select
              className="input"
              name="certificateId"
              required
              value={certId}
              onChange={(e) => {
                const v = e.target.value === '' ? '' : Number(e.target.value)
                setCertId(v)
                setFormSubjectId('')
                setChapters([])
              }}
            >
              <option value="">请选择证书</option>
              {certs.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="知识版本（课程归属该版本内容树）">
            <select className="input" name="versionId" required disabled={versions.length === 0} value={formVersionId} onChange={(e) => setFormVersionId(e.target.value === '' ? '' : Number(e.target.value))}>
              {versions.length === 0 && <option value="">证书下暂无版本</option>}
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.versionNo} · {v.name}
                  {v.status === 2 ? '（当前）' : ''}
                </option>
              ))}
            </select>
          </Field>
          <Field label="考试科目（课程按目标推荐给学员）">
            <select className="input" name="subjectId" required disabled={subjects.length === 0} value={formSubjectId} onChange={(e) => setFormSubjectId(e.target.value === '' ? '' : Number(e.target.value))}>
              <option value="">请选择科目</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="学习章节（禁止孤立课程；先选科目后加载其章节）">
            <select className="input" name="chapterId" required disabled={chapters.length === 0} key={`${formSubjectId}-${formVersionId}`} defaultValue="">
              <option value="">请选择章节</option>
              {chapters.map((ch) => (
                <option key={ch.id} value={ch.id}>
                  {ch.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="授课教师">
            <select className="input" name="teacherId" required>
              {teachers.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.nickname || t.username}（{t.username}）
                </option>
              ))}
            </select>
          </Field>
          <Field label="课程标题">
            <input className="input" name="title" required />
          </Field>
          <Field label="课程简介">
            <textarea className="input" name="description" rows={3} />
          </Field>
        </AdminForm>
      </Modal>
    </div>
  )
}
