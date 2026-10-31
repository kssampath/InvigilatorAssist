import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post } from '../../api/client'
import type { ExamSlot, SittingSummary, SittingView } from '../../api/types'
import { Empty, ErrorNote, Loading, PageHeader, StatusPill } from '../../components/ui'
import { formatDate, formatTime } from '../../format'

export default function SchedulePage() {
  const qc = useQueryClient()
  const sittings = useQuery({ queryKey: ['sittings'], queryFn: () => api<SittingSummary[]>('/api/admin/sittings') })
  const [form, setForm] = useState({ subjectCode: '', date: '', startTime: '09:30', endTime: '12:30' })

  const create = useMutation({
    mutationFn: () => post<ExamSlot>('/api/admin/exam-slots', form),
    onSuccess: () => {
      setForm((f) => ({ ...f, subjectCode: '' }))
      qc.invalidateQueries({ queryKey: ['sittings'] })
    },
  })

  const allocate = useMutation({
    mutationFn: (s: SittingSummary) =>
      post<SittingView>('/api/admin/sittings/allocate', { date: s.date, startTime: s.startTime }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sittings'] }),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  return (
    <>
      <PageHeader
        title="Exam schedule"
        subtitle="Exams that start at the same date and time form one sitting. Allocating a sitting seats every student and assigns invigilators in one step."
      />

      <form className="card inline-form" onSubmit={submit}>
        <label>
          Subject code
          <input value={form.subjectCode} onChange={(e) => setForm({ ...form, subjectCode: e.target.value })} placeholder="CSE301" required />
        </label>
        <label>
          Date
          <input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} required />
        </label>
        <label>
          Starts
          <input type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} required />
        </label>
        <label>
          Ends
          <input type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} required />
        </label>
        <button className="btn btn-primary" disabled={create.isPending}>
          Add exam
        </button>
        <ErrorNote error={create.error} />
      </form>

      <ErrorNote error={allocate.error} />
      {sittings.isLoading && <Loading />}
      <ErrorNote error={sittings.error} />
      {sittings.data?.length === 0 && <Empty>No exams scheduled yet.</Empty>}
      {sittings.data && sittings.data.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Starts</th>
              <th>Subjects</th>
              <th>Status</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {sittings.data.map((s) => (
              <tr key={s.date + s.startTime}>
                <td>{formatDate(s.date)}</td>
                <td>{formatTime(s.startTime)}</td>
                <td>{s.subjects.join(', ')}</td>
                <td>
                  <StatusPill status={s.allocated ? 'ALLOCATED' : 'SCHEDULED'} />
                </td>
                <td className="actions">
                  {s.allocated ? (
                    <Link className="btn btn-secondary" to={`/admin/sittings/${s.date}/${s.startTime}`}>
                      View seating
                    </Link>
                  ) : (
                    <button
                      className="btn btn-primary"
                      disabled={allocate.isPending}
                      onClick={() => allocate.mutate(s)}
                    >
                      {allocate.isPending && allocate.variables === s ? 'Allocating…' : 'Allocate'}
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
