import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { FacultyLoad } from '../../api/types'
import { Empty, ErrorNote, LoadBar, Loading, PageHeader } from '../../components/ui'

export default function FacultyLoadPage() {
  const faculty = useQuery({ queryKey: ['faculty-load'], queryFn: () => api<FacultyLoad[]>('/api/admin/faculty') })
  const rows = faculty.data ?? []
  const used = rows.reduce((n, f) => n + f.dutyCount, 0)
  const quota = rows.reduce((n, f) => n + f.maxDuties, 0)

  return (
    <>
      <PageHeader
        title="Duty fairness"
        subtitle={rows.length ? `${used} of ${quota} duty slots used across ${rows.length} faculty, most loaded first` : undefined}
      />
      {faculty.isLoading && <Loading />}
      <ErrorNote error={faculty.error} />
      {faculty.data?.length === 0 && <Empty>No faculty imported yet.</Empty>}
      {rows.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Emp. no</th>
              <th>Name</th>
              <th>Dept.</th>
              <th>Designation</th>
              <th>Duties</th>
              <th className="wide">Load</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((f) => (
              <tr key={f.facultyId}>
                <td>{f.employeeNo}</td>
                <td>{f.name}</td>
                <td>{f.department ?? '—'}</td>
                <td>{f.designation}</td>
                <td>
                  {f.dutyCount} / {f.maxDuties}
                </td>
                <td>
                  <LoadBar used={f.dutyCount} max={f.maxDuties} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
