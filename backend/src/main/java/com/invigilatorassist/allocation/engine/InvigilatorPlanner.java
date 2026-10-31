package com.invigilatorassist.allocation.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, fairness-first invigilator assignment for one sitting.
 *
 * <p>A faculty member is eligible for a room when they are available for the sitting, still have quota left,
 * are not already used in this sitting, and do not teach any subject being written in that room.
 *
 * <p>Among eligible faculty, the planner picks, in order: the lowest load (duties / quota), the department
 * used least so far in this sitting, the longest time since their last duty, then the lowest id (stable).
 */
public final class InvigilatorPlanner {

    public record FacultyCandidate(
            long facultyId,
            Long departmentId,
            int dutyCount,
            int maxDuties,
            LocalDateTime lastDutyAt,
            Set<String> teachesGroupKeys) {}

    public record RoomNeed(long roomId, String roomCode, int invigilatorsNeeded, Set<String> groupKeys) {}

    public record Assignment(long roomId, long facultyId) {}

    /** Students per invigilator; a room always gets at least one. */
    public static int invigilatorsFor(int students, int studentsPerInvigilator) {
        return Math.max(1, (students + studentsPerInvigilator - 1) / studentsPerInvigilator);
    }

    /**
     * @param candidates faculty already filtered to those available for this sitting
     *                   (not on leave, no overlapping duty)
     */
    public List<Assignment> plan(List<RoomNeed> rooms, List<FacultyCandidate> candidates) {
        Map<Long, Integer> load = new HashMap<>();
        candidates.forEach(c -> load.put(c.facultyId(), c.dutyCount()));
        Set<Long> used = new HashSet<>();
        Map<Long, Integer> deptUsage = new HashMap<>();
        List<Assignment> out = new ArrayList<>();

        // Rooms with the most subject conflicts first: they have the fewest eligible faculty.
        List<RoomNeed> ordered = new ArrayList<>(rooms);
        ordered.sort(Comparator.comparingInt((RoomNeed r) -> r.groupKeys().size()).reversed()
                .thenComparing(RoomNeed::roomCode));

        for (RoomNeed room : ordered) {
            for (int i = 0; i < room.invigilatorsNeeded(); i++) {
                FacultyCandidate pick = candidates.stream()
                        .filter(c -> !used.contains(c.facultyId()))
                        .filter(c -> load.get(c.facultyId()) < c.maxDuties())
                        .filter(c -> Collections.disjoint(c.teachesGroupKeys(), room.groupKeys()))
                        .min(Comparator
                                .comparingDouble((FacultyCandidate c) -> ratio(load.get(c.facultyId()), c.maxDuties()))
                                .thenComparingInt(c -> deptUsage.getOrDefault(c.departmentId(), 0))
                                .thenComparing(FacultyCandidate::lastDutyAt,
                                        Comparator.nullsFirst(Comparator.naturalOrder()))
                                .thenComparingLong(FacultyCandidate::facultyId))
                        .orElseThrow(() -> new AllocationException(
                                "Not enough free invigilators for room " + room.roomCode()));
                used.add(pick.facultyId());
                load.merge(pick.facultyId(), 1, Integer::sum);
                if (pick.departmentId() != null) deptUsage.merge(pick.departmentId(), 1, Integer::sum);
                out.add(new Assignment(room.roomId(), pick.facultyId()));
            }
        }
        return out;
    }

    private static double ratio(int duties, int max) {
        return max == 0 ? Double.MAX_VALUE : (double) duties / max;
    }
}
