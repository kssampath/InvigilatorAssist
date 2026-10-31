package com.invigilatorassist.swaps;

import com.invigilatorassist.domain.Faculty;
import com.invigilatorassist.domain.InvigilationDuty;
import com.invigilatorassist.domain.RoomAllocation;
import com.invigilatorassist.repository.FacultyUnavailabilityRepository;
import com.invigilatorassist.repository.InvigilationDutyRepository;
import com.invigilatorassist.repository.SeatAllocationRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** The checks that decide whether a faculty member may take over a given duty. */
@Component
public class DutyRules {

    private final InvigilationDutyRepository duties;
    private final FacultyUnavailabilityRepository unavailability;
    private final SeatAllocationRepository seats;
    private final Clock clock;

    public DutyRules(InvigilationDutyRepository duties, FacultyUnavailabilityRepository unavailability,
                     SeatAllocationRepository seats, Clock clock) {
        this.duties = duties;
        this.unavailability = unavailability;
        this.seats = seats;
        this.clock = clock;
    }

    public boolean isInFuture(InvigilationDuty duty) {
        RoomAllocation ra = duty.getRoomAllocation();
        return LocalDateTime.of(ra.getExamDate(), ra.getStartTime()).isAfter(LocalDateTime.now(clock));
    }

    /**
     * Why {@code faculty} cannot take {@code duty} (giving up {@code givingUp} in exchange), or empty when they can.
     */
    public Optional<String> whyCannotTake(Faculty faculty, InvigilationDuty duty, InvigilationDuty givingUp) {
        RoomAllocation ra = duty.getRoomAllocation();
        if (unavailability.existsByFacultyIdAndUnavailableOn(faculty.getId(), ra.getExamDate())) {
            return Optional.of(faculty.getUser().getFullName() + " is on leave on " + ra.getExamDate());
        }
        if (duties.hasOverlappingDuty(faculty.getId(), givingUp.getId(), ra.getExamDate(), ra.getStartTime(),
                ra.getEndTime())) {
            return Optional.of(faculty.getUser().getFullName() + " already has a duty at that time");
        }
        if (seats.facultyTeachesSubjectInRoom(ra.getId(), faculty.getId())) {
            return Optional.of(faculty.getUser().getFullName() + " teaches a subject written in room "
                    + ra.getRoom().getCode());
        }
        return Optional.empty();
    }
}
