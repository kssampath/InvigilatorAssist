package com.invigilatorassist.allocation.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure seating algorithm (no Spring, no database) so it can be unit tested in isolation.
 *
 * <p>Rules:
 * <ol>
 *   <li>Students who need an accessible room are seated only in accessible rooms.</li>
 *   <li>The fewest rooms are used: largest rooms first.</li>
 *   <li>Subjects are mixed: inside every room, students of the same subject (group) are never seated
 *       next to each other when the numbers make that possible.</li>
 * </ol>
 */
public final class SeatingPlanner {

    public record Candidate(long studentId, long examSlotId, String groupKey, boolean needsAccessible) {}

    public record RoomSpec(long roomId, String code, int capacity, boolean accessible) {}

    public record Seat(int seatNo, Candidate candidate) {}

    public record RoomPlan(RoomSpec room, List<Seat> seats) {
        public List<String> groupKeys() {
            return seats.stream().map(s -> s.candidate().groupKey()).distinct().toList();
        }
    }

    public record SeatingPlan(List<RoomPlan> rooms) {
        public int seatedCount() {
            return rooms.stream().mapToInt(r -> r.seats().size()).sum();
        }
    }

    public SeatingPlan plan(List<Candidate> candidates, List<RoomSpec> rooms) {
        if (candidates.isEmpty()) {
            return new SeatingPlan(List.of());
        }
        List<Candidate> accessibleNeeded = candidates.stream().filter(Candidate::needsAccessible).toList();
        List<Candidate> others = candidates.stream().filter(c -> !c.needsAccessible()).toList();

        List<RoomSpec> chosen = chooseRooms(rooms, candidates.size(), accessibleNeeded.size());

        // Room membership: accessible-needs students first into accessible rooms, then everyone else.
        Map<RoomSpec, List<Candidate>> members = new LinkedHashMap<>();
        chosen.forEach(r -> members.put(r, new ArrayList<>()));

        int a = 0;
        for (RoomSpec room : chosen) {
            if (!room.accessible()) continue;
            List<Candidate> list = members.get(room);
            while (a < accessibleNeeded.size() && list.size() < room.capacity()) {
                list.add(accessibleNeeded.get(a++));
            }
        }

        // Spread subjects across rooms too, so no room is filled with a single subject.
        List<Candidate> spread = spreadByGroup(others);
        int o = 0;
        for (RoomSpec room : chosen) {
            List<Candidate> list = members.get(room);
            while (o < spread.size() && list.size() < room.capacity()) {
                list.add(spread.get(o++));
            }
        }

        List<RoomPlan> plans = new ArrayList<>();
        for (var e : members.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            List<Candidate> ordered = spreadByGroup(e.getValue());
            List<Seat> seats = new ArrayList<>(ordered.size());
            for (int i = 0; i < ordered.size(); i++) {
                seats.add(new Seat(i + 1, ordered.get(i)));
            }
            plans.add(new RoomPlan(e.getKey(), List.copyOf(seats)));
        }
        return new SeatingPlan(List.copyOf(plans));
    }

    /** Picks the fewest rooms that fit everyone, covering accessible needs with accessible rooms first. */
    static List<RoomSpec> chooseRooms(List<RoomSpec> rooms, int total, int accessibleNeeded) {
        Comparator<RoomSpec> bySizeDesc = Comparator.comparingInt(RoomSpec::capacity).reversed()
                .thenComparing(RoomSpec::code);

        List<RoomSpec> accessibleRooms = rooms.stream().filter(RoomSpec::accessible).sorted(bySizeDesc).toList();
        int accessibleCapacity = accessibleRooms.stream().mapToInt(RoomSpec::capacity).sum();
        if (accessibleCapacity < accessibleNeeded) {
            throw new AllocationException("Not enough accessible seats: need " + accessibleNeeded
                    + ", have " + accessibleCapacity);
        }
        int totalCapacity = rooms.stream().mapToInt(RoomSpec::capacity).sum();
        if (totalCapacity < total) {
            throw new AllocationException("Not enough seats: need " + total + ", have " + totalCapacity);
        }

        List<RoomSpec> chosen = new ArrayList<>();
        int covered = 0;
        for (RoomSpec r : accessibleRooms) {
            if (covered >= accessibleNeeded) break;
            chosen.add(r);
            covered += r.capacity();
        }
        List<RoomSpec> remaining = rooms.stream().filter(r -> !chosen.contains(r)).sorted(bySizeDesc).toList();
        for (RoomSpec r : remaining) {
            if (covered >= total) break;
            chosen.add(r);
            covered += r.capacity();
        }
        return chosen;
    }

    /**
     * Orders candidates so that equal group keys are not adjacent whenever the largest group is at most
     * half of the list (rounded up). Groups are laid out largest-first into even positions, then odd.
     */
    static List<Candidate> spreadByGroup(List<Candidate> input) {
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        for (Candidate c : input) {
            groups.computeIfAbsent(c.groupKey(), k -> new ArrayList<>()).add(c);
        }
        List<Candidate> flat = new ArrayList<>(input.size());
        groups.values().stream()
                .sorted(Comparator.<List<Candidate>>comparingInt(List::size).reversed())
                .forEach(flat::addAll);

        Candidate[] out = new Candidate[flat.size()];
        int pos = 0;
        for (Candidate c : flat) {
            if (pos >= out.length) pos = 1;
            out[pos] = c;
            pos += 2;
        }
        return List.of(out);
    }

    /** Number of neighbouring seat pairs that share a subject; 0 is ideal. */
    public static int adjacentSameGroupPairs(RoomPlan room) {
        int n = 0;
        List<Seat> s = room.seats();
        for (int i = 1; i < s.size(); i++) {
            if (s.get(i).candidate().groupKey().equals(s.get(i - 1).candidate().groupKey())) n++;
        }
        return n;
    }
}
