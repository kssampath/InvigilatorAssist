package com.invigilatorassist.exams;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Request and response shapes for the admin exam endpoints. */
public final class ExamDtos {
    private ExamDtos() {}

    public record CreateExamSlot(@NotBlank String subjectCode, @NotNull LocalDate date,
                                 @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

    public record ExamSlotView(long id, String subjectCode, String subjectName, int semester, LocalDate date,
                               LocalTime startTime, LocalTime endTime, String status) {}

    public record SittingKey(@NotNull LocalDate date, @NotNull LocalTime startTime) {}

    public record SittingSummary(LocalDate date, LocalTime startTime, List<String> subjects, boolean allocated) {}

    public record SeatView(int seatNo, String regNo, String studentName, String subjectCode) {}

    public record RoomView(long roomAllocationId, String roomCode, String building, int capacity,
                           List<String> invigilators, Map<String, Long> studentsPerSubject, List<SeatView> seats) {}

    public record SittingView(LocalDate date, LocalTime startTime, LocalTime endTime, int studentsSeated,
                              int roomsUsed, int invigilatorsAssigned, List<RoomView> rooms) {}

    public record FacultyLoadView(long facultyId, String employeeNo, String name, String department,
                                  String designation, int dutyCount, int maxDuties) {}

    public record RoomRow(long id, String code, String building, Integer floor, int capacity, boolean accessible,
                          boolean active) {}

    public record SubjectRow(long id, String code, String name, int semester, String department, String instructor,
                             boolean elective) {}
}
