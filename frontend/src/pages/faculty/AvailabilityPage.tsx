import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, del, post } from '../../api/client'
import type { BlockedDate } from '../../api/types'
import { Empty, ErrorNote, Loading, PageHeader } from '../../components/ui'
import { formatDate } from '../../format'

export default function AvailabilityPage() {
  const qc = useQueryClient()
  const blocked = useQuery({ queryKey: ['blocked'], queryFn: () => api<BlockedDate[]>('/api/faculty/unavailability') })
  const [date, setDate] = useState('')
  const [reason, setReason] = useState('')
  const add = useMutation({
    mutationFn: () => post<BlockedDate>('/api/faculty/unavailability', { date, reason: reason || null }),
    onSuccess: () => {
      setDate('')
      setReason('')
      qc.invalidateQueries({ queryKey: ['blocked'] })
    },
  })
  const remove = useMutation({
    mutationFn: (id: number) => del(`/api/faculty/unavailability/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['blocked'] }),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    add.mutate()
  }

  return (
    <>
      <PageHeader
        title="My availability"
        subtitle="Block the dates you cannot invigilate. The next allocation will skip you on those days; existing duties are not changed."
      />
      <form className="card inline-form" onSubmit={submit}>
        <label>
          Date
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
        </label>
        <label className="grow">
          Reason (optional)
          <input value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} placeholder="Conference, leave…" />
        </label>
        <button className="btn btn-primary" disabled={add.isPending}>
          Block date
        </button>
        <ErrorNote error={add.error ?? remove.error} />
      </form>
      {blocked.isLoading && <Loading />}
      <ErrorNote error={blocked.error} />
      {blocked.data?.length === 0 && <Empty>No blocked dates.</Empty>}
      <ul className="plain-list">
        {blocked.data?.map((b) => (
          <li key={b.id} className="card blocked">
            <div>
              <strong>{formatDate(b.date)}</strong>
              {b.reason && <span className="muted"> · {b.reason}</span>}
            </div>
            <button className="link" onClick={() => remove.mutate(b.id)}>
              Remove
            </button>
          </li>
        ))}
      </ul>
    </>
  )
}
