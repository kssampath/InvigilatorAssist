package com.invigilatorassist.allocation.engine;

import com.invigilatorassist.allocation.engine.InvigilatorPlanner.Assignment;
import com.invigilatorassist.allocation.engine.InvigilatorPlanner.FacultyCandidate;
import com.invigilatorassist.allocation.engine.InvigilatorPlanner.RoomNeed;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvigilatorPlannerTest {

    private final InvigilatorPlanner planner = new InvigilatorPlanner();

    private static FacultyCandidate faculty(long id, long dept, int duties, int max, Set<String> teaches) {
        return new FacultyCandidate(id, dept, duties, max, null, teaches);
    }

    private static RoomNeed room(long id, int needed, String... subjects) {
        return new RoomNeed(id, "R" + id, needed, Set.of(subjects));
    }

    @Test
    void leastLoadedFacultyGoFirst() {
        List<FacultyCandidate> f = List.of(
                faculty(1, 10, 4, 5, Set.of()),   // 80% used
                faculty(2, 10, 1, 5, Set.of()),   // 20% used
                faculty(3, 10, 3, 10, Set.of())); // 30% used

        List<Assignment> a = planner.plan(List.of(room(100, 2, "CS501")), f);

        assertEquals(List.of(2L, 3L), a.stream().map(Assignment::facultyId).toList());
    }

    @Test
    void instructorNeverInvigilatesTheirOwnSubject() {
        List<FacultyCandidate> f = List.of(
                faculty(1, 10, 0, 5, Set.of("CS501")),
                faculty(2, 10, 3, 5, Set.of()));

        List<Assignment> a = planner.plan(List.of(room(100, 1, "CS501", "EC502")), f);

        assertEquals(2L, a.get(0).facultyId());
    }

    @Test
    void quotaIsRespected() {
        List<FacultyCandidate> f = List.of(faculty(1, 10, 5, 5, Set.of()), faculty(2, 10, 0, 0, Set.of()));
        assertThrows(AllocationException.class, () -> planner.plan(List.of(room(100, 1, "CS501")), f));
    }

    @Test
    void nobodyGetsTwoRoomsInTheSameSitting() {
        List<FacultyCandidate> f = List.of(faculty(1, 10, 0, 9, Set.of()), faculty(2, 10, 0, 9, Set.of()),
                faculty(3, 10, 0, 9, Set.of()));

        List<Assignment> a = planner.plan(List.of(room(100, 1, "A"), room(101, 1, "B"), room(102, 1, "C")), f);

        Set<Long> ids = new HashSet<>();
        a.forEach(x -> assertTrue(ids.add(x.facultyId())));
        assertEquals(3, ids.size());
    }

    @Test
    void tiesAreSpreadAcrossDepartments() {
        List<FacultyCandidate> f = List.of(
                faculty(1, 10, 0, 5, Set.of()),
                faculty(2, 10, 0, 5, Set.of()),
                faculty(3, 20, 0, 5, Set.of()));

        List<Assignment> a = planner.plan(List.of(room(100, 2, "CS501")), f);

        assertEquals(1L, a.get(0).facultyId());
        assertEquals(3L, a.get(1).facultyId(), "second pick comes from the other department");
    }

    @Test
    void longestRestedGoesFirstOnEqualLoad() {
        List<FacultyCandidate> f = List.of(
                new FacultyCandidate(1, 10L, 1, 5, LocalDateTime.of(2026, 11, 3, 9, 0), Set.of()),
                new FacultyCandidate(2, 10L, 1, 5, LocalDateTime.of(2026, 11, 1, 9, 0), Set.of()));

        List<Assignment> a = planner.plan(List.of(room(100, 1, "CS501")), f);

        assertEquals(2L, a.get(0).facultyId());
    }

    @Test
    void conflictHeavyRoomsAreFilledFirst() {
        // Faculty 2 teaches both subjects in room 101, so faculty 1 must go there even though room 100 sorts first.
        List<FacultyCandidate> f = List.of(faculty(1, 10, 0, 5, Set.of()), faculty(2, 10, 0, 5, Set.of("A", "B")));

        List<Assignment> a = planner.plan(List.of(room(100, 1, "C"), room(101, 1, "A", "B")), f);

        Assignment r101 = a.stream().filter(x -> x.roomId() == 101).findFirst().orElseThrow();
        assertEquals(1L, r101.facultyId());
        assertNotEquals(r101.facultyId(), a.stream().filter(x -> x.roomId() == 100).findFirst().orElseThrow().facultyId());
    }

    @Test
    void invigilatorCountScalesWithStudents() {
        assertEquals(1, InvigilatorPlanner.invigilatorsFor(0, 30));
        assertEquals(1, InvigilatorPlanner.invigilatorsFor(30, 30));
        assertEquals(3, InvigilatorPlanner.invigilatorsFor(61, 30));
    }
}
