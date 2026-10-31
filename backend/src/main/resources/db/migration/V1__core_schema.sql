-- InvigilatorAssist core schema (PostgreSQL).
-- Every business table carries institution_id: one deployment serves many colleges (row-level tenancy).

CREATE TABLE institution (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE department (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institution(id),
    code           VARCHAR(16)  NOT NULL,
    name           VARCHAR(120) NOT NULL,
    UNIQUE (institution_id, code)
);

CREATE TABLE app_user (
    id                   BIGSERIAL PRIMARY KEY,
    institution_id       BIGINT       NOT NULL REFERENCES institution(id),
    username             VARCHAR(120) NOT NULL,          -- email for staff, registration number for students
    password_hash        VARCHAR(100) NOT NULL,          -- BCrypt
    full_name            VARCHAR(120) NOT NULL,
    email                VARCHAR(200),
    role                 VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'FACULTY', 'STUDENT')),
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (institution_id, username)
);

CREATE TABLE faculty (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institution(id),
    user_id        BIGINT      NOT NULL UNIQUE REFERENCES app_user(id),
    employee_no    VARCHAR(32) NOT NULL,
    department_id  BIGINT      REFERENCES department(id),
    designation    VARCHAR(60) NOT NULL,
    max_duties     INT         NOT NULL CHECK (max_duties >= 0),   -- quota per exam season
    duty_count     INT         NOT NULL DEFAULT 0,
    last_duty_at   TIMESTAMP,                                     -- used to spread duties over time
    UNIQUE (institution_id, employee_no)
);

CREATE TABLE faculty_unavailability (
    id         BIGSERIAL PRIMARY KEY,
    faculty_id BIGINT       NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    unavailable_on DATE     NOT NULL,
    reason     VARCHAR(200),
    UNIQUE (faculty_id, unavailable_on)
);

CREATE TABLE room (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institution(id),
    code           VARCHAR(32) NOT NULL,
    building       VARCHAR(80),
    floor          INT,
    capacity       INT         NOT NULL CHECK (capacity > 0),
    accessible     BOOLEAN     NOT NULL DEFAULT FALSE,           -- ground floor / ramp / lift
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (institution_id, code)
);

CREATE TABLE student (
    id                    BIGSERIAL PRIMARY KEY,
    institution_id        BIGINT       NOT NULL REFERENCES institution(id),
    user_id               BIGINT       UNIQUE REFERENCES app_user(id),
    reg_no                VARCHAR(32)  NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    section               VARCHAR(16),
    semester              INT          NOT NULL CHECK (semester BETWEEN 1 AND 12),
    department_id         BIGINT       REFERENCES department(id),
    needs_accessible_room BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (institution_id, reg_no)
);

CREATE TABLE subject (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institution(id),
    code           VARCHAR(16)  NOT NULL,
    name           VARCHAR(120) NOT NULL,
    semester       INT          NOT NULL CHECK (semester BETWEEN 1 AND 12),
    department_id  BIGINT       REFERENCES department(id),     -- NULL = common to all departments of that semester
    instructor_id  BIGINT       REFERENCES faculty(id),        -- never invigilates this subject's exam
    elective       BOOLEAN      NOT NULL DEFAULT FALSE,        -- TRUE: only explicitly enrolled students write it
    UNIQUE (institution_id, code)
);

-- Extra enrollments beyond "my semester + department": backlog (re-take) papers and chosen electives.
CREATE TABLE backlog_enrollment (
    student_id BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
    PRIMARY KEY (student_id, subject_id)
);

CREATE TABLE exam_slot (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institution(id),
    subject_id     BIGINT      NOT NULL REFERENCES subject(id),
    exam_date      DATE        NOT NULL,
    start_time     TIME        NOT NULL,
    end_time       TIME        NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'ALLOCATED', 'COMPLETED', 'CANCELLED')),
    CHECK (end_time > start_time),
    UNIQUE (subject_id, exam_date, start_time)
);
CREATE INDEX idx_exam_slot_sitting ON exam_slot (institution_id, exam_date, start_time);

-- A "sitting" = all exams of one institution starting at the same date + time.
-- Seating mixes subjects within a sitting, so rooms are allocated per sitting, not per subject.
CREATE TABLE room_allocation (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT NOT NULL REFERENCES institution(id),
    exam_date      DATE   NOT NULL,
    start_time     TIME   NOT NULL,
    end_time       TIME   NOT NULL,
    room_id        BIGINT NOT NULL REFERENCES room(id),
    UNIQUE (institution_id, exam_date, start_time, room_id)
);

CREATE TABLE invigilation_duty (
    id                 BIGSERIAL PRIMARY KEY,
    room_allocation_id BIGINT      NOT NULL REFERENCES room_allocation(id) ON DELETE CASCADE,
    faculty_id         BIGINT      NOT NULL REFERENCES faculty(id),
    duty_role          VARCHAR(16) NOT NULL DEFAULT 'INVIGILATOR' CHECK (duty_role IN ('INVIGILATOR', 'RELIEVER')),
    UNIQUE (room_allocation_id, faculty_id)
);
CREATE INDEX idx_duty_faculty ON invigilation_duty (faculty_id);

CREATE TABLE seat_allocation (
    id                 BIGSERIAL PRIMARY KEY,
    room_allocation_id BIGINT NOT NULL REFERENCES room_allocation(id) ON DELETE CASCADE,
    exam_slot_id       BIGINT NOT NULL REFERENCES exam_slot(id),
    student_id         BIGINT NOT NULL REFERENCES student(id),
    seat_no            INT    NOT NULL,
    UNIQUE (room_allocation_id, seat_no),
    UNIQUE (exam_slot_id, student_id)
);
CREATE INDEX idx_seat_student ON seat_allocation (student_id);

CREATE TABLE swap_request (
    id                BIGSERIAL PRIMARY KEY,
    institution_id    BIGINT      NOT NULL REFERENCES institution(id),
    requester_duty_id BIGINT      NOT NULL REFERENCES invigilation_duty(id) ON DELETE CASCADE,
    target_duty_id    BIGINT      NOT NULL REFERENCES invigilation_duty(id) ON DELETE CASCADE,
    status            VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    note              VARCHAR(300),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at        TIMESTAMPTZ,
    CHECK (requester_duty_id <> target_duty_id)
);
CREATE INDEX idx_swap_target ON swap_request (target_duty_id, status);
