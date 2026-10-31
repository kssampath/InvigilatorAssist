import type { ReactNode } from 'react'
import { ApiError } from '../api/client'

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : 'Something went wrong'
  const details = error instanceof ApiError ? error.errors : []
  return (
    <div className="note note-error" role="alert">
      <strong>{message}</strong>
      {details.length > 0 && (
        <ul>
          {details.slice(0, 20).map((d) => (
            <li key={d}>{d}</li>
          ))}
          {details.length > 20 && <li>…and {details.length - 20} more</li>}
        </ul>
      )}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="empty">{children}</p>
}

export function Loading() {
  return <p className="muted">Loading…</p>
}

export function StatusPill({ status }: { status: string }) {
  return <span className={`pill pill-${status.toLowerCase()}`}>{status.toLowerCase()}</span>
}

export function LoadBar({ used, max }: { used: number; max: number }) {
  const pct = max === 0 ? 0 : Math.min(100, Math.round((used / max) * 100))
  const level = pct >= 100 ? 'full' : pct >= 75 ? 'high' : 'ok'
  return (
    <div className="loadbar" aria-label={`${used} of ${max} duties`}>
      <div className={`loadbar-fill loadbar-${level}`} style={{ width: `${pct}%` }} />
    </div>
  )
}
