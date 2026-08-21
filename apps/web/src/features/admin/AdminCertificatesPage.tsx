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
import { useToast } from '../../shared/ui/Toast'
import { certificateAdminApi, type CategoryNode, type CertItem } from './certificateAdminApi'

/** 证书与分类管理（分类树 + 证书表格 + 证书体系复制） */
export default function AdminCertificatesPage() {
  const [tree, setTree] = useState<CategoryNode[]>([])
  const [selectedCat, setSelectedCat] = useState<number | undefined>(undefined)
  const [certs, setCerts] = useState<CertItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [catModal, setCatModal] = useState<{ parentId: number } | null>(null)
  const [certModal, setCertModal] = useState(false)
  const [copyTarget, setCopyTarget] = useState<CertItem | null>(null)
  const run = useApiAction()
  const { toast } = useToast()

  const loadTree = useCallback(async () => {
    try {
      setTree(await certificateAdminApi.categoryTree())
    } catch (e) {
      setError(errText(e))
    }
  }, [])

  const loadCerts = useCallback(async (page = pageNum) => {
    setLoading(true)
    try {
      const p = await certificateAdminApi.certs(page, 20, selectedCat)
      setCerts(p.list)
      setTotal(p.total)
    } catch (e) {
      setError(errText(e))
    } finally {
      setLoading(false)
    }
  }, [pageNum, selectedCat])

  useEffect(() => {
    void loadTree()
  }, [loadTree])

  useEffect(() => {
    void loadCerts(pageNum)
  }, [pageNum, loadCerts])

  const createCat = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!catModal) return
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        certificateAdminApi.createCategory({
          parentId: catModal.parentId,
          name: String(fd.get('name')),
          code: String(fd.get('code')),
          sort: Number(fd.get('sort') || 0),
          enabled: 1,
          description: String(fd.get('description') || ''),
        }),
      '分类已创建',
    )
    if (result) {
      setCatModal(null)
      await loadTree()
    }
  }

  const createCert = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (selectedCat === undefined) return
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        certificateAdminApi.createCert({
          categoryId: selectedCat,
          name: String(fd.get('name')),
          code: String(fd.get('code')),
          shortName: String(fd.get('shortName') || ''),
          description: String(fd.get('description') || ''),
          sort: 0,
          enabled: 1,
        }),
      '证书已创建',
    )
    if (result) {
      setCertModal(false)
      void loadCerts()
    }
  }

  const copyCert = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!copyTarget) return
    const fd = new FormData(e.currentTarget)
    try {
      const summary = await certificateAdminApi.copyCert(copyTarget.id, {
        name: String(fd.get('name')),
        code: String(fd.get('code')),
        copyQuestions: fd.get('copyQuestions') === 'on',
      })
      toast(`证书体系已复制：${summary.subjectCount} 科目 · ${summary.nodeCount} 节点 · ${summary.courseCount} 课程 · ${summary.questionCount} 题`, 'success')
      setCopyTarget(null)
      void loadCerts()
    } catch (err) {
      toast(errText(err), 'error')
    }
  }

  const flatCats = (nodes: CategoryNode[]): CategoryNode[] => nodes.flatMap((n) => [n, ...flatCats(n.children ?? [])])

  return (
    <div className="page">
      <PageHeader
        title="证书与分类"
        sub="无限级分类树 + 证书（知识体系/题库/考试/课程的地基）"
        extra={
          <button type="button" className="btn btn-primary btn-md" onClick={() => setCatModal({ parentId: 0 })}>
            新建根分类
          </button>
        }
      />
      {error && <div className="inline-error">{error}</div>}
      <div className="admin-split">
        <Card title="分类树">
          <ul className="tree-list">
            {tree.map((n) => (
              <TreeRow
                key={n.id}
                node={n}
                depth={0}
                selected={selectedCat === n.id}
                onSelect={setSelectedCat}
                onAddChild={(id) => setCatModal({ parentId: id })}
                onDelete={(id) => {
                  void run(() => certificateAdminApi.deleteCategory(id), '分类已删除').then((ok) => {
                    if (ok) void loadTree()
                  })
                }}
              />
            ))}
            {tree.length === 0 && <li className="list-row-sub">暂无分类，点击右上角新建</li>}
          </ul>
        </Card>
        <Card
          title={selectedCat === undefined ? '证书（全部分类）' : '证书（当前分类）'}
          extra={
            <button type="button" className="btn btn-secondary btn-sm" disabled={selectedCat === undefined} onClick={() => setCertModal(true)}>
              新建证书
            </button>
          }
        >
          <DataTable<CertItem>
            loading={loading}
            rows={certs}
            rowKey={(r) => r.id}
            emptyText="该分类下暂无证书"
            columns={[
              { key: 'id', title: 'ID', width: '60px', render: (r) => <span className="num">{r.id}</span> },
              { key: 'name', title: '名称' },
              { key: 'code', title: '编码' },
              { key: 'categoryName', title: '分类' },
              {
                key: 'enabled',
                title: '状态',
                render: (r) => <Badge tone={r.enabled === 1 ? 'success' : 'error'}>{r.enabled === 1 ? '启用' : '禁用'}</Badge>,
              },
              {
                key: 'actions',
                title: '操作',
                render: (r) => (
                  <span className="row-actions">
                    <button type="button" className="link-btn" onClick={() => setCopyTarget(r)}>
                      复制体系
                    </button>
                    <Link className="link-btn" to={`/admin/subjects?cert=${r.id}`}>
                      配置知识体系
                    </Link>
                    <ConfirmButton
                      label="删除"
                      confirmText="确认删除"
                      onConfirm={() => {
                        void run(() => certificateAdminApi.deleteCert(r.id), '证书已删除').then((ok) => {
                          if (ok) void loadCerts()
                        })
                      }}
                    />
                  </span>
                ),
              },
            ]}
          />
          <Pagination pageNum={pageNum} pageSize={20} total={total} onChange={(p) => setPageNum(p)} />
        </Card>
      </div>

      <Modal open={catModal !== null} title={catModal?.parentId === 0 ? '新建根分类' : '新建子分类'} onClose={() => setCatModal(null)}>
        <AdminForm onSubmit={createCat} submitLabel="创建">
          <Field label="分类名称">
            <input className="input" name="name" required />
          </Field>
          <Field label="分类编码">
            <input className="input" name="code" required placeholder="唯一编码，如 nurse" />
          </Field>
          <Field label="排序">
            <input className="input" name="sort" type="number" defaultValue={0} />
          </Field>
          <Field label="描述">
            <input className="input" name="description" />
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={certModal} title="新建证书" onClose={() => setCertModal(false)}>
        <AdminForm onSubmit={createCert} submitLabel="创建">
          <Field label="证书名称">
            <input className="input" name="name" required placeholder="如 护士执业资格考试" />
          </Field>
          <Field label="证书编码">
            <input className="input" name="code" required placeholder="唯一编码，创建后不可变" />
          </Field>
          <Field label="简称">
            <input className="input" name="shortName" />
          </Field>
          <Field label="描述">
            <input className="input" name="description" />
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={copyTarget !== null} title={`复制证书体系 · ${copyTarget?.name ?? ''}`} onClose={() => setCopyTarget(null)}>
        <AdminForm onSubmit={copyCert} submitLabel="开始复制">
          <Field label="目标证书名称">
            <input className="input" name="name" required defaultValue={copyTarget ? `${copyTarget.name}（副本）` : ''} />
          </Field>
          <Field label="目标证书编码">
            <input className="input" name="code" required placeholder="唯一编码" />
          </Field>
          <Field label="复制题库" hint="复制源证书已发布题目（目标为草稿，发布仍需审核通过）">
            <label className="checkbox-label">
              <input type="checkbox" name="copyQuestions" defaultChecked />
              同时复制已发布题库
            </label>
          </Field>
          <p className="list-row-sub">
            将复制：科目、章节、知识点、课程结构（章/节/知识点关联；视频不复制）。目标证书知识版本自动设为当前。
          </p>
        </AdminForm>
      </Modal>
    </div>
  )
}

function TreeRow({
  node,
  depth,
  selected,
  onSelect,
  onAddChild,
  onDelete,
}: {
  node: CategoryNode
  depth: number
  selected: boolean
  onSelect: (id: number) => void
  onAddChild: (id: number) => void
  onDelete: (id: number) => void
}) {
  return (
    <>
      <li className={`tree-row ${selected ? 'tree-row-active' : ''}`} style={{ paddingLeft: depth * 18 }}>
        <button type="button" className="tree-row-main" onClick={() => onSelect(node.id)}>
          <span>{node.name}</span>
          <span className="list-row-sub">{node.code}</span>
        </button>
        <span className="row-actions">
          <button type="button" className="link-btn" onClick={() => onAddChild(node.id)}>
            加子分类
          </button>
          <ConfirmButton label="删除" confirmText="确认" onConfirm={() => onDelete(node.id)} />
        </span>
      </li>
      {(node.children ?? []).map((c) => (
        <TreeRow key={c.id} node={c} depth={depth + 1} selected={selected} onSelect={onSelect} onAddChild={onAddChild} onDelete={onDelete} />
      ))}
    </>
  )
}
