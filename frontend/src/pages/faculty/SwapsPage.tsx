import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post } from '../../api/client'
import type { DutyView, SwapView } from '../../api/types'
import { Empty, ErrorNote, Loading, PageHeader, StatusPill } from '../../components/ui'
import { formatDate, timeRange } from '../../format'

export default function SwapsPage() {
  const qc = useQueryClient()
  const swaps = useQuery({ queryKey: ['swaps'], queryFn: () => api<SwapView[]>('/api/swaps') })
  const act = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'accept' | 'reject' | 'cancel' }) =>
      post<SwapView>(`/api/swaps/${id}/${action}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['swaps'] })
      qc.invalidateQueries({ queryKey: ['my-duties'] })
    },
  })

  const incoming = swaps.data?.filter((s) => s.incoming) ?? []
  const others = swaps.data?.filter((s) => !s.incoming) ?? []

  return (
    <>
      <PageHeader title="Swap requests" />
      {swaps.isLoading && <Loading />}
      <ErrorNote error={swaps.error ?? act.error} />

      <h2 className="section-title">Waiting for you</h2>
      {incoming.length === 0 && !swaps.isLoading && <Empty>No requests waiting for you.</Empty>}
      {incoming.map((s) => (
        <div key={s.id} className="card swap">
          <p>
            <strong>{s.requesterDuty.facultyName}</strong> asks to swap.
            {s.note && <span className="muted"> “{s.note}”</span>}
          </p>
          <div className="swap-pair">
            <Slot label="You would take" duty={s.requesterDuty} />
            <Slot label="They would take" duty={s.targetDuty} />
          </div>
          <div className="row-actions">
            <button className="btn btn-primary" disabled={act.isPending} onClick={() => act.mutate({ id: s.id, action: 'accept' })}>
              Accept swap
            </button>
            <button className="btn btn-secondary" disabled={act.isPending} onClick={() => act.mutate({ id: s.id, action: 'reject' })}>
              Decline
            </button>
          </div>
        </div>
      ))}

      <h2 className="section-title">History</h2>
      {others.length === 0 && !swaps.isLoading && <Empty>No other swap requests.</Empty>}
      {others.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Requested by</th>
              <th>Duty offered</th>
              <th>Duty asked for</th>
              <th>Status</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {others.map((s) => (
              <tr key={s.id}>
                <td>{s.requesterDuty.facultyName}</td>
                <td>{brief(s.requesterDuty)}</td>
                <td>{brief(s.targetDuty)}</td>
                <td>
                  <StatusPill status={s.status} />
                </td>
                <td className="actions">
                  {s.status === 'PENDING' && (
                    <button className="btn btn-secondary" onClick={() => act.mutate({ id: s.id, action: 'cancel' })}>
                      Cancel
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

function Slot({ label, duty }: { label: string; duty: DutyView }) {
  return (
    <div className="slot">
      <span className="label">{label}</span>
      <strong>{formatDate(duty.date)}</strong>
      <span>{timeRange(duty.startTime, duty.endTime)}</span>
      <span className="muted">Room {duty.roomCode}</span>
    </div>
  )
}

function brief(d: DutyView) {
  return `${formatDate(d.date)}, ${timeRange(d.startTime, d.endTime)}, room ${d.roomCode}`
}
