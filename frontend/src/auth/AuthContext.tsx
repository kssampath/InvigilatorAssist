import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, post, setUnauthorizedHandler, tokenStore } from '../api/client'
import type { LoginResponse, Me } from '../api/types'

import { AuthContext } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(() => tokenStore.get() !== null)
  const queryClient = useQueryClient()

  const logout = useCallback(() => {
    tokenStore.set(null)
    setMe(null)
    queryClient.clear()
  }, [queryClient])

  const refresh = useCallback(async () => {
    setMe(await api<Me>('/api/me'))
  }, [])

  // Restore the session after a page reload.
  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null))
    if (!tokenStore.get()) return
    let cancelled = false
    api<Me>('/api/me')
      .then((m) => !cancelled && setMe(m))
      .catch(() => !cancelled && logout())
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [logout])

  const login = useCallback(
    async (institutionCode: string, username: string, password: string) => {
      const res = await post<LoginResponse>('/api/auth/login', { institutionCode, username, password })
      tokenStore.set(res.token)
      await refresh()
    },
    [refresh],
  )

  const value = useMemo(() => ({ me, loading, login, logout, refresh }), [me, loading, login, logout, refresh])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
