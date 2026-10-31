import { describe, expect, it } from 'vitest'
import { formatTime, place, timeRange } from './format'

describe('format', () => {
  it('formats 24h times as 12h', () => {
    expect(formatTime('09:30:00')).toBe('9:30 AM')
    expect(formatTime('12:00')).toBe('12:00 PM')
    expect(formatTime('00:15')).toBe('12:15 AM')
    expect(formatTime('14:05')).toBe('2:05 PM')
  })

  it('builds a time range', () => {
    expect(timeRange('09:30', '12:30')).toBe('9:30 AM – 12:30 PM')
  })

  it('describes a room location', () => {
    expect(place('A001', 'Block A', 0)).toBe('Room A001, Block A, Ground floor')
    expect(place('B201', null, 2)).toBe('Room B201, Floor 2')
    expect(place('X1')).toBe('Room X1')
  })
})
