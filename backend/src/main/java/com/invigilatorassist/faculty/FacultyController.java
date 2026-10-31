package com.invigilatorassist.faculty;

import com.invigilatorassist.common.ApiException;
import com.invigilatorassist.domain.Faculty;
import com.invigilatorassist.domain.FacultyUnavailability;
import com.invigilatorassist.repository.FacultyRepository;
import com.invigilatorassist.repository.FacultyUnavailabilityRepository;
import com.invigilatorassist.repository.InvigilationDutyRepository;
import com.invigilatorassist.repository.SeatAllocationRepository;
import com.invigilatorassist.security.CurrentUser;
import com.invigilatorassist.swaps.SwapService;
import com.invigilatorassist.swaps.SwapService.DutyView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/faculty")
public class FacultyController {

    public record MyDuty(long dutyId, LocalDate date, LocalTime startTime, LocalTime endTime, String roomCode,
                         String building, List<String> subjects) {}

    public record LoadSummary(int dutyCount, int maxDuties) {}

    public record Unavailability(long id, LocalDate date, String reason) {}

    public record AddUnavailability(@NotNull @FutureOrPresent LocalDate date, @Size(max = 200) String reason) {}

    private final FacultyRepository faculty;
    private final InvigilationDutyRepository duties;
    private final SeatAllocationRepository seats;
    private final FacultyUnavailabilityRepository unavailability;
    private final SwapService swaps;
    private final Clock clock;

    public FacultyController(FacultyRepository faculty, InvigilationDutyRepository duties,
                             SeatAllocationRepository seats, FacultyUnavailabilityRepository unavailability,
                             SwapService swaps, Clock clock) {
        this.faculty = faculty;
        this.duties = duties;
        this.seats = seats;
        this.unavailability = unavailability;
        this.swaps = swaps;
        this.clock = clock;
    }

    @GetMapping("/duties")
    @Transactional(readOnly = true)
    public List<MyDuty> duties(@AuthenticationPrincipal Jwt jwt) {
        Faculty me = me(jwt);
        return duties.findUpcomingForFaculty(me.getId(), LocalDate.now(clock)).stream()
                .map(d -> new MyDuty(d.getId(), d.getRoomAllocation().getExamDate(), d.getRoomAllocation().getStartTime(),
                        d.getRoomAllocation().getEndTime(), d.getRoomAllocation().getRoom().getCode(),
                        d.getRoomAllocation().getRoom().getBuilding(),
                        seats.findSubjectCodesInRoom(d.getRoomAllocation().getId())))
                .toList();
    }

    @GetMapping("/load")
    @Transactional(readOnly = true)
    public LoadSummary load(@AuthenticationPrincipal Jwt jwt) {
        Faculty me = me(jwt);
        return new LoadSummary(me.getDutyCount(), me.getMaxDuties());
    }

    @GetMapping("/duties/{dutyId}/swap-candidates")
    public List<DutyView> swapCandidates(@AuthenticationPrincipal Jwt jwt, @PathVariable long dutyId) {
        CurrentUser cu = CurrentUser.of(jwt);
        return swaps.candidates(cu.userId(), cu.institutionId(), dutyId);
    }

    @GetMapping("/unavailability")
    @Transactional(readOnly = true)
    public List<Unavailability> unavailability(@AuthenticationPrincipal Jwt jwt) {
        return unavailability.findAllByFacultyIdAndUnavailableOnGreaterThanEqualOrderByUnavailableOn(
                        me(jwt).getId(), LocalDate.now(clock)).stream()
                .map(u -> new Unavailability(u.getId(), u.getUnavailableOn(), u.getReason()))
                .toList();
    }

    @PostMapping("/unavailability")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Unavailability blockDate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddUnavailability req) {
        Faculty me = me(jwt);
        if (unavailability.existsByFacultyIdAndUnavailableOn(me.getId(), req.date())) {
            throw ApiException.conflict(req.date() + " is already blocked");
        }
        FacultyUnavailability saved = unavailability.save(new FacultyUnavailability(me, req.date(), req.reason()));
        return new Unavailability(saved.getId(), saved.getUnavailableOn(), saved.getReason());
    }

    @DeleteMapping("/unavailability/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void unblockDate(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        Faculty me = me(jwt);
        FacultyUnavailability u = unavailability.findById(id)
                .filter(x -> Objects.equals(x.getFaculty().getId(), me.getId()))
                .orElseThrow(() -> ApiException.notFound("Blocked date"));
        unavailability.delete(u);
    }

    private Faculty me(Jwt jwt) {
        return faculty.findByUserId(CurrentUser.of(jwt).userId())
                .orElseThrow(() -> ApiException.forbidden("No faculty profile for this account"));
    }
}
