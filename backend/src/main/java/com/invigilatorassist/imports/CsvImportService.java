package com.invigilatorassist.imports;

import com.invigilatorassist.domain.AppUser;
import com.invigilatorassist.domain.Department;
import com.invigilatorassist.domain.Faculty;
import com.invigilatorassist.domain.Role;
import com.invigilatorassist.domain.Room;
import com.invigilatorassist.domain.Student;
import com.invigilatorassist.domain.Subject;
import com.invigilatorassist.repository.AppUserRepository;
import com.invigilatorassist.repository.DepartmentRepository;
import com.invigilatorassist.repository.FacultyRepository;
import com.invigilatorassist.repository.RoomRepository;
import com.invigilatorassist.repository.StudentRepository;
import com.invigilatorassist.repository.SubjectRepository;
import com.invigilatorassist.security.AuthController;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bulk upsert of reference data from CSV. Each file is all-or-nothing: if any row is invalid, nothing is saved
 * and every bad row is reported. Existing rows (matched by their code / employee number / registration number)
 * are updated; passwords of existing users are never touched.
 */
@Service
public class CsvImportService {

    public enum Kind { DEPARTMENTS, ROOMS, FACULTY, SUBJECTS, STUDENTS, BACKLOGS }

    public record NewCredential(String username, String fullName, String temporaryPassword) {}

    public record ImportResult(Kind kind, int created, int updated, List<NewCredential> newCredentials) {}

    static final Map<Kind, List<String>> REQUIRED_COLUMNS = Map.of(
            Kind.DEPARTMENTS, List.of("code", "name"),
            Kind.ROOMS, List.of("code", "capacity"),
            Kind.FACULTY, List.of("employee_no", "full_name", "email", "designation", "max_duties"),
            Kind.SUBJECTS, List.of("code", "name", "semester"),
            Kind.STUDENTS, List.of("reg_no", "name", "semester"),
            Kind.BACKLOGS, List.of("reg_no", "subject_code"));

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true)
            .setIgnoreEmptyLines(true).build();

    private static final char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    private final DepartmentRepository departments;
    private final RoomRepository rooms;
    private final AppUserRepository users;
    private final FacultyRepository faculty;
    private final SubjectRepository subjects;
    private final StudentRepository students;
    private final PasswordEncoder passwords;

    public CsvImportService(DepartmentRepository departments, RoomRepository rooms, AppUserRepository users,
                            FacultyRepository faculty, SubjectRepository subjects, StudentRepository students,
                            PasswordEncoder passwords) {
        this.departments = departments;
        this.rooms = rooms;
        this.users = users;
        this.faculty = faculty;
        this.subjects = subjects;
        this.students = students;
        this.passwords = passwords;
    }

    @Transactional
    public ImportResult importCsv(long institutionId, Kind kind, InputStream csv) {
        Rows rows = parse(csv, kind);
        Counter counter = new Counter();
        List<String> errors = new ArrayList<>();
        for (CSVRecord r : rows.records()) {
            try {
                switch (kind) {
                    case DEPARTMENTS -> department(institutionId, r, counter);
                    case ROOMS -> room(institutionId, r, counter);
                    case FACULTY -> faculty(institutionId, r, counter);
                    case SUBJECTS -> subject(institutionId, r, counter);
                    case STUDENTS -> student(institutionId, r, counter);
                    case BACKLOGS -> backlog(institutionId, r, counter);
                }
            } catch (IllegalArgumentException e) {
                errors.add("Row " + (r.getRecordNumber() + 1) + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new ImportFailedException(errors);   // rolls back the whole file
        }
        return new ImportResult(kind, counter.created, counter.updated, List.copyOf(counter.credentials));
    }

    // ---- one method per file type -------------------------------------------------------------------

    private void department(long inst, CSVRecord r, Counter c) {
        String code = upper(req(r, "code"));
        departments.findByInstitutionIdAndCode(inst, code).ifPresentOrElse(
                d -> { d.setName(req(r, "name")); c.updated++; },
                () -> { departments.save(new Department(inst, code, req(r, "name"))); c.created++; });
    }

    private void room(long inst, CSVRecord r, Counter c) {
        String code = upper(req(r, "code"));
        int capacity = positiveInt(r, "capacity");
        Integer floor = opt(r, "floor") == null ? null : intVal(r, "floor");
        boolean accessible = bool(r, "accessible");
        rooms.findByInstitutionIdAndCode(inst, code).ifPresentOrElse(
                room -> { room.update(opt(r, "building"), floor, capacity, accessible); c.updated++; },
                () -> { rooms.save(new Room(inst, code, opt(r, "building"), floor, capacity, accessible)); c.created++; });
    }

    private void faculty(long inst, CSVRecord r, Counter c) {
        String empNo = upper(req(r, "employee_no"));
        String email = req(r, "email").toLowerCase(Locale.ROOT);
        if (!email.contains("@")) throw new IllegalArgumentException("email '" + email + "' is not valid");
        Department dept = optDepartment(inst, r, "department_code");
        int maxDuties = nonNegativeInt(r, "max_duties");
        var existing = faculty.findByInstitutionIdAndEmployeeNo(inst, empNo);
        if (existing.isPresent()) {
            Faculty f = existing.get();
            f.setDepartment(dept);
            f.setDesignation(req(r, "designation"));
            f.setMaxDuties(maxDuties);
            f.getUser().setFullName(req(r, "full_name"));
            f.getUser().setEmail(email);
            c.updated++;
            return;
        }
        AppUser user = newUser(inst, email, req(r, "full_name"), email, Role.FACULTY, c);
        faculty.save(new Faculty(inst, user, empNo, dept, req(r, "designation"), maxDuties));
        c.created++;
    }

    private void subject(long inst, CSVRecord r, Counter c) {
        String code = upper(req(r, "code"));
        int semester = semester(r);
        Department dept = optDepartment(inst, r, "department_code");
        Faculty instructor = null;
        String instructorNo = opt(r, "instructor_employee_no");
        if (instructorNo != null) {
            instructor = faculty.findByInstitutionIdAndEmployeeNo(inst, upper(instructorNo))
                    .orElseThrow(() -> new IllegalArgumentException("unknown instructor_employee_no " + instructorNo));
        }
        Faculty finalInstructor = instructor;
        boolean elective = bool(r, "elective");
        subjects.findByInstitutionIdAndCode(inst, code).ifPresentOrElse(
                s -> { s.update(req(r, "name"), semester, dept, finalInstructor, elective); c.updated++; },
                () -> { subjects.save(new Subject(inst, code, req(r, "name"), semester, dept, finalInstructor, elective)); c.created++; });
    }

    private void student(long inst, CSVRecord r, Counter c) {
        String regNo = upper(req(r, "reg_no"));
        int semester = semester(r);
        Department dept = optDepartment(inst, r, "department_code");
        boolean accessible = bool(r, "needs_accessible_room");
        var existing = students.findByInstitutionIdAndRegNo(inst, regNo);
        if (existing.isPresent()) {
            existing.get().update(req(r, "name"), opt(r, "section"), semester, dept, accessible);
            c.updated++;
            return;
        }
        AppUser user = newUser(inst, regNo, req(r, "name"), opt(r, "email"), Role.STUDENT, c);
        students.save(new Student(inst, user, regNo, req(r, "name"), opt(r, "section"), semester, dept, accessible));
        c.created++;
    }

    /** backlogs.csv lists every extra enrollment: re-take papers and chosen electives. */
    private void backlog(long inst, CSVRecord r, Counter c) {
        String regNo = upper(req(r, "reg_no"));
        String code = upper(req(r, "subject_code"));
        Student s = students.findByInstitutionIdAndRegNo(inst, regNo)
                .orElseThrow(() -> new IllegalArgumentException("unknown reg_no " + regNo));
        Subject subject = subjects.findByInstitutionIdAndCode(inst, code)
                .orElseThrow(() -> new IllegalArgumentException("unknown subject_code " + code));
        if (s.getBacklogSubjects().add(subject)) c.created++; else c.updated++;
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private AppUser newUser(long inst, String username, String fullName, String email, Role role, Counter c) {
        String normalised = AuthController.normalise(username);
        if (users.findByInstitutionIdAndUsername(inst, normalised).isPresent()) {
            throw new IllegalArgumentException("a user named " + normalised + " already exists");
        }
        String temp = temporaryPassword();
        AppUser user = users.save(new AppUser(inst, normalised, passwords.encode(temp), fullName, email, role));
        c.credentials.add(new NewCredential(normalised, fullName, temp));
        return user;
    }

    private String temporaryPassword() {
        char[] out = new char[10];
        for (int i = 0; i < out.length; i++) out[i] = PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.length)];
        return new String(out);
    }

    private Department optDepartment(long inst, CSVRecord r, String col) {
        String code = opt(r, col);
        if (code == null) return null;
        return departments.findByInstitutionIdAndCode(inst, upper(code))
                .orElseThrow(() -> new IllegalArgumentException("unknown " + col + " " + code));
    }

    private record Rows(List<CSVRecord> records) {}

    private static final class Counter {
        int created;
        int updated;
        final List<NewCredential> credentials = new ArrayList<>();
    }

    private static Rows parse(InputStream csv, Kind kind) {
        try (CSVParser parser = CSVParser.parse(new InputStreamReader(csv, StandardCharsets.UTF_8), FORMAT)) {
            List<String> headers = parser.getHeaderNames().stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
            List<String> missing = REQUIRED_COLUMNS.get(kind).stream().filter(col -> !headers.contains(col)).toList();
            if (!missing.isEmpty()) {
                throw new ImportFailedException(List.of("Missing column(s): " + String.join(", ", missing)
                        + ". Expected at least: " + String.join(", ", REQUIRED_COLUMNS.get(kind))));
            }
            return new Rows(parser.getRecords());
        } catch (IOException | IllegalArgumentException e) {
            throw new ImportFailedException(List.of("Could not read CSV: " + e.getMessage()));
        }
    }

    static String opt(CSVRecord r, String col) {
        if (!r.isMapped(col) || !r.isSet(col)) return null;
        String v = r.get(col);
        return v == null || v.isBlank() ? null : v.trim();
    }

    static String req(CSVRecord r, String col) {
        String v = opt(r, col);
        if (v == null) throw new IllegalArgumentException(col + " is required");
        return v;
    }

    private static String upper(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    private static int intVal(CSVRecord r, String col) {
        try {
            return Integer.parseInt(req(r, col));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(col + " must be a whole number");
        }
    }

    private static int positiveInt(CSVRecord r, String col) {
        int v = intVal(r, col);
        if (v <= 0) throw new IllegalArgumentException(col + " must be greater than 0");
        return v;
    }

    private static int nonNegativeInt(CSVRecord r, String col) {
        int v = intVal(r, col);
        if (v < 0) throw new IllegalArgumentException(col + " cannot be negative");
        return v;
    }

    private static int semester(CSVRecord r) {
        int v = intVal(r, "semester");
        if (v < 1 || v > 12) throw new IllegalArgumentException("semester must be between 1 and 12");
        return v;
    }

    private static boolean bool(CSVRecord r, String col) {
        String v = opt(r, col);
        if (v == null) return false;
        return Arrays.asList("true", "yes", "y", "1").contains(v.toLowerCase(Locale.ROOT));
    }
}
