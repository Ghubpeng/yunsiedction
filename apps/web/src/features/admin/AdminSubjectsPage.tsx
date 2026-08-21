import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Badge, Card, Skeleton } from '../../shared/ui/primitives'
import {
  AdminForm,
  ConfirmButton,
  Field,
  Modal,
  PageHeader,
  errText,
  useApiAction,
} from '../../admin/ui'
import { certificateAdminApi, type CertItem } from './certificateAdminApi'
import { subjectAdminApi, type NodeItem, type SubjectItem, type SubjectTree, type VersionItem } from './subjectAdminApi'

const NODE_TYPE: Record<number, string> = { 1: '章节', 2: '知识点', 3: '子知识点' }

/** 知识体系管理：证书 → 版本/科目 → 树（章节/知识点/子知识点） */
export default function AdminSubjectsPage() {
  const [certs, setCerts] = useState<CertItem[]>([])
  const [certId, setCertId] = useState<number | undefined>(undefined)
  const [versions, setVersions] = useState<VersionItem[]>([])
  const [versionId, setVersionId] = useState<number | undefined>(undefined)
  const [subjects, setSubjects] = useState<SubjectItem[]>([])
  const [trees, setTrees] = useState<SubjectTree[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [verModal, setVerModal] = useState(false)
  const [subjModal, setSubjModal] = useState(false)
  const [nodeModal, setNodeModal] = useState<{ parentId: number; subjectId: number; nodeType: number } | null>(null)
  const [subjEdit, setSubjEdit] = useState<SubjectItem | null>(null)
  const [nodeEdit, setNodeEdit] = useState<NodeItem | null>(null)
  const run = useApiAction()

  useEffect(() => {
    // Stage 2.3B：支持 ?cert={id} 深链（证书页「配置知识体系」跳转直达）
    const params = new URLSearchParams(window.location.search)
    const preset = params.get('cert')
    certificateAdminApi
      .certs(1, 100)
      .then((p) => {
        setCerts(p.list)
        if (preset && p.list.some((c) => c.id === Number(preset))) {
          setCertId(Number(preset))
        } else if (p.list.length > 0) {
          setCertId(p.list[0].id)
        }
      })
      .catch(() => setError('证书列表加载失败'))
  }, [])

  const loadVersions = useCallback(async (cert: number) => {
    try {
      const list = await subjectAdminApi.versions(cert)
      setVersions(list)
      const current = list.find((v) => v.status === 2) ?? list[0]
      setVersionId(current?.id)
    } catch (e) {
      setError(errText(e))
    }
  }, [])

  useEffect(() => {
    if (certId !== undefined) {
      void loadVersions(certId)
    }
  }, [certId, loadVersions])

  const loadTree = useCallback(async (ver: number) => {
    setLoading(true)
    try {
      const [subs, tree] = await Promise.all([subjectAdminApi.subjects(certId!), subjectAdminApi.tree(ver)])
      setSubjects(subs)
      setTrees(tree)
    } catch (e) {
      setError(errText(e))
    } finally {
      setLoading(false)
    }
  }, [certId])

  useEffect(() => {
    if (versionId !== undefined) {
      void loadTree(versionId)
    }
  }, [versionId, loadTree])

  const createVersion = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (certId === undefined) return
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        subjectAdminApi.createVersion({
          certificateId: certId,
          versionNo: String(fd.get('versionNo')),
          name: String(fd.get('name')),
          remark: '',
        }),
      '版本已创建',
    )
    if (result) {
      setVerModal(false)
      await loadVersions(certId)
    }
  }

  const createSubject = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (certId === undefined) return
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        subjectAdminApi.createSubject({
          certificateId: certId,
          name: String(fd.get('name')),
          code: String(fd.get('code')),
          source: 1,
          enabled: 1,
          description: '',
        }),
      '科目已创建',
    )
    if (result) {
      setSubjModal(false)
      if (versionId !== undefined) await loadTree(versionId)
    }
  }

  const createNode = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!nodeModal || versionId === undefined) return
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        subjectAdminApi.createNode({
          versionId,
          subjectId: nodeModal.subjectId,
          parentId: nodeModal.parentId,
          nodeType: nodeModal.nodeType,
          name: String(fd.get('name')),
          description: String(fd.get('description') || ''),
          code: String(fd.get('code')),
          sort: Number(fd.get('sort') || 0),
          enabled: 1,
          source: 1,
          remark: '',
        }),
      '节点已创建',
    )
    if (result) {
      setNodeModal(null)
      await loadTree(versionId)
    }
  }

  const saveSubject = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!subjEdit || versionId === undefined) return
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        subjectAdminApi.updateSubject(subjEdit.id, {
          name: String(fd.get('name')),
          sort: Number(fd.get('sort') || 0),
          enabled: Number(fd.get('enabled')),
          description: String(fd.get('description') || ''),
        }),
      '科目已保存',
    )
    if (result) {
      setSubjEdit(null)
      await loadTree(versionId)
    }
  }

  const saveNode = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!nodeEdit || versionId === undefined) return
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        subjectAdminApi.updateNode(nodeEdit.id, {
          name: String(fd.get('name')),
          description: String(fd.get('description') || ''),
          sort: Number(fd.get('sort') || 0),
          enabled: Number(fd.get('enabled')),
          source: nodeEdit.source,
          remark: '',
        }),
      '节点已保存',
    )
    if (result) {
      setNodeEdit(null)
      await loadTree(versionId)
    }
  }

  const toggleSubjectEnabled = (s: SubjectItem) => {
    void run(() => subjectAdminApi.updateSubject(s.id, { name: s.name, sort: s.sort, enabled: s.enabled === 1 ? 0 : 1, description: s.description ?? '' }), s.enabled === 1 ? '科目已停用' : '科目已启用').then((ok) => {
      if (ok && versionId !== undefined) void loadTree(versionId)
    })
  }

  const toggleNodeEnabled = (n: NodeItem) => {
    void run(() => subjectAdminApi.updateNode(n.id, { name: n.name, description: n.description ?? '', sort: n.sort, enabled: n.enabled === 1 ? 0 : 1, source: n.source, remark: '' }), n.enabled === 1 ? '节点已停用' : '节点已启用').then((ok) => {
      if (ok && versionId !== undefined) void loadTree(versionId)
    })
  }

  const NodeTree = ({ nodes, subjectId, depth }: { nodes: NodeItem[]; subjectId: number; depth: number }) => (
    <ul className="tree-list">
      {nodes.map((n) => (
        <li key={n.id} className="tree-row" style={{ paddingLeft: depth * 18 }}>
          <span className="tree-row-main">
            <span>
              {n.name} <Badge tone={n.enabled === 1 ? 'success' : 'neutral'}>{NODE_TYPE[n.nodeType] ?? n.nodeType}</Badge>
            </span>
            <span className="list-row-sub">
              {n.code}
              {n.description ? ` · ${n.description}` : ''}
            </span>
          </span>
          <span className="row-actions">
            <button type="button" className="link-btn" onClick={() => setNodeEdit(n)}>
              编辑
            </button>
            <button type="button" className="link-btn" onClick={() => toggleNodeEnabled(n)}>
              {n.enabled === 1 ? '停用' : '启用'}
            </button>
            {n.nodeType === 1 && (
              <button type="button" className="link-btn" onClick={() => setNodeModal({ parentId: n.id, subjectId, nodeType: 2 })}>
                加知识点
              </button>
            )}
            {n.nodeType === 2 && (
              <button type="button" className="link-btn" onClick={() => setNodeModal({ parentId: n.id, subjectId, nodeType: 3 })}>
                加子知识点
              </button>
            )}
            <ConfirmButton
              label="删除"
              confirmText="确认"
              onConfirm={() => {
                void run(() => subjectAdminApi.deleteNode(n.id), '节点已删除').then((ok) => {
                  if (ok) void loadTree(versionId!)
                })
              }}
            />
          </span>
          {n.children?.length > 0 && <NodeTree nodes={n.children} subjectId={subjectId} depth={depth + 1} />}
        </li>
      ))}
    </ul>
  )

  return (
    <div className="page">
      <PageHeader
        title="知识体系"
        sub="证书 → 版本 → 考试科目 → 章节 → 知识点 → 子知识点"
        extra={
          <span className="row-actions">
            <button type="button" className="btn btn-secondary btn-md" onClick={() => setSubjModal(true)}>
              新建科目
            </button>
            <button type="button" className="btn btn-primary btn-md" onClick={() => setVerModal(true)}>
              新建版本
            </button>
          </span>
        }
      />
      <div className="admin-toolbar">
        <select className="input" aria-label="选择证书" value={certId} onChange={(e) => setCertId(Number(e.target.value))}>
          {certs.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <select className="input" aria-label="选择版本" value={versionId} onChange={(e) => setVersionId(Number(e.target.value))}>
          {versions.map((v) => (
            <option key={v.id} value={v.id}>
              {v.versionNo} {v.name} {v.status === 2 ? '（当前）' : ''}
            </option>
          ))}
        </select>
        {versionId !== undefined && versions.find((v) => v.id === versionId)?.status !== 2 && (
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => void run(() => subjectAdminApi.setCurrentVersion(versionId), '已设为当前版本').then((ok) => { if (ok) void loadVersions(certId!) })}>
            设为当前版本
          </button>
        )}
      </div>
      {!loading && versions.length === 0 && certId !== undefined && (
        <div className="inline-hint">证书尚无知识版本：请先「新建版本」——版本是知识体系的容器（证书创建后不自动生成科目，由管理员逐步配置 版本 → 科目 → 章节 → 知识点）。</div>
      )}
      {error && <div className="inline-error">{error}</div>}

      {loading ? (
        <Card>
          <Skeleton lines={5} />
        </Card>
      ) : (
        <div className="admin-split">
          <Card title="科目">
            <ul className="tree-list">
              {subjects.map((s) => (
                <li key={s.id} className="tree-row">
                  <span className="tree-row-main">
                    <span>
                      {s.name} <Badge tone={s.enabled === 1 ? 'success' : 'neutral'}>{s.enabled === 1 ? '启用' : '停用'}</Badge>
                    </span>
                    <span className="list-row-sub">
                      {s.code}
                      {s.description ? ` · ${s.description}` : ''}
                    </span>
                  </span>
                  <span className="row-actions">
                    <button type="button" className="link-btn" onClick={() => setNodeModal({ parentId: 0, subjectId: s.id, nodeType: 1 })}>
                      加章节
                    </button>
                    <button type="button" className="link-btn" onClick={() => setSubjEdit(s)}>
                      编辑
                    </button>
                    <button type="button" className="link-btn" onClick={() => toggleSubjectEnabled(s)}>
                      {s.enabled === 1 ? '停用' : '启用'}
                    </button>
                    <ConfirmButton label="删除" confirmText="确认" onConfirm={() => void run(() => subjectAdminApi.deleteSubject(s.id), '科目已删除').then((ok) => { if (ok) void loadTree(versionId!) })} />
                  </span>
                </li>
              ))}
              {subjects.length === 0 && <li className="list-row-sub">暂无科目：证书创建后不自动生成科目，请点击右上角「新建科目」</li>}
            </ul>
          </Card>
          <Card title="知识树">
            {trees.map((t) => (
              <div key={t.subjectId} className="tree-block">
                <div className="tree-block-title">{t.subjectName}</div>
                <NodeTree nodes={t.children} subjectId={t.subjectId} depth={0} />
              </div>
            ))}
            {trees.length === 0 && <div className="list-row-sub">暂无节点：先在左侧科目上「加章节」，再逐层添加知识点/子知识点</div>}
          </Card>
        </div>
      )}

      <Modal open={verModal} title="新建版本" onClose={() => setVerModal(false)}>
        <AdminForm onSubmit={createVersion} submitLabel="创建">
          <Field label="版本号">
            <input className="input" name="versionNo" required placeholder="如 2026" />
          </Field>
          <Field label="版本名称">
            <input className="input" name="name" required placeholder="如 2026版" />
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={subjModal} title="新建科目" onClose={() => setSubjModal(false)}>
        <AdminForm onSubmit={createSubject} submitLabel="创建">
          <Field label="科目名称">
            <input className="input" name="name" required />
          </Field>
          <Field label="科目编码">
            <input className="input" name="code" required />
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={nodeModal !== null} title={`新建${NODE_TYPE[nodeModal?.nodeType ?? 1] ?? '节点'}`} onClose={() => setNodeModal(null)}>
        <AdminForm onSubmit={createNode} submitLabel="创建">
          <Field label="名称">
            <input className="input" name="name" required />
          </Field>
          <Field label="描述">
            <input className="input" name="description" placeholder="可选，最长500字符" />
          </Field>
          <Field label="编码">
            <input className="input" name="code" required placeholder="版本内唯一" />
          </Field>
          <Field label="排序">
            <input className="input" name="sort" type="number" defaultValue={0} />
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={subjEdit !== null} title={`编辑科目 · #${subjEdit?.id ?? ''}`} onClose={() => setSubjEdit(null)}>
        <AdminForm onSubmit={saveSubject} submitLabel="保存">
          <Field label="科目名称">
            <input className="input" name="name" required defaultValue={subjEdit?.name} />
          </Field>
          <Field label="描述">
            <input className="input" name="description" defaultValue={subjEdit?.description ?? ''} />
          </Field>
          <Field label="排序">
            <input className="input" name="sort" type="number" defaultValue={subjEdit?.sort ?? 0} />
          </Field>
          <Field label="状态">
            <select className="input" name="enabled" defaultValue={subjEdit?.enabled ?? 1}>
              <option value={1}>启用</option>
              <option value={0}>停用</option>
            </select>
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={nodeEdit !== null} title={`编辑${NODE_TYPE[nodeEdit?.nodeType ?? 1] ?? '节点'} · #${nodeEdit?.id ?? ''}`} onClose={() => setNodeEdit(null)}>
        <AdminForm onSubmit={saveNode} submitLabel="保存">
          <Field label="名称">
            <input className="input" name="name" required defaultValue={nodeEdit?.name} />
          </Field>
          <Field label="描述">
            <input className="input" name="description" defaultValue={nodeEdit?.description ?? ''} />
          </Field>
          <Field label="排序">
            <input className="input" name="sort" type="number" defaultValue={nodeEdit?.sort ?? 0} />
          </Field>
          <Field label="状态">
            <select className="input" name="enabled" defaultValue={nodeEdit?.enabled ?? 1}>
              <option value={1}>启用</option>
              <option value={0}>停用</option>
            </select>
          </Field>
        </AdminForm>
      </Modal>
    </div>
  )
}
