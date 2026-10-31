import { Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom'
import type { Role } from './api/types'
import { useAuth } from './auth/context'
import { Loading } from './components/ui'
import ChangePasswordPage from './pages/ChangePasswordPage'
import LoginPage from './pages/LoginPage'
import FacultyLoadPage from './pages/admin/FacultyLoadPage'
import ImportPage from './pages/admin/ImportPage'
import SchedulePage from './pages/admin/SchedulePage'
import SittingPage from './pages/admin/SittingPage'
import AvailabilityPage from './pages/faculty/AvailabilityPage'
import DutiesPage from './pages/faculty/DutiesPage'
import SwapsPage from './pages/faculty/SwapsPage'
import TimetablePage from './pages/student/TimetablePage'

const NAV: Record<Role, { to: string; label: string }[]> = {
  ADMIN: [
    { to: '/admin/schedule', label: 'Schedule' },
    { to: '/admin/faculty', label: 'Duty fairness' },
    { to: '/admin/import', label: 'Import data' },
  ],
  FACULTY: [
    { to: '/faculty/duties', label: 'My duties' },
    { to: '/faculty/swaps', label: 'Swaps' },
    { to: '/faculty/availability', label: 'Availability' },
  ],
  STUDENT: [{ to: '/student/timetable', label: 'My exams' }],
}

const HOME: Record<Role, string> = {
  ADMIN: '/admin/schedule',
  FACULTY: '/faculty/duties',
  STUDENT: '/student/timetable',
}

export default function App() {
  const { me, loading } = useAuth()
  if (loading) return <Loading />
  if (!me) return <LoginPage />

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/account/password" element={<ChangePasswordPage />} />
        <Route element={<RequireRole role="ADMIN" />}>
          <Route path="/admin/schedule" element={<SchedulePage />} />
          <Route path="/admin/sittings/:date/:start" element={<SittingPage />} />
          <Route path="/admin/faculty" element={<FacultyLoadPage />} />
          <Route path="/admin/import" element={<ImportPage />} />
        </Route>
        <Route element={<RequireRole role="FACULTY" />}>
          <Route path="/faculty/duties" element={<DutiesPage />} />
          <Route path="/faculty/swaps" element={<SwapsPage />} />
          <Route path="/faculty/availability" element={<AvailabilityPage />} />
        </Route>
        <Route element={<RequireRole role="STUDENT" />}>
          <Route path="/student/timetable" element={<TimetablePage />} />
        </Route>
        <Route path="*" element={<Navigate to={HOME[me.role]} replace />} />
      </Route>
    </Routes>
  )
}

function RequireRole({ role }: { role: Role }) {
  const { me } = useAuth()
  if (!me) return null
  if (me.mustChangePassword) return <Navigate to="/account/password" replace />
  return me.role === role ? <Outlet /> : <Navigate to={HOME[me.role]} replace />
}

function Layout() {
  const { me, logout } = useAuth()
  if (!me) return null
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden />
          <div>
            <strong>InvigilatorAssist</strong>
            <span>{me.institutionName}</span>
          </div>
        </div>
        <nav>
          {NAV[me.role].map((n) => (
            <NavLink key={n.to} to={n.to} className={({ isActive }) => (isActive ? 'active' : undefined)}>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="account">
          <span>{me.fullName}</span>
          <NavLink to="/account/password">Change password</NavLink>
          <button className="link" onClick={logout}>
            Sign out
          </button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
