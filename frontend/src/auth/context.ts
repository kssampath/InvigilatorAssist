import { createContext, useContext } from 'react'
import type { Me } from '../api/types'

export interface AuthState {
  me: Me | null
  loading: boolean
  login: (institutionCode: string, username: string, password: string) => Promise<void>
  logout: () => void
  refresh: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
