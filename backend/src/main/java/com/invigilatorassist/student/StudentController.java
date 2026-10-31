package com.invigilatorassist.student;

import com.invigilatorassist.common.ApiException;
import com.invigilatorassist.domain.Student;
import com.invigilatorassist.repository.SeatAllocationRepository;
import com.invigilatorassist.repository.StudentRepository;
import com.invigilatorassist.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Students only ever see their own timetable: the identity comes from the token, never from a form field. */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    public record TimetableEntry(LocalDate date, LocalTime startTime, LocalTime endTime, String subjectCode,
                                 String subjectName, String roomCode, String building, Integer floor, int seatNo) {}

    public record Timetable(String regNo, String name, List<TimetableEntry> exams) {}

    private final StudentRepository students;
    private final SeatAllocationRepository seats;

    public StudentController(StudentRepository students, SeatAllocationRepository seats) {
        this.students = students;
        this.seats = seats;
    }

    @GetMapping("/timetable")
    @Transactional(readOnly = true)
    public Timetable timetable(@AuthenticationPrincipal Jwt jwt) {
        Student me = students.findByUserId(CurrentUser.of(jwt).userId())
                .orElseThrow(() -> ApiException.forbidden("No student profile for this account"));
        List<TimetableEntry> exams = seats.findTimetableForStudent(me.getId()).stream()
                .map(s -> new TimetableEntry(s.getRoomAllocation().getExamDate(), s.getExamSlot().getStartTime(),
                        s.getExamSlot().getEndTime(), s.getExamSlot().getSubject().getCode(),
                        s.getExamSlot().getSubject().getName(), s.getRoomAllocation().getRoom().getCode(),
                        s.getRoomAllocation().getRoom().getBuilding(), s.getRoomAllocation().getRoom().getFloor(),
                        s.getSeatNo()))
                .toList();
        return new Timetable(me.getRegNo(), me.getName(), exams);
    }
}
