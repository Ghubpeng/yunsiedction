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
  StatusBadge,
  errText,
  useApiAction,
} from '../../admin/ui'
import { sysAdminApi, type AdminUser } from './sysApi'

const USER_TYPE: Record<number, string> = { 1: '学员', 2: '教师', 3: '管理员' }
const USER_STATUS: Record<number, string> = { 0: '禁用', 1: '正常', 2: '锁定' }

/** 用户管理（sys:user:* 权限；服务端为最终权威） */
export default function AdminUsersPage() {
  const [rows, setRows] = useState<AdminUser[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [resetOpen, setResetOpen] = useState<AdminUser | null>(null)
  const [error, setError] = useState('')
  const run = useApiAction()

  const load = useCallback(async (page = pageNum) => {
    setLoading(true)
    setError('')
    try {
      const pageData = await sysAdminApi.users(page, 20, keyword || undefined)
      setRows(pageData.list)
      setTotal(pageData.total)
    } catch (e) {
      setError(errText(e))
    } finally {
      setLoading(false)
    }
  }, [keyword, pageNum])

  useEffect(() => {
    void load(pageNum)
  }, [pageNum, load])

  const create = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const fd = new FormData(e.currentTarget)
    const result = await run(
      () =>
        sysAdminApi.createUser({
          username: String(fd.get('username')),
          nickname: String(fd.get('nickname') || ''),
          password: String(fd.get('password')),
          userType: Number(fd.get('userType')),
          roleIds: null,
        }),
      '用户已创建',
    )
    if (result) {
      setCreateOpen(false)
      void load()
    }
  }

  const resetPwd = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!resetOpen) return
    const fd = new FormData(e.currentTarget)
    const result = await run(() => sysAdminApi.resetPassword(resetOpen.id, String(fd.get('newPassword'))), '密码已重置')
    if (result) {
      setResetOpen(null)
    }
  }

  return (
    <div className="page">
      <PageHeader
        title="用户管理"
        sub="学员 / 教师 / 管理员账号"
        extra={
          <button type="button" className="btn btn-primary btn-md" onClick={() => setCreateOpen(true)}>
            新建用户
          </button>
        }
      />
      <Card>
        <div className="admin-toolbar">
          <input
            className="input admin-search"
            placeholder="按账号/昵称搜索"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setPageNum(1)
                void load(1)
              }
            }}
          />
          <button type="button" className="btn btn-secondary btn-md" onClick={() => { setPageNum(1); void load(1) }}>
            搜索
          </button>
        </div>
        {error && <div className="inline-error">{error}</div>}
        <DataTable<AdminUser>
          loading={loading}
          rows={rows}
          rowKey={(r) => r.id}
          columns={[
            { key: 'id', title: 'ID', width: '60px', render: (r) => <span className="num">{r.id}</span> },
            { key: 'username', title: '账号' },
            { key: 'nickname', title: '昵称' },
            {
              key: 'userType',
              title: '类型',
              render: (r) => <Badge tone={r.userType === 3 ? 'primary' : r.userType === 2 ? 'warning' : 'neutral'}>{USER_TYPE[r.userType] ?? r.userType}</Badge>,
            },
            {
              key: 'status',
              title: '状态',
              render: (r) => <StatusBadge labels={USER_STATUS} value={r.status} />,
            },
            {
              key: 'actions',
              title: '操作',
              render: (r) => (
                <span className="row-actions">
                  <button type="button" className="link-btn" onClick={() => void run(() => sysAdminApi.updateUserStatus(r.id, r.status === 1 ? 0 : 1), '状态已更新').then((ok) => { if (ok) void load() })}>
                    {r.status === 1 ? '禁用' : '启用'}
                  </button>
                  <button type="button" className="link-btn" onClick={() => setResetOpen(r)}>
                    重置密码
                  </button>
                  <ConfirmButton
                    label="删除"
                    confirmText="确认删除"
                    onConfirm={() => {
                      void run(() => sysAdminApi.deleteUser(r.id), '用户已删除').then((ok) => {
                        if (ok) void load()
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

      <Modal open={createOpen} title="新建用户" onClose={() => setCreateOpen(false)}>
        <AdminForm onSubmit={create} submitLabel="创建">
          <Field label="账号">
            <input className="input" name="username" required minLength={4} placeholder="字母数字下划线，4-50 位" />
          </Field>
          <Field label="昵称">
            <input className="input" name="nickname" placeholder="可选" />
          </Field>
          <Field label="初始密码">
            <input className="input" name="password" required minLength={8} placeholder="至少 8 位" />
          </Field>
          <Field label="用户类型">
            <select className="input" name="userType" defaultValue={1}>
              <option value={1}>学员</option>
              <option value={2}>教师</option>
              <option value={3}>管理员</option>
            </select>
          </Field>
        </AdminForm>
      </Modal>

      <Modal open={resetOpen !== null} title={`重置密码 · ${resetOpen?.username ?? ''}`} onClose={() => setResetOpen(null)}>
        <AdminForm onSubmit={resetPwd} submitLabel="重置">
          <Field label="新密码">
            <input className="input" name="newPassword" required minLength={8} placeholder="8-64 位" />
          </Field>
        </AdminForm>
      </Modal>
    </div>
  )
}
