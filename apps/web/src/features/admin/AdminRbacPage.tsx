import { useCallback, useEffect, useState, type FormEvent } from 'react'
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
import { sysAdminApi, type PermissionNode, type RoleItem } from './sysApi'

const ROLE_TYPE: Record<number, string> = { 1: '内置', 2: '自定义' }

/** 角色与权限管理（sys:role:* / sys:permission:*；权限点展示 + 角色权限分配） */
export default function AdminRbacPage() {
  const [tab, setTab] = useState<'roles' | 'perms'>('roles')
  const [roles, setRoles] = useState<RoleItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [assignOpen, setAssignOpen] = useState<RoleItem | null>(null)
  const [permTree, setPermTree] = useState<PermissionNode[]>([])
  const [checked, setChecked] = useState<Set<number>>(new Set())
  const run = useApiAction()

  const loadRoles = useCallback(async (page = pageNum) => {
    setLoading(true)
    setError('')
    try {
      const p = await sysAdminApi.roles(page, 20)
      setRoles(p.list)
      setTotal(p.total)
    } catch (e) {
      setError(errText(e))
    } finally {
      setLoading(false)
    }
  }, [pageNum])

  useEffect(() => {
    void loadRoles(pageNum)
  }, [pageNum, loadRoles])

  useEffect(() => {
    if (tab === 'perms') {
      sysAdminApi.permissionTree().then(setPermTree).catch(() => setPermTree([]))
    }
  }, [tab])

  const openAssign = async (role: RoleItem) => {
    try {
      const [tree, detail] = await Promise.all([sysAdminApi.permissionTree(), sysAdminApi.roleDetail(role.id)])
      setPermTree(tree)
      setChecked(new Set(detail.permissionIds))
      setAssignOpen(role)
    } catch (e) {
      setError(errText(e))
    }
  }

  const createRole = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        sysAdminApi.createRole({
          roleCode: String(fd.get('roleCode')),
          roleName: String(fd.get('roleName')),
          roleType: 2,
          status: 1,
          remark: '',
          sort: 0,
        }),
      '角色已创建',
    )
    if (result) {
      setCreateOpen(false)
      void loadRoles()
    }
  }

  const toggle = (id: number) => {
    setChecked((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const saveAssign = async () => {
    if (!assignOpen) return
    const result = await run(() => sysAdminApi.assignPermissions(assignOpen.id, [...checked]), '权限已保存')
    if (result) {
      setAssignOpen(null)
    }
  }

  const permFlat = (nodes: PermissionNode[]): PermissionNode[] =>
    nodes.flatMap((n) => [n, ...permFlat(n.children ?? [])])

  return (
    <div className="page">
      <PageHeader
        title="角色与权限"
        sub="角色权限分配（权限点实时解析，服务端最终校验）"
        extra={
          <button type="button" className="btn btn-primary btn-md" onClick={() => setCreateOpen(true)}>
            新建角色
          </button>
        }
      />
      <div className="seg-row" role="tablist" aria-label="RBAC 页签">
        <button type="button" role="tab" aria-selected={tab === 'roles'} className={`seg-item ${tab === 'roles' ? 'seg-active' : ''}`} onClick={() => setTab('roles')}>
          角色
        </button>
        <button type="button" role="tab" aria-selected={tab === 'perms'} className={`seg-item ${tab === 'perms' ? 'seg-active' : ''}`} onClick={() => setTab('perms')}>
          权限点
        </button>
      </div>

      {error && <div className="inline-error">{error}</div>}

      {tab === 'roles' ? (
        <Card>
          <DataTable<RoleItem>
            loading={loading}
            rows={roles}
            rowKey={(r) => r.id}
            columns={[
              { key: 'id', title: 'ID', width: '60px', render: (r) => <span className="num">{r.id}</span> },
              { key: 'roleCode', title: '编码' },
              { key: 'roleName', title: '名称' },
              { key: 'roleType', title: '类型', render: (r) => <Badge>{ROLE_TYPE[r.roleType] ?? r.roleType}</Badge> },
              {
                key: 'actions',
                title: '操作',
                render: (r) => (
                  <span className="row-actions">
                    <button type="button" className="link-btn" onClick={() => void openAssign(r)}>
                      分配权限
                    </button>
                    <ConfirmButton
                      label="删除"
                      confirmText="确认删除"
                      onConfirm={() => {
                        void run(() => sysAdminApi.deleteRole(r.id), '角色已删除').then((ok) => {
                          if (ok) void loadRoles()
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
      ) : (
        <Card title="全部权限点">
          <div className="perm-tree">
            {permFlat(permTree).map((p) => (
              <div key={p.id} className={`perm-row ${p.children?.length ? 'perm-parent' : ''}`} style={{ marginLeft: 0 }}>
                <code>{p.permissionCode}</code>
                <span>{p.permissionName}</span>
              </div>
            ))}
          </div>
        </Card>
      )}

      <Modal open={createOpen} title="新建角色" onClose={() => setCreateOpen(false)}>
        <AdminForm onSubmit={createRole} submitLabel="创建">
          <Field label="角色编码">
            <input className="input" name="roleCode" required placeholder="如 teacher_math" />
          </Field>
          <Field label="角色名称">
            <input className="input" name="roleName" required placeholder="如 数学教师" />
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={assignOpen !== null} title={`分配权限 · ${assignOpen?.roleName ?? ''}`} onClose={() => setAssignOpen(null)} wide>
        <div className="perm-pick" role="group" aria-label="权限点选择">
          {permFlat(permTree).map((p) => (
            <label key={p.id} className={`perm-pick-item ${p.children?.length ? 'perm-pick-parent' : ''}`}>
              <input type="checkbox" checked={checked.has(p.id)} onChange={() => toggle(p.id)} />
              <code>{p.permissionCode}</code>
              <span>{p.permissionName}</span>
            </label>
          ))}
        </div>
        <div className="modal-actions">
          <button type="button" className="btn btn-primary btn-md" onClick={() => void saveAssign()}>
            保存
          </button>
        </div>
      </Modal>
    </div>
  )
}
