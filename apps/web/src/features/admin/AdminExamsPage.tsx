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
import { examAdminApi, type ExamItem } from './examAdminApi'
import { subjectAdminApi, type SubjectItem } from './subjectAdminApi'

const EXAM_STATUS: Record<number, string> = { 1: '草稿', 2: '已发布', 3: '已下架' }

/** 考试管理：创建（规则组卷配置）→ 组卷 → 发布 → 成绩查看入口（状态转换由后端校验） */
export default function AdminExamsPage() {
  const [certs, setCerts] = useState<CertItem[]>([])
  const [subjects, setSubjects] = useState<SubjectItem[]>([])
  const [certId, setCertId] = useState<number | ''>('')
  const [rows, setRows] = useState<ExamItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const run = useApiAction()

  useEffect(() => {
    certificateAdminApi.certs(1, 100).then((p) => {
      setCerts(p.list)
      // 默认选中第一个证书并联动加载科目（内容树归属）
      if (p.list.length > 0 && certId === '') {
        setCertId(p.list[0].id)
        subjectAdminApi
          .subjects(p.list[0].id)
          .then(setSubjects)
          .catch(() => setSubjects([]))
      }
    }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const load = useCallback(async (page = pageNum) => {
    setLoading(true)
    setError('')
    try {
      const p = await examAdminApi.page(page, 20)
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

  const createExam = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const fd = new FormData(e.currentTarget)
    const types = [...fd.getAll('questionTypes')].map(Number)
    const subjectRaw = fd.get('subjectId')
    const result = await run(
      () =>
        examAdminApi.create({
          certificateId: Number(fd.get('certificateId')),
          subjectId: subjectRaw ? Number(subjectRaw) : null,
          versionId: null,
          name: String(fd.get('name')),
          durationMinutes: Number(fd.get('durationMinutes')),
          passScore: Number(fd.get('passScore') || 0) || undefined,
          validFrom: String(fd.get('validFrom') || '') || null,
          validUntil: String(fd.get('validUntil') || '') || null,
          rule: { questionCount: Number(fd.get('questionCount')), questionTypes: types },
        }),
      '考试已创建（请继续组卷）',
    )
    if (result) {
      setCreateOpen(false)
      setSubjects([])
      void load()
    }
  }

  return (
    <div className="page">
      <PageHeader
        title="考试管理"
        sub="创建 → 组卷 → 发布（服务端校验状态机）"
        extra={
          <button type="button" className="btn btn-primary btn-md" onClick={() => setCreateOpen(true)}>
            新建考试
          </button>
        }
      />
      <Card>
        {error && <div className="inline-error">{error}</div>}
        <DataTable<ExamItem>
          loading={loading}
          rows={rows}
          rowKey={(r) => r.id}
          columns={[
            { key: 'id', title: 'ID', width: '60px', render: (r) => <span className="num">{r.id}</span> },
            { key: 'name', title: '名称', render: (r) => <Link className="link-btn" to={`/admin/exams/${r.id}`}>{r.name}</Link> },
            { key: 'durationMinutes', title: '时长(分)', render: (r) => <span className="num">{r.durationMinutes}</span> },
            { key: 'totalScore', title: '总分', render: (r) => <span className="num">{r.totalScore}</span> },
            { key: 'passScore', title: '及格分', render: (r) => <span className="num">{r.passScore}</span> },
            { key: 'status', title: '状态', render: (r) => <StatusBadge labels={EXAM_STATUS} value={r.status} /> },
            {
              key: 'actions',
              title: '操作',
              render: (r) => (
                <span className="row-actions">
                  {r.status === 1 && (
                    <button type="button" className="link-btn" onClick={() => void run(() => examAdminApi.assemble(r.id), '组卷成功').then((ok) => { if (ok) void load() })}>
                      组卷
                    </button>
                  )}
                  {r.status === 1 && (
                    <button type="button" className="link-btn" onClick={() => void run(() => examAdminApi.publish(r.id), '已发布').then((ok) => { if (ok) void load() })}>
                      发布
                    </button>
                  )}
                  {r.status === 2 && (
                    <button type="button" className="link-btn" onClick={() => void run(() => examAdminApi.unpublish(r.id), '已下架').then((ok) => { if (ok) void load() })}>
                      下架
                    </button>
                  )}
                  <Link className="link-btn" to={`/admin/exams/${r.id}`}>
                    详情/成绩
                  </Link>
                  <ConfirmButton label="删除" confirmText="确认删除" onConfirm={() => void run(() => examAdminApi.delete(r.id), '已删除').then((ok) => { if (ok) void load() })} />
                </span>
              ),
            },
          ]}
        />
        <Pagination pageNum={pageNum} pageSize={20} total={total} onChange={(p) => setPageNum(p)} />
      </Card>

      <Modal open={createOpen} title="新建考试" onClose={() => { setCreateOpen(false); setSubjects([]); setCertId(certs.length > 0 ? certs[0].id : '') }} wide>
        <AdminForm onSubmit={createExam} submitLabel="创建">
          <Field label="所属证书（内容树：证书 → 版本 → 科目 → 考试）">
            <select
              className="input"
              name="certificateId"
              required
              value={certId}
              onChange={async (e) => {
                const cid = Number(e.target.value)
                setCertId(cid)
                if (cid) {
                  try {
                    setSubjects(await subjectAdminApi.subjects(cid))
                  } catch {
                    setSubjects([])
                  }
                } else {
                  setSubjects([])
                }
              }}
            >
              {certs.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="考试科目（归属明确后，考试中心按目标过滤）">
            <select className="input" name="subjectId" disabled={subjects.length === 0}>
              <option value="">不指定科目（仅证书级）</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="考试名称">
            <input className="input" name="name" required />
          </Field>
          <div className="grid-2">
            <Field label="时长（分钟）">
              <input className="input" name="durationMinutes" type="number" required min={1} max={300} defaultValue={60} />
            </Field>
            <Field label="及格分">
              <input className="input" name="passScore" type="number" defaultValue={60} />
            </Field>
          </div>
          <div className="grid-2">
            <Field label="开考时间（可选）">
              <input className="input" name="validFrom" type="datetime-local" />
            </Field>
            <Field label="结束时间（可选）">
              <input className="input" name="validUntil" type="datetime-local" />
            </Field>
          </div>
          <Field label="组卷规则（组卷时按规则从已发布题库抽题）">
            <div className="grid-2">
              <Field label="题目总数">
                <input className="input" name="questionCount" type="number" required min={1} max={200} defaultValue={10} />
              </Field>
              <Field label="题型">
                <label className="node-pick-item">
                  <input type="checkbox" name="questionTypes" value={1} defaultChecked /> 单选
                </label>
                <label className="node-pick-item">
                  <input type="checkbox" name="questionTypes" value={2} /> 多选
                </label>
                <label className="node-pick-item">
                  <input type="checkbox" name="questionTypes" value={3} /> 判断
                </label>
              </Field>
            </div>
          </Field>
        </AdminForm>
      </Modal>
    </div>
  )
}
