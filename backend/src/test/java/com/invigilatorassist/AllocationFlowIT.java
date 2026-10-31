package com.invigilatorassist;

import com.invigilatorassist.domain.ExamSlot;
import com.invigilatorassist.domain.Faculty;
import com.invigilatorassist.domain.Institution;
import com.invigilatorassist.domain.InvigilationDuty;
import com.invigilatorassist.exams.ExamDtos.RoomView;
import com.invigilatorassist.exams.ExamDtos.SeatView;
import com.invigilatorassist.exams.ExamDtos.SittingView;
import com.invigilatorassist.exams.SittingAllocationService;
import com.invigilatorassist.imports.CsvImportService;
import com.invigilatorassist.imports.CsvImportService.Kind;
import com.invigilatorassist.repository.ExamSlotRepository;
import com.invigilatorassist.repository.FacultyRepository;
import com.invigilatorassist.repository.InstitutionRepository;
import com.invigilatorassist.repository.InvigilationDutyRepository;
import com.invigilatorassist.repository.StudentRepository;
import com.invigilatorassist.repository.SubjectRepository;
import com.invigilatorassist.swaps.SwapService;
import com.invigilatorassist.swaps.SwapService.DutyView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end against a real PostgreSQL (needs Docker): import the demo CSVs, schedule two sittings,
 * allocate both, then swap two duties and check the invigilators really changed.
 */
@SpringBootTest(properties = "app.jwt.secret=integration-test-secret-0123456789abcdef")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AllocationFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Path DEMO = Path.of("..", "samples", "demo");
    private static final LocalDate DAY = LocalDate.of(2030, 1, 10);
    private static final LocalTime MORNING = LocalTime.of(9, 30);
    private static final LocalTime AFTERNOON = LocalTime.of(14, 0);

    @Autowired InstitutionRepository institutions;
    @Autowired CsvImportService imports;
    @Autowired SubjectRepository subjects;
    @Autowired StudentRepository students;
    @Autowired ExamSlotRepository slots;
    @Autowired SittingAllocationService allocation;
    @Autowired FacultyRepository faculty;
    @Autowired InvigilationDutyRepository duties;
    @Autowired SwapService swaps;
    @Autowired TransactionTemplate tx;

    long inst;

    @BeforeAll
    void importDemoData() throws Exception {
        inst = institutions.save(new Institution("ITEST", "Integration Test College")).getId();
        for (Kind kind : List.of(Kind.DEPARTMENTS, Kind.ROOMS, Kind.FACULTY, Kind.SUBJECTS, Kind.STUDENTS, Kind.BACKLOGS)) {
            try (InputStream in = Files.newInputStream(DEMO.resolve(kind.name().toLowerCase() + ".csv"))) {
                imports.importCsv(inst, kind, in);
            }
        }
        // Common subjects (MA301, HS501) and the elective get their own sittings in real life: a student can
        // write only one paper per sitting, and the service rejects a schedule that breaks that.
        schedule(MORNING, "CSE301", "ECE301", "ME301");
        schedule(AFTERNOON, "CSE501", "ECE501", "ME501");
    }

    @Test
    void allocatesBothSittingsAndSwapsADuty() {
        SittingView morning = allocation.allocate(inst, DAY, MORNING);
        SittingView afternoon = allocation.allocate(inst, DAY, AFTERNOON);

        for (SittingView s : List.of(morning, afternoon)) {
            assertThat(s.studentsSeated()).isPositive();
            for (RoomView room : s.rooms()) {
                assertThat(room.invigilators()).isNotEmpty();
                assertThat(room.seats().size()).isLessThanOrEqualTo(room.capacity());
                if (room.seats().size() > 1) {
                    assertThat(room.studentsPerSubject().size()).isGreaterThan(1);   // subjects are mixed
                }
                List<SeatView> seats = room.seats();
                for (int i = 1; i < seats.size(); i++) {
                    assertThat(seats.get(i).subjectCode()).isNotEqualTo(seats.get(i - 1).subjectCode());
                }
            }
        }
        // Morning = every semester-3 student plus the 12 semester-5 students re-taking a 301 paper.
        assertThat(morning.studentsSeated()).isEqualTo(students.findAllByInstitutionIdAndSemester(inst, 3).size() + 12);
        assertThat(afternoon.studentsSeated()).isEqualTo(students.findAllByInstitutionIdAndSemester(inst, 5).size());

        // Swap: find a faculty member with a morning duty who has a valid afternoon partner.
        tx.executeWithoutResult(status -> {
            for (Faculty f : faculty.findAllByInstitutionIdOrderByEmployeeNo(inst)) {
                List<InvigilationDuty> mine = duties.findUpcomingForFaculty(f.getId(), DAY);
                if (mine.size() != 1) continue;
                List<DutyView> options = swaps.candidates(f.getUser().getId(), inst, mine.get(0).getId());
                if (options.isEmpty()) continue;

                DutyView target = options.get(0);
                Faculty other = duties.findById(target.dutyId()).orElseThrow().getFaculty();
                var request = swaps.request(f.getUser().getId(), inst, mine.get(0).getId(), target.dutyId(), "clinic");
                swaps.accept(other.getUser().getId(), request.id());

                assertThat(duties.findById(mine.get(0).getId()).orElseThrow().getFaculty().getId()).isEqualTo(other.getId());
                assertThat(duties.findById(target.dutyId()).orElseThrow().getFaculty().getId()).isEqualTo(f.getId());
                status.setRollbackOnly();
                return;
            }
            throw new AssertionError("No swappable pair found in the demo data");
        });
    }

    private void schedule(LocalTime start, String... codes) {
        for (String code : codes) {
            var subject = subjects.findByInstitutionIdAndCode(inst, code).orElseThrow();
            slots.save(new ExamSlot(inst, subject, DAY, start, start.plusHours(3)));
        }
    }
}
