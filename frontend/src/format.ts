// Display helpers. API dates are yyyy-MM-dd and times HH:mm[:ss]; shown in Indian English format.

export function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

export function formatTime(hhmm: string): string {
  const [h, m] = hhmm.split(':').map(Number)
  const suffix = h >= 12 ? 'PM' : 'AM'
  const hour = h % 12 === 0 ? 12 : h % 12
  return `${hour}:${String(m).padStart(2, '0')} ${suffix}`
}

export function timeRange(start: string, end: string): string {
  return `${formatTime(start)} – ${formatTime(end)}`
}

/** Comma-joined room location, skipping blanks. */
export function place(room: string, building?: string | null, floor?: number | null): string {
  const parts = [`Room ${room}`]
  if (building) parts.push(building)
  if (floor !== null && floor !== undefined) parts.push(floor === 0 ? 'Ground floor' : `Floor ${floor}`)
  return parts.join(', ')
}
