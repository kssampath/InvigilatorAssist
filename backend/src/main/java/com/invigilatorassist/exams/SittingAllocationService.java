package com.invigilatorassist.exams;

import com.invigilatorassist.allocation.engine.AllocationException;
import com.invigilatorassist.allocation.engine.InvigilatorPlanner;
import com.invigilatorassist.allocation.engine.InvigilatorPlanner.FacultyCandidate;
import com.invigilatorassist.allocation.engine.InvigilatorPlanner.RoomNeed;
import com.invigilatorassist.allocation.engine.SeatingPlanner;
import com.invigilatorassist.allocation.engine.SeatingPlanner.Candidate;
import com.invigilatorassist.allocation.engine.SeatingPlanner.RoomPlan;
import com.invigilatorassist.allocation.engine.SeatingPlanner.RoomSpec;
import com.invigilatorassist.common.ApiException;
import com.invigilatorassist.config.AppProperties;
import com.invigilatorassist.domain.ExamSlot;
import com.invigilatorassist.domain.Faculty;
import com.invigilatorassist.domain.InvigilationDuty;
import com.invigilatorassist.domain.Room;
import com.invigilatorassist.domain.RoomAllocation;
import com.invigilatorassist.domain.SeatAllocation;
import com.invigilatorassist.domain.Student;
import com.invigilatorassist.domain.Subject;
import com.invigilatorassist.exams.ExamDtos.RoomView;
import com.invigilatorassist.exams.ExamDtos.SeatView;
import com.invigilatorassist.exams.ExamDtos.SittingView;
import com.invigilatorassist.repository.ExamSlotRepository;
import com.invigilatorassist.repository.FacultyRepository;
import com.invigilatorassist.repository.FacultyUnavailabilityRepository;
import com.invigilatorassist.repository.InvigilationDutyRepository;
import com.invigilatorassist.repository.RoomAllocationRepository;
import com.invigilatorassist.repository.RoomRepository;
import com.invigilatorassist.repository.SeatAllocationRepository;
import com.invigilatorassist.repository.StudentRepository;
import com.invigilatorassist.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Allocates one sitting (every exam of the institution starting at the same date and time) in a single
 * transaction: seats students with subjects mixed, then assigns invigilators fairly. Either everything is
 * saved or nothing is.
 */
@Service
public class SittingAllocationService {

    private final ExamSlotRepository slots;
    private final StudentRepository students;
    private final RoomRepository rooms;
    private final RoomAllocationRepository roomAllocations;
    private final FacultyRepository faculty;
    private final FacultyUnavailabilityRepository unavailability;
    private final InvigilationDutyRepository duties;
    private final SeatAllocationRepository seats;
    private final SubjectRepository subjects;
    private final AppProperties props;
    private final SeatingPlanner seatingPlanner = new SeatingPlanner();
    private final InvigilatorPlanner invigilatorPlanner = new InvigilatorPlanner();

    public SittingAllocationService(ExamSlotRepository slots, StudentRepository students, RoomRepository rooms,
                                    RoomAllocationRepository roomAllocations, FacultyRepository faculty,
                                    FacultyUnavailabilityRepository unavailability, InvigilationDutyRepository duties,
                                    SeatAllocationRepository seats, SubjectRepository subjects, AppProperties props) {
        this.slots = slots;
        this.students = students;
        this.rooms = rooms;
        this.roomAllocations = roomAllocations;
        this.faculty = faculty;
        this.unavailability = unavailability;
        this.duties = duties;
        this.seats = seats;
        this.subjects = subjects;
        this.props = props;
    }

    @Transactional
    public SittingView allocate(long inst, LocalDate date, LocalTime start) {
        List<ExamSlot> sittingSlots = slots.findAllByInstitutionIdAndExamDateAndStartTime(inst, date, start).stream()
                .filter(s -> s.getStatus() == ExamSlot.Status.SCHEDULED)
                .toList();
        if (sittingSlots.isEmpty()) {
            throw ApiException.notFound("Scheduled exams on " + date + " at " + start);
        }
        if (roomAllocations.existsByInstitutionIdAndExamDateAndStartTime(inst, date, start)) {
            throw ApiException.conflict("This sitting is already allocated. Clear it first to re-allocate.");
        }
        LocalTime end = sittingSlots.stream().map(ExamSlot::getEndTime).max(Comparator.naturalOrder()).orElseThrow();

        // 1. Who writes what. A student may write only one exam per sitting.
        Map<Long, Student> studentById = new HashMap<>();
        Map<Long, ExamSlot> slotById = new HashMap<>();
        Map<Long, String> slotOfStudent = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        List<String> clashes = new ArrayList<>();
        for (ExamSlot slot : sittingSlots) {
            slotById.put(slot.getId(), slot);
            for (Student st : studentsFor(inst, slot.getSubject())) {
                String code = slot.getSubject().getCode();
                String previous = slotOfStudent.putIfAbsent(st.getId(), code);
                if (previous != null) {
                    if (!previous.equals(code)) clashes.add(st.getRegNo() + " (" + previous + " and " + code + ")");
                    continue;
                }
                studentById.put(st.getId(), st);
                candidates.add(new Candidate(st.getId(), slot.getId(), code, st.isNeedsAccessibleRoom()));
            }
        }
        if (!clashes.isEmpty()) {
            throw new AllocationException("Students with two exams in this sitting: " + String.join(", ",
                    clashes.subList(0, Math.min(20, clashes.size()))) + (clashes.size() > 20 ? " …" : ""));
        }
        if (candidates.isEmpty()) {
            throw new AllocationException("No students are registered for the exams in this sitting");
        }

        // 2. Seating, using rooms not taken by an overlapping sitting.
        Set<Long> busyRooms = roomAllocations.findBusyRoomIds(inst, date, start, end);
        Map<Long, Room> roomById = rooms.findAllByInstitutionIdAndActiveTrue(inst).stream()
                .filter(r -> !busyRooms.contains(r.getId()))
                .collect(Collectors.toMap(Room::getId, Function.identity()));
        List<RoomSpec> roomSpecs = roomById.values().stream()
                .map(r -> new RoomSpec(r.getId(), r.getCode(), r.getCapacity(), r.isAccessible()))
                .toList();
        SeatingPlanner.SeatingPlan seating = seatingPlanner.plan(candidates, roomSpecs);

        // 3. Invigilators: lock faculty rows, drop anyone on leave or already busy at this time.
        List<Faculty> allFaculty = faculty.lockAllForInstitution(inst);
        Set<Long> unavailable = unavailability.findFacultyIdsUnavailableOn(inst, date);
        Set<Long> busy = duties.findBusyFacultyIds(inst, date, start, end);
        Map<Long, Set<String>> teaches = new HashMap<>();
        for (Subject s : subjects.findAllByInstitutionIdOrderByCode(inst)) {
            if (s.getInstructor() != null) {
                teaches.computeIfAbsent(s.getInstructor().getId(), k -> new HashSet<>()).add(s.getCode());
            }
        }
        Map<Long, Faculty> facultyById = new HashMap<>();
        List<FacultyCandidate> facultyCandidates = new ArrayList<>();
        for (Faculty f : allFaculty) {
            if (unavailable.contains(f.getId()) || busy.contains(f.getId())) continue;
            facultyById.put(f.getId(), f);
            facultyCandidates.add(new FacultyCandidate(f.getId(),
                    f.getDepartment() == null ? null : f.getDepartment().getId(),
                    f.getDutyCount(), f.getMaxDuties(), f.getLastDutyAt(),
                    teaches.getOrDefault(f.getId(), Set.of())));
        }
        int perInvigilator = props.allocation().studentsPerInvigilator();
        List<RoomNeed> needs = seating.rooms().stream()
                .map(rp -> new RoomNeed(rp.room().roomId(), rp.room().code(),
                        InvigilatorPlanner.invigilatorsFor(rp.seats().size(), perInvigilator),
                        Set.copyOf(rp.groupKeys())))
                .toList();
        List<InvigilatorPlanner.Assignment> assignments = invigilatorPlanner.plan(needs, facultyCandidates);

        // 4. Persist.
        LocalDateTime dutyTime = LocalDateTime.of(date, start);
        Map<Long, List<Long>> facultyPerRoom = new HashMap<>();
        assignments.forEach(a -> facultyPerRoom.computeIfAbsent(a.roomId(), k -> new ArrayList<>()).add(a.facultyId()));
        List<RoomAllocation> saved = new ArrayList<>();
        for (RoomPlan rp : seating.rooms()) {
            RoomAllocation ra = new RoomAllocation(inst, date, start, end, roomById.get(rp.room().roomId()));
            rp.seats().forEach(seat -> ra.addSeat(slotById.get(seat.candidate().examSlotId()),
                    studentById.get(seat.candidate().studentId()), seat.seatNo()));
            for (Long facultyId : facultyPerRoom.getOrDefault(rp.room().roomId(), List.of())) {
                Faculty f = facultyById.get(facultyId);
                ra.addDuty(f, InvigilationDuty.DutyRole.INVIGILATOR);
                f.recordDuty(dutyTime);
            }
            saved.add(ra);
        }
        roomAllocations.saveAll(saved);
        sittingSlots.forEach(s -> s.setStatus(ExamSlot.Status.ALLOCATED));
        roomAllocations.flush();
        return view(inst, date, start);
    }

    /** Removes a sitting's allocation and gives the faculty their quota back. */
    @Transactional
    public void clear(long inst, LocalDate date, LocalTime start) {
        List<RoomAllocation> existing = roomAllocations.findAllByInstitutionIdAndExamDateAndStartTimeOrderById(inst, date, start);
        if (existing.isEmpty()) throw ApiException.notFound("Allocation for " + date + " at " + start);
        faculty.lockAllForInstitution(inst);
        for (RoomAllocation ra : existing) {
            for (InvigilationDuty d : ra.getDuties()) {
                d.getFaculty().undoDuty();
            }
        }
        roomAllocations.deleteAll(existing);
        slots.findAllByInstitutionIdAndExamDateAndStartTime(inst, date, start).stream()
                .filter(s -> s.getStatus() == ExamSlot.Status.ALLOCATED)
                .forEach(s -> s.setStatus(ExamSlot.Status.SCHEDULED));
    }

    @Transactional(readOnly = true)
    public SittingView view(long inst, LocalDate date, LocalTime start) {
        List<RoomAllocation> ras = roomAllocations.findAllByInstitutionIdAndExamDateAndStartTimeOrderById(inst, date, start);
        if (ras.isEmpty()) throw ApiException.notFound("Allocation for " + date + " at " + start);
        List<Long> ids = ras.stream().map(RoomAllocation::getId).toList();

        Map<Long, List<SeatAllocation>> seatsByRoom = seats.findAllForRoomAllocations(ids).stream()
                .collect(Collectors.groupingBy(s -> s.getRoomAllocation().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<String>> invigilatorsByRoom = duties.findAllForRoomAllocations(ids).stream()
                .collect(Collectors.groupingBy(d -> d.getRoomAllocation().getId(),
                        Collectors.mapping(d -> d.getFaculty().getUser().getFullName(), Collectors.toList())));

        List<RoomView> roomViews = new ArrayList<>();
        int seated = 0;
        int invigilators = 0;
        for (RoomAllocation ra : ras) {
            List<SeatAllocation> roomSeats = seatsByRoom.getOrDefault(ra.getId(), List.of());
            List<String> names = invigilatorsByRoom.getOrDefault(ra.getId(), List.of());
            Map<String, Long> perSubject = roomSeats.stream().collect(Collectors.groupingBy(
                    s -> s.getExamSlot().getSubject().getCode(), TreeMap::new, Collectors.counting()));
            List<SeatView> seatViews = roomSeats.stream()
                    .map(s -> new SeatView(s.getSeatNo(), s.getStudent().getRegNo(), s.getStudent().getName(),
                            s.getExamSlot().getSubject().getCode()))
                    .toList();
            roomViews.add(new RoomView(ra.getId(), ra.getRoom().getCode(), ra.getRoom().getBuilding(),
                    ra.getRoom().getCapacity(), names, perSubject, seatViews));
            seated += roomSeats.size();
            invigilators += names.size();
        }
        return new SittingView(date, start, ras.get(0).getEndTime(), seated, ras.size(), invigilators, roomViews);
    }

    private List<Student> studentsFor(long inst, Subject subject) {
        List<Student> regular = subject.isElective() ? List.of() : subject.getDepartment() == null
                ? students.findAllByInstitutionIdAndSemester(inst, subject.getSemester())
                : students.findAllByInstitutionIdAndSemesterAndDepartmentId(inst, subject.getSemester(),
                subject.getDepartment().getId());
        List<Student> all = new ArrayList<>(regular);
        Set<Long> seen = regular.stream().map(Student::getId).collect(Collectors.toSet());
        for (Student b : students.findBacklogStudents(subject.getId())) {
            if (seen.add(b.getId())) all.add(b);
        }
        all.sort(Comparator.comparing(Student::getRegNo));
        return all;
    }
}
