package com.invigilatorassist.allocation.engine;

import com.invigilatorassist.allocation.engine.SeatingPlanner.Candidate;
import com.invigilatorassist.allocation.engine.SeatingPlanner.RoomPlan;
import com.invigilatorassist.allocation.engine.SeatingPlanner.RoomSpec;
import com.invigilatorassist.allocation.engine.SeatingPlanner.Seat;
import com.invigilatorassist.allocation.engine.SeatingPlanner.SeatingPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatingPlannerTest {

    private final SeatingPlanner planner = new SeatingPlanner();

    private static List<Candidate> students(String subject, long slotId, int count, long firstId, boolean accessible) {
        List<Candidate> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Candidate(firstId + i, slotId, subject, accessible));
        }
        return list;
    }

    private static RoomSpec room(long id, int capacity, boolean accessible) {
        return new RoomSpec(id, "R" + id, capacity, accessible);
    }

    @Test
    void seatsEveryoneUsingTheFewestRooms() {
        List<Candidate> c = students("CS501", 1, 70, 1, false);
        List<RoomSpec> rooms = List.of(room(1, 20, false), room(2, 40, false), room(3, 30, false), room(4, 30, false));

        SeatingPlan plan = planner.plan(c, rooms);

        assertEquals(70, plan.seatedCount());
        assertEquals(2, plan.rooms().size(), "40 + 30 seats is enough");
        assertEquals(2L, plan.rooms().get(0).room().roomId());
    }

    @Test
    void everyStudentIsSeatedExactlyOnceWithUniqueSeatNumbers() {
        List<Candidate> c = new ArrayList<>();
        c.addAll(students("CS501", 1, 23, 1, false));
        c.addAll(students("EC502", 2, 17, 100, false));
        c.addAll(students("ME503", 3, 9, 200, false));

        SeatingPlan plan = planner.plan(c, List.of(room(1, 25, false), room(2, 25, false), room(3, 25, false)));

        Set<Long> seen = new HashSet<>();
        for (RoomPlan r : plan.rooms()) {
            Set<Integer> seatNos = new HashSet<>();
            for (Seat s : r.seats()) {
                assertTrue(seen.add(s.candidate().studentId()), "student seated twice");
                assertTrue(seatNos.add(s.seatNo()), "duplicate seat number");
                assertTrue(s.seatNo() >= 1 && s.seatNo() <= r.room().capacity());
            }
        }
        assertEquals(49, seen.size());
    }

    @Test
    void neighboursWriteDifferentSubjectsWhenNumbersAllow() {
        List<Candidate> c = new ArrayList<>();
        c.addAll(students("CS501", 1, 20, 1, false));
        c.addAll(students("EC502", 2, 20, 100, false));
        c.addAll(students("ME503", 3, 20, 200, false));

        SeatingPlan plan = planner.plan(c, List.of(room(1, 30, false), room(2, 30, false)));

        for (RoomPlan r : plan.rooms()) {
            assertEquals(0, SeatingPlanner.adjacentSameGroupPairs(r), "room " + r.room().code());
            assertTrue(r.groupKeys().size() >= 2, "each room should mix subjects");
        }
    }

    @Test
    void studentsNeedingAccessibilityAreOnlyInAccessibleRooms() {
        List<Candidate> c = new ArrayList<>();
        c.addAll(students("CS501", 1, 50, 1, false));
        c.addAll(students("CS501", 1, 3, 500, true));
        List<RoomSpec> rooms = List.of(room(1, 40, false), room(2, 40, false), room(3, 10, true));

        SeatingPlan plan = planner.plan(c, rooms);

        for (RoomPlan r : plan.rooms()) {
            for (Seat s : r.seats()) {
                if (s.candidate().needsAccessible()) {
                    assertTrue(r.room().accessible(), "student " + s.candidate().studentId());
                }
            }
        }
        assertEquals(53, plan.seatedCount());
    }

    @Test
    void failsClearlyWhenSeatsRunOut() {
        AllocationException ex = assertThrows(AllocationException.class,
                () -> planner.plan(students("CS501", 1, 61, 1, false), List.of(room(1, 30, false), room(2, 30, false))));
        assertTrue(ex.getMessage().contains("need 61"));
    }

    @Test
    void failsClearlyWhenAccessibleSeatsRunOut() {
        assertThrows(AllocationException.class,
                () -> planner.plan(students("CS501", 1, 5, 1, true), List.of(room(1, 30, false), room(2, 4, true))));
    }

    @Test
    void noCandidatesMeansNoRooms() {
        assertEquals(0, planner.plan(List.of(), List.of(room(1, 30, false))).rooms().size());
    }
}
