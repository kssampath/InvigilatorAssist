import { useState } from 'react'
import { api } from '../../api/client'
import type { ImportKind, ImportResult } from '../../api/types'
import { ErrorNote, PageHeader } from '../../components/ui'

const STEPS: { kind: ImportKind; label: string; columns: string }[] = [
  { kind: 'departments', label: 'Departments', columns: 'code, name' },
  { kind: 'rooms', label: 'Rooms', columns: 'code, capacity, building?, floor?, accessible?' },
  { kind: 'faculty', label: 'Faculty', columns: 'employee_no, full_name, email, designation, max_duties, department_code?' },
  { kind: 'subjects', label: 'Subjects', columns: 'code, name, semester, department_code?, instructor_employee_no?, elective?' },
  { kind: 'students', label: 'Students', columns: 'reg_no, name, semester, section?, department_code?, needs_accessible_room?' },
  { kind: 'backlogs', label: 'Backlogs & electives', columns: 'reg_no, subject_code' },
]

export default function ImportPage() {
  const [results, setResults] = useState<Partial<Record<ImportKind, ImportResult>>>({})
  const [errors, setErrors] = useState<Partial<Record<ImportKind, unknown>>>({})
  const [busy, setBusy] = useState<ImportKind | null>(null)

  async function upload(kind: ImportKind, file: File) {
    setBusy(kind)
    setErrors((e) => ({ ...e, [kind]: null }))
    try {
      const body = new FormData()
      body.append('file', file)
      const res = await api<ImportResult>(`/api/admin/import/${kind}`, { method: 'POST', body })
      setResults((r) => ({ ...r, [kind]: res }))
    } catch (err) {
      setErrors((e) => ({ ...e, [kind]: err }))
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <PageHeader
        title="Import data"
        subtitle="Upload CSV files in this order. Re-uploading a file updates existing rows; a file with any bad row changes nothing."
      />
      <ol className="steps">
        {STEPS.map((s, i) => {
          const res = results[s.kind]
          return (
            <li key={s.kind} className="card step">
              <div className="step-head">
                <span className="step-no">{i + 1}</span>
                <div>
                  <h3>{s.label}</h3>
                  <code className="muted">{s.columns}</code>
                </div>
                <label className="btn btn-secondary file-btn">
                  {busy === s.kind ? 'Uploading…' : 'Choose CSV'}
                  <input
                    type="file"
                    accept=".csv,text/csv"
                    disabled={busy !== null}
                    onChange={(e) => {
                      const f = e.target.files?.[0]
                      if (f) upload(s.kind, f)
                      e.target.value = ''
                    }}
                  />
                </label>
              </div>
              <ErrorNote error={errors[s.kind]} />
              {res && (
                <div className="note note-ok">
                  {res.created} created, {res.updated} updated.
                  {res.newCredentials.length > 0 && (
                    <CredentialsDownload kind={s.kind} rows={res.newCredentials} />
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ol>
    </>
  )
}

/** New accounts' temporary passwords are shown once by the API; let the admin save them as CSV. */
function CredentialsDownload({ kind, rows }: { kind: string; rows: ImportResult['newCredentials'] }) {
  function download() {
    const lines = ['username,full_name,temporary_password', ...rows.map((r) => [r.username, r.fullName, r.temporaryPassword].map(csvCell).join(','))]
    const url = URL.createObjectURL(new Blob([lines.join('\n')], { type: 'text/csv' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `${kind}-temporary-passwords.csv`
    a.click()
    URL.revokeObjectURL(url)
  }
  return (
    <p>
      {rows.length} new account(s).{' '}
      <button type="button" className="link" onClick={download}>
        Download temporary passwords
      </button>{' '}
      — this is the only time they are shown.
    </p>
  )
}

function csvCell(v: string) {
  return /[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v
}
