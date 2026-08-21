import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../shared/api/types'
import { Button } from '../shared/ui/Button'
import { useToast } from '../shared/ui/Toast'

/* ================= 管理端通用组件（admin-development：克制、信息密度优先、无装饰动效） ================= */

export function PageHeader({ title, sub, extra }: { title: string; sub?: string; extra?: ReactNode }) {
  return (
    <header className="admin-head">
      <div>
        <h1 className="page-title">{title}</h1>
        {sub && <p className="page-sub">{sub}</p>}
      </div>
      {extra}
    </header>
  )
}

/** 数据表格（列定义驱动） */
export interface Column<T> {
  key: string
  title: string
  width?: string
  render?: (row: T) => ReactNode
}

export function DataTable<T>({
  columns,
  rows,
  loading,
  emptyText = '暂无数据',
  rowKey,
}: {
  columns: Column<T>[]
  rows: T[]
  loading?: boolean
  emptyText?: string
  rowKey: (row: T) => string | number
}) {
  if (loading) {
    return (
      <div className="admin-table">
        <div className="skeleton-group">
          <div className="skeleton-line" />
          <div className="skeleton-line" />
          <div className="skeleton-line" />
        </div>
      </div>
    )
  }
  if (rows.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-icon" aria-hidden="true">
          ◌
        </div>
        <p className="empty-title">{emptyText}</p>
      </div>
    )
  }
  return (
    <div className="admin-table-wrap">
      <table className="admin-table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} style={c.width ? { width: c.width } : undefined}>
                {c.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((c) => (
                <td key={c.key}>{c.render ? c.render(row) : String((row as Record<string, unknown>)[c.key] ?? '')}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** 分页 */
export function Pagination({
  pageNum,
  pageSize,
  total,
  onChange,
}: {
  pageNum: number
  pageSize: number
  total: number
  onChange: (pageNum: number) => void
}) {
  const pages = Math.max(1, Math.ceil(total / pageSize))
  return (
    <div className="pagination">
      <span className="page-sub">
        共 {total} 条 · 第 {pageNum} / {pages} 页
      </span>
      <div className="pagination-btns">
        <button type="button" className="btn btn-ghost btn-sm" disabled={pageNum <= 1} onClick={() => onChange(pageNum - 1)}>
          上一页
        </button>
        <button type="button" className="btn btn-ghost btn-sm" disabled={pageNum >= pages} onClick={() => onChange(pageNum + 1)}>
          下一页
        </button>
      </div>
    </div>
  )
}

/** Modal（表单/详情容器；破坏性操作由调用方二次确认） */
export function Modal({
  open,
  title,
  onClose,
  children,
  wide,
}: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  wide?: boolean
}) {
  if (!open) return null
  return (
    <div className="modal-mask" role="dialog" aria-modal="true" aria-label={title}>
      <div className={`modal-panel ${wide ? 'modal-wide' : ''}`}>
        <header className="modal-head">
          <h3>{title}</h3>
          <button type="button" className="link-btn" aria-label="关闭" onClick={onClose}>
            ✕
          </button>
        </header>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  )
}

/** 二次确认的破坏性按钮 */
export function ConfirmButton({
  label,
  confirmText,
  onConfirm,
  loading,
  danger = true,
}: {
  label: string
  confirmText: string
  onConfirm: () => void
  loading?: boolean
  danger?: boolean
}) {
  const [armed, setArmed] = useState(false)
  useEffect(() => {
    if (!armed) return
    const t = window.setTimeout(() => setArmed(false), 4000)
    return () => window.clearTimeout(t)
  }, [armed])
  if (!armed) {
    return (
      <Button size="sm" variant={danger ? 'danger' : 'secondary'} onClick={() => setArmed(true)}>
        {label}
      </Button>
    )
  }
  return (
    <span className="confirm-group">
      <Button size="sm" variant="danger" loading={loading} onClick={() => void onConfirm()}>
        {confirmText}
      </Button>
      <Button size="sm" variant="ghost" onClick={() => setArmed(false)}>
        取消
      </Button>
    </span>
  )
}

/** 表单行（label 包裹控件：可访问性关联，支持 getByLabel/键盘聚焦） */
export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <div className="field">
      <label className="field-label">
        <span className="field-label-text">{label}</span>
        {children}
      </label>
      {hint && <span className="field-hint">{hint}</span>}
    </div>
  )
}

/** 通用提交表单封装：错误内联展示 + toast 成功 */
export function AdminForm({
  onSubmit,
  submitLabel = '保存',
  loading,
  error,
  children,
}: {
  onSubmit: (e: FormEvent<HTMLFormElement>) => void
  submitLabel?: string
  loading?: boolean
  error?: string
  children: ReactNode
}) {
  return (
    <form
      className="admin-form"
      onSubmit={onSubmit}
    >
      {children}
      {error && (
        <div className="inline-error" role="alert">
          <span>{error}</span>
        </div>
      )}
      <Button type="submit" loading={loading}>
        {submitLabel}
      </Button>
    </form>
  )
}

/** 状态徽标（纯展示映射；状态权威在后端） */
export function StatusBadge({ labels, value }: { labels: Record<number, string>; value: number | null | undefined }) {
  const v = value ?? 0
  const text = labels[v] ?? `未知(${v})`
  const toneMap: Record<number, string> = {}
  return <span className={`badge ${toneMap[v] ?? 'badge-neutral'}`}>{text}</span>
}

/** 文件上传（multipart；统一错误处理） */
export function FileInput({
  accept,
  label,
  onFile,
  disabled,
}: {
  accept: string
  label: string
  onFile: (file: File) => void
  disabled?: boolean
}) {
  return (
    <label className={`upload-btn ${disabled ? 'upload-disabled' : ''}`}>
      {label}
      <input
        type="file"
        accept={accept}
        disabled={disabled}
        onChange={(e) => {
          const f = e.target.files?.[0]
          if (f) onFile(f)
          e.target.value = ''
        }}
      />
    </label>
  )
}

/** 统一 API 错误文本 */
export function errText(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '操作失败，请稍后重试'
}

/** 执行 + toast 封装（返回是否成功；注意 void 接口的 data 为 null，不能以返回值判成败） */
export function useApiAction() {
  const { toast } = useToast()
  return async function run<T>(fn: () => Promise<T>, successText: string): Promise<boolean> {
    try {
      await fn()
      toast(successText, 'success')
      return true
    } catch (e) {
      console.error('[run] failed:', successText, e)
      toast(errText(e), 'error')
      return false
    }
  }
}
