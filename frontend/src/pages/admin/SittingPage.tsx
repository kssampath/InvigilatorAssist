import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, del } from '../../api/client'
import type { SittingView } from '../../api/types'
import { ErrorNote, Loading, PageHeader } from '../../components/ui'
import { formatDate, timeRange } from '../../format'

export default function SittingPage() {
  const { date = '', start = '' } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const query = new URLSearchParams({ date, startTime: start }).toString()
  const sitting = useQuery({
    queryKey: ['sitting', date, start],
    queryFn: () => api<SittingView>(`/api/admin/sittings/allocation?${query}`),
  })
  const clear = useMutation({
    mutationFn: () => del(`/api/admin/sittings/allocation?${query}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sittings'] })
      navigate('/admin/schedule')
    },
  })

  if (sitting.isLoading) return <Loading />
  if (sitting.error) return <ErrorNote error={sitting.error} />
  const s = sitting.data!

  return (
    <>
      <PageHeader
        title={`${formatDate(s.date)}, ${timeRange(s.startTime, s.endTime)}`}
        subtitle={`${s.studentsSeated} students in ${s.roomsUsed} rooms, ${s.invigilatorsAssigned} invigilators`}
        actions={
          <>
            <button className="btn btn-secondary" onClick={() => window.print()}>
              Print door sheets
            </button>
            <button
              className="btn btn-danger"
              disabled={clear.isPending}
              onClick={() => {
                if (confirm('Clear this allocation? Seats and duties will be removed and quotas given back.')) clear.mutate()
              }}
            >
              Clear allocation
            </button>
          </>
        }
      />
      <ErrorNote error={clear.error} />
      <div className="rooms">
        {s.rooms.map((r) => (
          <section key={r.roomAllocationId} className="card room">
            <header className="room-head">
              <div>
                <h3>Room {r.roomCode}</h3>
                <p className="muted">
                  {r.building ?? ''} {r.seats.length}/{r.capacity} seats
                </p>
              </div>
              <div className="room-meta">
                <span className="label">Invigilators</span>
                <strong>{r.invigilators.join(', ')}</strong>
              </div>
            </header>
            <p className="subject-mix">
              {Object.entries(r.studentsPerSubject).map(([code, n]) => (
                <span key={code} className="chip">
                  {code} × {n}
                </span>
              ))}
            </p>
            <table className="table compact">
              <thead>
                <tr>
                  <th>Seat</th>
                  <th>Reg. no</th>
                  <th>Name</th>
                  <th>Subject</th>
                </tr>
              </thead>
              <tbody>
                {r.seats.map((seat) => (
                  <tr key={seat.seatNo}>
                    <td>{seat.seatNo}</td>
                    <td>{seat.regNo}</td>
                    <td>{seat.studentName}</td>
                    <td>{seat.subjectCode}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        ))}
      </div>
    </>
  )
}
