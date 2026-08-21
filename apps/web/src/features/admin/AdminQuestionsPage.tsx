import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Badge, Card } from '../../shared/ui/primitives'
import {
  AdminForm,
  ConfirmButton,
  DataTable,
  Field,
  Modal,
  PageHeader,
  Pagination,
  errText,
  useApiAction,
} from '../../admin/ui'
import { certificateAdminApi, type CertItem } from './certificateAdminApi'
import { questionAdminApi, type QuestionDetail, type QuestionItem, type QuestionOption } from './questionAdminApi'
import { subjectAdminApi, type SubjectTree } from './subjectAdminApi'

const Q_STATUS: Record<number, string> = { 1: '草稿', 2: '待审核', 3: '驳回', 4: '已发布', 5: '已下架', 6: '回收站' }
const Q_TYPE: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断' }

/** 题库管理：CRUD + 审核流（发布唯一通道=审核通过，服务端裁决） */
export default function AdminQuestionsPage() {
  const [certs, setCerts] = useState<CertItem[]>([])
  const [certId, setCertId] = useState<number | undefined>(undefined)
  const [rows, setRows] = useState<QuestionItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<number | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editor, setEditor] = useState<{ mode: 'create' } | { mode: 'edit'; detail: QuestionDetail } | null>(null)
  const [nodes, setNodes] = useState<{ id: number; name: string; nodeType: number }[]>([])
  const [rejectTarget, setRejectTarget] = useState<QuestionItem | null>(null)
  const run = useApiAction()

  useEffect(() => {
    certificateAdminApi
      .certs(1, 100)
      .then((p) => {
        setCerts(p.list)
        if (p.list.length > 0) setCertId(p.list[0].id)
      })
      .catch(() => {})
  }, [])

  const loadNodes = useCallback(async (cert: number) => {
    try {
      const [versions, tree] = await Promise.all([
        subjectAdminApi.versions(cert),
        (async () => {
          const vs = await subjectAdminApi.versions(cert)
          const cur = vs.find((v) => v.status === 2) ?? vs[0]
          return cur ? subjectAdminApi.tree(cur.id) : []
        })(),
      ])
      void versions
      const flat: { id: number; name: string; nodeType: number }[] = []
      const walk = (nodes: SubjectTree['children']) => {
        for (const n of nodes) {
          if (n.nodeType === 2 || n.nodeType === 3) flat.push({ id: n.id, name: n.name, nodeType: n.nodeType })
          walk(n.children ?? [])
        }
      }
      tree.forEach((t) => walk(t.children))
      setNodes(flat)
    } catch {
      setNodes([])
    }
  }, [])

  const load = useCallback(
    async (page = pageNum) => {
      setLoading(true)
      setError('')
      try {
        const p = await questionAdminApi.page({ pageNum: page, pageSize: 20, certificateId: certId, status: status === '' ? undefined : status, keyword: keyword || undefined })
        setRows(p.list)
        setTotal(p.total)
      } catch (e) {
        setError(errText(e))
      } finally {
        setLoading(false)
      }
    },
    [pageNum, certId, keyword, status],
  )

  useEffect(() => {
    void load(pageNum)
  }, [pageNum, load])

  const openEditor = async (mode: 'create' | 'edit', id?: number) => {
    if (certId === undefined) return
    await loadNodes(certId)
    if (mode === 'create') {
      setEditor({ mode: 'create' })
    } else if (id !== undefined) {
      try {
        setEditor({ mode: 'edit', detail: await questionAdminApi.detail(id) })
      } catch (e) {
        setError(errText(e))
      }
    }
  }

  const saveQuestion = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!editor || certId === undefined) return
    const fd = new FormData(e.currentTarget)
    const qType = Number(fd.get('questionType'))
    const options: QuestionOption[] = []
    if (qType !== 3) {
      for (const key of ['A', 'B', 'C', 'D']) {
        const content = String(fd.get(`opt-${key}`) ?? '')
        if (content) options.push({ optionKey: key, content })
      }
    }
    const base = {
      stem: String(fd.get('stem')),
      analysis: String(fd.get('analysis')),
      answer: String(fd.get('answer')),
      difficulty: Number(fd.get('difficulty')),
      source: Number(fd.get('source')),
      options,
    }
    const nodeIds = [...fd.getAll('nodeIds')].map(Number)
    if (editor.mode === 'create') {
      const result = await run(() => questionAdminApi.create({ certificateId: certId, questionType: qType, ...base, nodeIds }), '题目已创建')
      if (result) {
        setEditor(null)
        void load()
      }
    } else {
      const result = await run(
        async () => {
          await questionAdminApi.update(editor.detail.question.id, base)
          await questionAdminApi.updateKnowledgeNodes(editor.detail.question.id, nodeIds)
        },
        '题目已保存',
      )
      if (result) {
        setEditor(null)
        void load()
      }
    }
  }

  const rejectSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!rejectTarget) return
    const fd = new FormData(e.currentTarget)
    const result = await run(() => questionAdminApi.reject(rejectTarget.id, String(fd.get('reason'))), '已驳回')
    if (result) {
      setRejectTarget(null)
      void load()
    }
  }

  const editorDefaults = (key: string): string | undefined => {
    if (!editor || editor.mode !== 'edit') return undefined
    const d = editor.detail
    if (key.startsWith('opt-')) {
      return d.options.find((o) => o.optionKey === key.slice(4))?.content
    }
    const map: Record<string, string> = {
      questionType: String(d.question.questionType),
      stem: d.question.stem,
      analysis: d.analysis,
      answer: d.answer,
      difficulty: String(d.question.difficulty),
      source: String(d.question.source),
    }
    return map[key]
  }
  const editorNodeIds = editor?.mode === 'edit' ? editor.detail.nodeIds : []

  return (
    <div className="page">
      <PageHeader
        title="题库管理"
        sub="创建 → 提审 → 审核通过（唯一发布通道）"
        extra={
          <span className="row-actions">
            <Link to="/admin/questions/import" className="btn btn-secondary btn-md">
              Excel 导入
            </Link>
            <button type="button" className="btn btn-primary btn-md" onClick={() => void openEditor('create')}>
              新建题目
            </button>
          </span>
        }
      />
      <Card>
        <div className="admin-toolbar">
          <select className="input" aria-label="选择证书" value={certId ?? ''} onChange={(e) => setCertId(e.target.value ? Number(e.target.value) : undefined)}>
            <option value="">全部证书</option>
            {certs.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          <select
            className="input"
            aria-label="题目状态"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value === '' ? '' : Number(e.target.value))
              setPageNum(1)
            }}
          >
            <option value="">全部状态</option>
            <option value={1}>草稿</option>
            <option value={2}>待审核</option>
            <option value={3}>已驳回</option>
            <option value={4}>已发布</option>
            <option value={5}>已下架</option>
            <option value={6}>回收站</option>
          </select>
          <input className="input admin-search" placeholder="题干关键词" value={keyword} onChange={(e) => setKeyword(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && (setPageNum(1), void load(1))} />
          <button type="button" className="btn btn-secondary btn-md" onClick={() => { setPageNum(1); void load(1) }}>
            搜索
          </button>
        </div>
        {error && <div className="inline-error">{error}</div>}
        <DataTable<QuestionItem>
          loading={loading}
          rows={rows}
          rowKey={(r) => r.id}
          columns={[
            { key: 'id', title: 'ID', width: '60px', render: (r) => <span className="num">{r.id}</span> },
            { key: 'questionType', title: '题型', render: (r) => <Badge>{Q_TYPE[r.questionType] ?? r.questionType}</Badge> },
            { key: 'stem', title: '题干', render: (r) => <span className="ellipsis-cell">{r.stem}</span> },
            { key: 'status', title: '状态', render: (r) => <Badge tone={r.status === 4 ? 'success' : r.status === 3 ? 'error' : 'neutral'}>{Q_STATUS[r.status] ?? r.status}</Badge> },
            { key: 'contentVersion', title: '版本', render: (r) => <span className="num">v{r.contentVersion}</span> },
            {
              key: 'actions',
              title: '操作',
              width: '300px',
              render: (r) => (
                <span className="row-actions">
                  <button type="button" className="link-btn" onClick={() => void openEditor('edit', r.id)}>
                    编辑
                  </button>
                  {r.status === 1 && (
                    <button type="button" className="link-btn" onClick={() => void run(() => questionAdminApi.submitReview(r.id), '已提审').then((ok) => { if (ok) void load() })}>
                      提审
                    </button>
                  )}
                  {r.status === 2 && (
                    <>
                      <button type="button" className="link-btn" onClick={() => void run(() => questionAdminApi.withdrawReview(r.id), '已撤回').then((ok) => { if (ok) void load() })}>
                        撤回
                      </button>
                      <button type="button" className="link-btn" onClick={() => void run(() => questionAdminApi.approve(r.id), '已通过并发布').then((ok) => { if (ok) void load() })}>
                        通过
                      </button>
                      <button type="button" className="link-btn" onClick={() => setRejectTarget(r)}>
                        驳回
                      </button>
                    </>
                  )}
                  {r.status === 4 && (
                    <button type="button" className="link-btn" onClick={() => void run(() => questionAdminApi.unpublish(r.id), '已下架').then((ok) => { if (ok) void load() })}>
                      下架
                    </button>
                  )}
                  {r.status === 6 && (
                    <button type="button" className="link-btn" onClick={() => void run(() => questionAdminApi.restore(r.id), '已恢复为草稿').then((ok) => { if (ok) void load() })}>
                      恢复
                    </button>
                  )}
                  <ConfirmButton
                    label="删除"
                    confirmText="确认删除"
                    onConfirm={() => void run(() => questionAdminApi.delete(r.id), r.status === 4 ? '已移入回收站' : '已删除').then((ok) => { if (ok) void load() })}
                  />
                </span>
              ),
            },
          ]}
        />
        <Pagination pageNum={pageNum} pageSize={20} total={total} onChange={(p) => setPageNum(p)} />
      </Card>

      <Modal open={editor !== null} title={editor?.mode === 'create' ? '新建题目' : '编辑题目'} onClose={() => setEditor(null)} wide>
        <AdminForm onSubmit={saveQuestion} submitLabel="保存">
          <Field label="题型">
            <select className="input" name="questionType" defaultValue={editorDefaults('questionType') ?? '1'} disabled={editor?.mode === 'edit'}>
              <option value={1}>单选题</option>
              <option value={2}>多选题</option>
              <option value={3}>判断题</option>
            </select>
          </Field>
          <Field label="题干">
            <textarea className="input" name="stem" required defaultValue={editorDefaults('stem')} rows={3} />
          </Field>
          <Field label="答案" hint="单选/判断：A 或 T/F；多选：A|C（竖线分隔）">
            <input className="input" name="answer" required defaultValue={editorDefaults('answer')} />
          </Field>
          <Field label="选项（判断题留空）">
            {['A', 'B', 'C', 'D'].map((k) => (
              <input key={k} className="input admin-opt" name={`opt-${k}`} placeholder={`选项 ${k}`} defaultValue={editorDefaults(`opt-${k}`)} />
            ))}
          </Field>
          <Field label="解析">
            <textarea className="input" name="analysis" required defaultValue={editorDefaults('analysis')} rows={2} />
          </Field>
          <div className="grid-2">
            <Field label="难度">
              <select className="input" name="difficulty" defaultValue={editorDefaults('difficulty') ?? '2'}>
                <option value={1}>1 简单</option>
                <option value={2}>2 中等</option>
                <option value={3}>3 困难</option>
              </select>
            </Field>
            <Field label="来源">
              <select className="input" name="source" defaultValue={editorDefaults('source') ?? '2'}>
                <option value={1}>官方</option>
                <option value={2}>自建</option>
                <option value={3}>第三方</option>
              </select>
            </Field>
          </div>
          <Field label="关联知识点（至少一个知识点/子知识点）">
            <div className="node-pick">
              {nodes.map((n) => (
                <label key={n.id} className="node-pick-item">
                  <input type="checkbox" name="nodeIds" value={n.id} defaultChecked={editorNodeIds.includes(n.id)} />
                  {n.name}
                </label>
              ))}
              {nodes.length === 0 && <span className="list-row-sub">当前证书无知识点，请先在知识体系创建</span>}
            </div>
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={rejectTarget !== null} title={`驳回 · #${rejectTarget?.id ?? ''}`} onClose={() => setRejectTarget(null)}>
        <AdminForm onSubmit={rejectSubmit} submitLabel="确认驳回">
          <Field label="驳回原因">
            <input className="input" name="reason" required placeholder="请填写驳回原因" />
          </Field>
        </AdminForm>
      </Modal>
    </div>
  )
}
