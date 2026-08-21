import type { ReactNode } from 'react'

export function Card({
  title,
  extra,
  children,
  className = '',
  pad = true,
}: {
  title?: string
  extra?: ReactNode
  children: ReactNode
  className?: string
  pad?: boolean
}) {
  return (
    <section className={`card ${pad ? 'card-pad' : ''} ${className}`}>
      {(title || extra) && (
        <header className="card-head">
          {title && <h2 className="card-title">{title}</h2>}
          {extra}
        </header>
      )}
      {children}
    </section>
  )
}

export function Skeleton({ lines = 3, className = '' }: { lines?: number; className?: string }) {
  return (
    <div className={`skeleton-group ${className}`} aria-hidden="true">
      {Array.from({ length: lines }, (_, i) => (
        <div key={i} className="skeleton-line" style={{ width: `${92 - (i % 3) * 18}%` }} />
      ))}
    </div>
  )
}

export function Spinner({ label = '加载中…' }: { label?: string }) {
  return (
    <div className="center-block" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

export function EmptyState({
  icon = '◌',
  title,
  hint,
  action,
}: {
  icon?: string
  title: string
  hint?: string
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <div className="empty-icon" aria-hidden="true">
        {icon}
      </div>
      <p className="empty-title">{title}</p>
      {hint && <p className="empty-hint">{hint}</p>}
      {action && <div className="empty-action">{action}</div>}
    </div>
  )
}

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode
  tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'error'
}) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

export function InlineError({ message, retry }: { message: string; retry?: () => void }) {
  return (
    <div className="inline-error" role="alert">
      <span>{message}</span>
      {retry && (
        <button type="button" className="link-btn" onClick={retry}>
          重试
        </button>
      )}
    </div>
  )
}

export function ProgressBar({ value, max = 100 }: { value: number; max?: number }) {
  const pct = max <= 0 ? 0 : Math.max(0, Math.min(100, (value / max) * 100))
  return (
    <div className="progress-track" role="progressbar" aria-valuenow={Math.round(pct)} aria-valuemin={0} aria-valuemax={100}>
      <div className="progress-fill" style={{ width: `${pct}%` }} />
    </div>
  )
}
