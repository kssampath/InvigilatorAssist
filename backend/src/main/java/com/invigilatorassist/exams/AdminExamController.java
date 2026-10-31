package com.invigilatorassist.exams;

import com.invigilatorassist.common.ApiException;
import com.invigilatorassist.domain.ExamSlot;
import com.invigilatorassist.domain.Faculty;
import com.invigilatorassist.domain.Subject;
import com.invigilatorassist.exams.ExamDtos.CreateExamSlot;
import com.invigilatorassist.exams.ExamDtos.ExamSlotView;
import com.invigilatorassist.exams.ExamDtos.FacultyLoadView;
import com.invigilatorassist.exams.ExamDtos.RoomRow;
import com.invigilatorassist.exams.ExamDtos.SittingKey;
import com.invigilatorassist.exams.ExamDtos.SittingSummary;
import com.invigilatorassist.exams.ExamDtos.SittingView;
import com.invigilatorassist.exams.ExamDtos.SubjectRow;
import com.invigilatorassist.repository.ExamSlotRepository;
import com.invigilatorassist.repository.FacultyRepository;
import com.invigilatorassist.repository.RoomRepository;
import com.invigilatorassist.repository.SubjectRepository;
import com.invigilatorassist.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminExamController {

    private final ExamSlotRepository slots;
    private final SubjectRepository subjects;
    private final RoomRepository rooms;
    private final FacultyRepository faculty;
    private final SittingAllocationService allocation;

    public AdminExamController(ExamSlotRepository slots, SubjectRepository subjects, RoomRepository rooms,
                               FacultyRepository faculty, SittingAllocationService allocation) {
        this.slots = slots;
        this.subjects = subjects;
        this.rooms = rooms;
        this.faculty = faculty;
        this.allocation = allocation;
    }

    // ---- exam timetable ---------------------------------------------------------------------------

    @PostMapping("/exam-slots")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ExamSlotView createSlot(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateExamSlot req) {
        long inst = CurrentUser.of(jwt).institutionId();
        if (!req.endTime().isAfter(req.startTime())) throw ApiException.badRequest("End time must be after start time");
        Subject subject = subjects.findByInstitutionIdAndCode(inst, req.subjectCode().trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Subject " + req.subjectCode()));
        try {
            ExamSlot slot = slots.saveAndFlush(new ExamSlot(inst, subject, req.date(), req.startTime(), req.endTime()));
            return toView(slot);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(subject.getCode() + " already has an exam at that date and time");
        }
    }

    @GetMapping("/exam-slots")
    @Transactional(readOnly = true)
    public List<ExamSlotView> listSlots(@AuthenticationPrincipal Jwt jwt) {
        return slots.findAllByInstitutionIdOrderByExamDateAscStartTimeAsc(CurrentUser.of(jwt).institutionId())
                .stream().map(AdminExamController::toView).toList();
    }

    /** Exams grouped into sittings (same date and start time), with whether each is allocated yet. */
    @GetMapping("/sittings")
    @Transactional(readOnly = true)
    public List<SittingSummary> listSittings(@AuthenticationPrincipal Jwt jwt) {
        Map<String, List<ExamSlot>> grouped = new LinkedHashMap<>();
        for (ExamSlot s : slots.findAllByInstitutionIdOrderByExamDateAscStartTimeAsc(CurrentUser.of(jwt).institutionId())) {
            if (s.getStatus() == ExamSlot.Status.CANCELLED) continue;
            grouped.computeIfAbsent(s.getExamDate() + "T" + s.getStartTime(), k -> new ArrayList<>()).add(s);
        }
        return grouped.values().stream().map(list -> new SittingSummary(
                list.get(0).getExamDate(), list.get(0).getStartTime(),
                list.stream().map(s -> s.getSubject().getCode()).sorted().toList(),
                list.stream().allMatch(s -> s.getStatus() != ExamSlot.Status.SCHEDULED))).toList();
    }

    // ---- allocation -------------------------------------------------------------------------------

    @PostMapping("/sittings/allocate")
    public SittingView allocate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SittingKey key) {
        return allocation.allocate(CurrentUser.of(jwt).institutionId(), key.date(), key.startTime());
    }

    @GetMapping("/sittings/allocation")
    public SittingView view(@AuthenticationPrincipal Jwt jwt,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime) {
        return allocation.view(CurrentUser.of(jwt).institutionId(), date, startTime);
    }

    @DeleteMapping("/sittings/allocation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime) {
        allocation.clear(CurrentUser.of(jwt).institutionId(), date, startTime);
    }

    // ---- reference data and fairness view ---------------------------------------------------------

    @GetMapping("/rooms")
    @Transactional(readOnly = true)
    public List<RoomRow> rooms(@AuthenticationPrincipal Jwt jwt) {
        return rooms.findAllByInstitutionIdOrderByCode(CurrentUser.of(jwt).institutionId()).stream()
                .map(r -> new RoomRow(r.getId(), r.getCode(), r.getBuilding(), r.getFloor(), r.getCapacity(),
                        r.isAccessible(), r.isActive()))
                .toList();
    }

    @GetMapping("/subjects")
    @Transactional(readOnly = true)
    public List<SubjectRow> subjects(@AuthenticationPrincipal Jwt jwt) {
        return subjects.findAllByInstitutionIdOrderByCode(CurrentUser.of(jwt).institutionId()).stream()
                .map(s -> new SubjectRow(s.getId(), s.getCode(), s.getName(), s.getSemester(),
                        s.getDepartment() == null ? null : s.getDepartment().getCode(),
                        s.getInstructor() == null ? null : s.getInstructor().getEmployeeNo(), s.isElective()))
                .toList();
    }

    /** Duty load per faculty member, most loaded first: the fairness dashboard. */
    @GetMapping("/faculty")
    @Transactional(readOnly = true)
    public List<FacultyLoadView> facultyLoad(@AuthenticationPrincipal Jwt jwt) {
        return faculty.findAllByInstitutionIdOrderByEmployeeNo(CurrentUser.of(jwt).institutionId()).stream()
                .sorted(Comparator.comparingDouble(AdminExamController::loadRatio).reversed())
                .map(f -> new FacultyLoadView(f.getId(), f.getEmployeeNo(), f.getUser().getFullName(),
                        f.getDepartment() == null ? null : f.getDepartment().getCode(), f.getDesignation(),
                        f.getDutyCount(), f.getMaxDuties()))
                .toList();
    }

    private static double loadRatio(Faculty f) {
        return f.getMaxDuties() == 0 ? 0 : (double) f.getDutyCount() / f.getMaxDuties();
    }

    static ExamSlotView toView(ExamSlot s) {
        return new ExamSlotView(s.getId(), s.getSubject().getCode(), s.getSubject().getName(),
                s.getSubject().getSemester(), s.getExamDate(), s.getStartTime(), s.getEndTime(), s.getStatus().name());
    }
}
