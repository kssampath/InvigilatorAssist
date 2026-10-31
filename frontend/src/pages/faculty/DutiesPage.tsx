import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post } from '../../api/client'
import type { DutyView, MyDuty, SwapView } from '../../api/types'
import { Empty, ErrorNote, LoadBar, Loading, PageHeader } from '../../components/ui'
import { formatDate, place, timeRange } from '../../format'

export default function DutiesPage() {
  const duties = useQuery({ queryKey: ['my-duties'], queryFn: () => api<MyDuty[]>('/api/faculty/duties') })
  const load = useQuery({
    queryKey: ['my-load'],
    queryFn: () => api<{ dutyCount: number; maxDuties: number }>('/api/faculty/load'),
  })
  const [swapFor, setSwapFor] = useState<MyDuty | null>(null)

  return (
    <>
      <PageHeader
        title="My invigilation duties"
        actions={
          load.data && (
            <div className="load-summary">
              <span>
                {load.data.dutyCount} of {load.data.maxDuties} duties this season
              </span>
              <LoadBar used={load.data.dutyCount} max={load.data.maxDuties} />
            </div>
          )
        }
      />
      {duties.isLoading && <Loading />}
      <ErrorNote error={duties.error} />
      {duties.data?.length === 0 && <Empty>No upcoming duties.</Empty>}
      <ul className="duty-list">
        {duties.data?.map((d) => (
          <li key={d.dutyId} className="card duty">
            <div>
              <h3>{formatDate(d.date)}</h3>
              <p>{timeRange(d.startTime, d.endTime)}</p>
              <p className="muted">
                {place(d.roomCode, d.building)} · {d.subjects.join(', ')}
              </p>
            </div>
            <button className="btn btn-secondary" onClick={() => setSwapFor(d)}>
              Find a swap
            </button>
          </li>
        ))}
      </ul>
      {swapFor && <SwapDialog duty={swapFor} onClose={() => setSwapFor(null)} />}
    </>
  )
}

function SwapDialog({ duty, onClose }: { duty: MyDuty; onClose: () => void }) {
  const qc = useQueryClient()
  const [note, setNote] = useState('')
  const candidates = useQuery({
    queryKey: ['swap-candidates', duty.dutyId],
    queryFn: () => api<DutyView[]>(`/api/faculty/duties/${duty.dutyId}/swap-candidates`),
  })
  const request = useMutation({
    mutationFn: (target: DutyView) =>
      post<SwapView>('/api/swaps', { myDutyId: duty.dutyId, targetDutyId: target.dutyId, note: note || null }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['swaps'] })
      onClose()
    },
  })

  return (
    <div className="dialog-backdrop" role="dialog" aria-modal="true" aria-label="Request a swap">
      <div className="card dialog">
        <header className="dialog-head">
          <div>
            <h2>Swap this duty</h2>
            <p className="muted">
              {formatDate(duty.date)}, {timeRange(duty.startTime, duty.endTime)}, room {duty.roomCode}
            </p>
          </div>
          <button className="link" onClick={onClose} aria-label="Close">
            Close
          </button>
        </header>
        <p className="muted">
          Only colleagues who are free at your slot, can take your room, and whose slot you can take are listed.
        </p>
        <label>
          Note to your colleague (optional)
          <input value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} placeholder="Doctor's appointment" />
        </label>
        {candidates.isLoading && <Loading />}
        <ErrorNote error={candidates.error ?? request.error} />
        {candidates.data?.length === 0 && <Empty>Nobody can swap with this duty right now.</Empty>}
        <ul className="candidate-list">
          {candidates.data?.map((c) => (
            <li key={c.dutyId}>
              <div>
                <strong>{c.facultyName}</strong>
                <span className="muted">
                  {' '}
                  · {formatDate(c.date)}, {timeRange(c.startTime, c.endTime)}, room {c.roomCode}
                </span>
              </div>
              <button className="btn btn-primary" disabled={request.isPending} onClick={() => request.mutate(c)}>
                Ask
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
