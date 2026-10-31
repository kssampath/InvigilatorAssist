import { useState, type FormEvent } from 'react'
import { post } from '../api/client'
import { useAuth } from '../auth/context'
import { ErrorNote, PageHeader } from '../components/ui'

export default function ChangePasswordPage() {
  const { me, refresh } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [done, setDone] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (next !== confirm) {
      setError(new Error('The new passwords do not match'))
      return
    }
    try {
      await post('/api/auth/change-password', { currentPassword: current, newPassword: next })
      setDone(true)
      await refresh()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <>
      <PageHeader
        title="Change password"
        subtitle={me?.mustChangePassword ? 'You are using a temporary password. Choose your own to continue.' : undefined}
      />
      <form className="card narrow" onSubmit={submit}>
        <label>
          Current password
          <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required autoComplete="current-password" />
        </label>
        <label>
          New password (at least 8 characters)
          <input type="password" minLength={8} value={next} onChange={(e) => setNext(e.target.value)} required autoComplete="new-password" />
        </label>
        <label>
          Repeat new password
          <input type="password" minLength={8} value={confirm} onChange={(e) => setConfirm(e.target.value)} required autoComplete="new-password" />
        </label>
        <ErrorNote error={error} />
        {done && <div className="note note-ok">Password changed.</div>}
        <button className="btn btn-primary">Save password</button>
      </form>
    </>
  )
}
