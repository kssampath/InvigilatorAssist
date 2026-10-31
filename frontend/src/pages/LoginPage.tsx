import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/context'
import { ErrorNote } from '../components/ui'

export default function LoginPage() {
  const { login } = useAuth()
  const [institution, setInstitution] = useState(() => localStorageGet('ia.institution') ?? '')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(institution.trim(), username.trim(), password)
      localStorageSet('ia.institution', institution.trim())
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-shell">
      <div className="login-brand">
        <span className="brand-mark" aria-hidden />
        <h1>InvigilatorAssist</h1>
        <p>Exam seating, invigilation duties and swaps for your institution, in one place.</p>
      </div>
      <form className="card login-card" onSubmit={submit}>
        <h2>Sign in</h2>
        <label>
          Institution code
          <input value={institution} onChange={(e) => setInstitution(e.target.value)} required autoComplete="organization" />
        </label>
        <label>
          Email or registration number
          <input value={username} onChange={(e) => setUsername(e.target.value)} required autoComplete="username" />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
        </label>
        <ErrorNote error={error} />
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}

function localStorageGet(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function localStorageSet(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    /* not available */
  }
}
