package com.invigilatorassist.swaps;

import com.invigilatorassist.common.ApiException;
import com.invigilatorassist.domain.Faculty;
import com.invigilatorassist.domain.InvigilationDuty;
import com.invigilatorassist.domain.RoomAllocation;
import com.invigilatorassist.domain.SwapRequest;
import com.invigilatorassist.repository.FacultyRepository;
import com.invigilatorassist.repository.InvigilationDutyRepository;
import com.invigilatorassist.repository.SwapRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Duty swaps between two faculty members. Unlike the old PHP flow, accepting a swap really exchanges the
 * invigilators, both sides are re-checked at accept time, and stale requests on the same duties are cancelled.
 */
@Service
public class SwapService {

    public record DutyView(long dutyId, LocalDate date, LocalTime startTime, LocalTime endTime, String roomCode,
                           String building, String facultyName) {}

    public record SwapView(long id, String status, String note, boolean incoming, DutyView requesterDuty,
                           DutyView targetDuty) {}

    private final FacultyRepository faculty;
    private final InvigilationDutyRepository duties;
    private final SwapRequestRepository swaps;
    private final DutyRules rules;
    private final Clock clock;

    public SwapService(FacultyRepository faculty, InvigilationDutyRepository duties, SwapRequestRepository swaps,
                       DutyRules rules, Clock clock) {
        this.faculty = faculty;
        this.duties = duties;
        this.swaps = swaps;
        this.rules = rules;
        this.clock = clock;
    }

    /** Duties of other faculty that the caller could swap their duty with, checked both ways. */
    @Transactional(readOnly = true)
    public List<DutyView> candidates(long userId, long institutionId, long myDutyId) {
        Faculty me = me(userId);
        InvigilationDuty mine = ownDuty(me, myDutyId);
        return duties.findUpcomingForInstitution(institutionId, LocalDate.now(clock)).stream()
                .filter(other -> !Objects.equals(other.getFaculty().getId(), me.getId()))
                .filter(other -> !Objects.equals(other.getRoomAllocation().getId(), mine.getRoomAllocation().getId()))
                .filter(rules::isInFuture)
                .filter(other -> rules.whyCannotTake(me, other, mine).isEmpty())
                .filter(other -> rules.whyCannotTake(other.getFaculty(), mine, other).isEmpty())
                .map(SwapService::view)
                .toList();
    }

    @Transactional
    public SwapView request(long userId, long institutionId, long myDutyId, long targetDutyId, String note) {
        Faculty me = me(userId);
        InvigilationDuty mine = ownDuty(me, myDutyId);
        InvigilationDuty target = duties.findById(targetDutyId)
                .filter(d -> d.getRoomAllocation().getInstitutionId() == institutionId)
                .orElseThrow(() -> ApiException.notFound("Duty " + targetDutyId));
        if (Objects.equals(target.getFaculty().getId(), me.getId())) throw ApiException.badRequest("That duty is already yours");
        if (!rules.isInFuture(target)) throw ApiException.badRequest("That duty has already started");
        if (swaps.existsPending(mine.getId(), target.getId())) throw ApiException.conflict("You already asked for this swap");
        ensureSwappable(me, mine, target);
        SwapRequest saved = swaps.save(new SwapRequest(institutionId, mine, target, note));
        return view(saved, me);
    }

    @Transactional
    public SwapView accept(long userId, long swapId) {
        Faculty me = me(userId);
        SwapRequest swap = pendingSwap(swapId);
        if (!Objects.equals(swap.getTargetDuty().getFaculty().getId(), me.getId())) {
            throw ApiException.forbidden("Only the colleague who was asked can accept this swap");
        }
        InvigilationDuty theirs = swap.getRequesterDuty();
        InvigilationDuty mine = swap.getTargetDuty();
        if (!rules.isInFuture(theirs) || !rules.isInFuture(mine)) {
            throw ApiException.conflict("One of the duties has already started");
        }
        Faculty requester = theirs.getFaculty();
        ensureSwappable(requester, theirs, mine);   // re-check: things may have changed since the request

        theirs.reassignTo(me);
        mine.reassignTo(requester);
        swap.decide(SwapRequest.Status.ACCEPTED);
        swaps.findPendingTouching(List.of(theirs.getId(), mine.getId()), swap.getId())
                .forEach(s -> s.decide(SwapRequest.Status.CANCELLED));
        return view(swap, me);
    }

    @Transactional
    public SwapView reject(long userId, long swapId) {
        Faculty me = me(userId);
        SwapRequest swap = pendingSwap(swapId);
        if (!Objects.equals(swap.getTargetDuty().getFaculty().getId(), me.getId())) {
            throw ApiException.forbidden("Only the colleague who was asked can reject this swap");
        }
        swap.decide(SwapRequest.Status.REJECTED);
        return view(swap, me);
    }

    @Transactional
    public SwapView cancel(long userId, long swapId) {
        Faculty me = me(userId);
        SwapRequest swap = pendingSwap(swapId);
        if (!Objects.equals(swap.getRequesterDuty().getFaculty().getId(), me.getId())) {
            throw ApiException.forbidden("Only the person who asked can cancel this swap");
        }
        swap.decide(SwapRequest.Status.CANCELLED);
        return view(swap, me);
    }

    @Transactional(readOnly = true)
    public List<SwapView> mine(long userId) {
        Faculty me = me(userId);
        return swaps.findAllInvolvingFaculty(me.getId()).stream().map(s -> view(s, me)).toList();
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private void ensureSwappable(Faculty requester, InvigilationDuty requesterDuty, InvigilationDuty targetDuty) {
        if (Objects.equals(requesterDuty.getRoomAllocation().getId(), targetDuty.getRoomAllocation().getId())) {
            throw ApiException.badRequest("Both duties are in the same room and sitting");
        }
        rules.whyCannotTake(requester, targetDuty, requesterDuty).ifPresent(reason -> {
            throw ApiException.conflict(reason);
        });
        rules.whyCannotTake(targetDuty.getFaculty(), requesterDuty, targetDuty).ifPresent(reason -> {
            throw ApiException.conflict(reason);
        });
    }

    private Faculty me(long userId) {
        return faculty.findByUserId(userId).orElseThrow(() -> ApiException.forbidden("No faculty profile for this account"));
    }

    private InvigilationDuty ownDuty(Faculty me, long dutyId) {
        InvigilationDuty d = duties.findById(dutyId).orElseThrow(() -> ApiException.notFound("Duty " + dutyId));
        if (!Objects.equals(d.getFaculty().getId(), me.getId())) throw ApiException.forbidden("That duty is not yours");
        if (!rules.isInFuture(d)) throw ApiException.badRequest("That duty has already started");
        return d;
    }

    private SwapRequest pendingSwap(long swapId) {
        SwapRequest s = swaps.findById(swapId).orElseThrow(() -> ApiException.notFound("Swap request " + swapId));
        if (s.getStatus() != SwapRequest.Status.PENDING) {
            throw ApiException.conflict("This swap request is already " + s.getStatus().name().toLowerCase());
        }
        return s;
    }

    static DutyView view(InvigilationDuty d) {
        RoomAllocation ra = d.getRoomAllocation();
        return new DutyView(d.getId(), ra.getExamDate(), ra.getStartTime(), ra.getEndTime(), ra.getRoom().getCode(),
                ra.getRoom().getBuilding(), d.getFaculty().getUser().getFullName());
    }

    private static SwapView view(SwapRequest s, Faculty me) {
        boolean incoming = Objects.equals(s.getTargetDuty().getFaculty().getId(), me.getId())
                && s.getStatus() == SwapRequest.Status.PENDING;
        return new SwapView(s.getId(), s.getStatus().name(), s.getNote(), incoming,
                view(s.getRequesterDuty()), view(s.getTargetDuty()));
    }
}
