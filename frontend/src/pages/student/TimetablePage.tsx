import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Timetable } from '../../api/types'
import { Empty, ErrorNote, Loading, PageHeader } from '../../components/ui'
import { formatDate, place, timeRange } from '../../format'

export default function TimetablePage() {
  const tt = useQuery({ queryKey: ['timetable'], queryFn: () => api<Timetable>('/api/student/timetable') })

  return (
    <>
      <PageHeader title="My exams" subtitle={tt.data ? `${tt.data.name} · ${tt.data.regNo}` : undefined} />
      {tt.isLoading && <Loading />}
      <ErrorNote error={tt.error} />
      {tt.data?.exams.length === 0 && <Empty>No seats allotted yet. Check back after the exam cell publishes seating.</Empty>}
      <ul className="exam-list">
        {tt.data?.exams.map((e) => (
          <li key={e.date + e.startTime + e.subjectCode} className="card exam">
            <div className="exam-when">
              <strong>{formatDate(e.date)}</strong>
              <span>{timeRange(e.startTime, e.endTime)}</span>
            </div>
            <div className="exam-what">
              <h3>{e.subjectName}</h3>
              <p className="muted">{e.subjectCode}</p>
            </div>
            <div className="exam-where">
              <span className="seat-no" aria-label={`Seat ${e.seatNo}`}>
                {e.seatNo}
              </span>
              <span>{place(e.roomCode, e.building, e.floor)}</span>
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}
