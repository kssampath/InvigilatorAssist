// Shapes returned by the Spring Boot API. Dates are ISO strings (yyyy-MM-dd), times are HH:mm:ss.

export type Role = 'ADMIN' | 'FACULTY' | 'STUDENT'

export interface LoginResponse {
  token: string
  role: Role
  fullName: string
  mustChangePassword: boolean
}

export interface Me {
  userId: number
  username: string
  fullName: string
  role: Role
  institutionCode: string
  institutionName: string
  mustChangePassword: boolean
}

export type ImportKind = 'departments' | 'rooms' | 'faculty' | 'subjects' | 'students' | 'backlogs'

export interface ImportResult {
  kind: string
  created: number
  updated: number
  newCredentials: { username: string; fullName: string; temporaryPassword: string }[]
}

export interface ExamSlot {
  id: number
  subjectCode: string
  subjectName: string
  semester: number
  date: string
  startTime: string
  endTime: string
  status: string
}

export interface SittingSummary {
  date: string
  startTime: string
  subjects: string[]
  allocated: boolean
}

export interface SeatView {
  seatNo: number
  regNo: string
  studentName: string
  subjectCode: string
}

export interface RoomView {
  roomAllocationId: number
  roomCode: string
  building: string | null
  capacity: number
  invigilators: string[]
  studentsPerSubject: Record<string, number>
  seats: SeatView[]
}

export interface SittingView {
  date: string
  startTime: string
  endTime: string
  studentsSeated: number
  roomsUsed: number
  invigilatorsAssigned: number
  rooms: RoomView[]
}

export interface FacultyLoad {
  facultyId: number
  employeeNo: string
  name: string
  department: string | null
  designation: string
  dutyCount: number
  maxDuties: number
}

export interface MyDuty {
  dutyId: number
  date: string
  startTime: string
  endTime: string
  roomCode: string
  building: string | null
  subjects: string[]
}

export interface DutyView {
  dutyId: number
  date: string
  startTime: string
  endTime: string
  roomCode: string
  building: string | null
  facultyName: string
}

export interface SwapView {
  id: number
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED'
  note: string | null
  incoming: boolean
  requesterDuty: DutyView
  targetDuty: DutyView
}

export interface BlockedDate {
  id: number
  date: string
  reason: string | null
}

export interface TimetableEntry {
  date: string
  startTime: string
  endTime: string
  subjectCode: string
  subjectName: string
  roomCode: string
  building: string | null
  floor: number | null
  seatNo: number
}

export interface Timetable {
  regNo: string
  name: string
  exams: TimetableEntry[]
}
