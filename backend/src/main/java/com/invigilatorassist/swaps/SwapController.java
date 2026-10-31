package com.invigilatorassist.swaps;

import com.invigilatorassist.security.CurrentUser;
import com.invigilatorassist.swaps.SwapService.SwapView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/swaps")
public class SwapController {

    public record CreateSwap(@NotNull Long myDutyId, @NotNull Long targetDutyId, @Size(max = 300) String note) {}

    private final SwapService swaps;

    public SwapController(SwapService swaps) {
        this.swaps = swaps;
    }

    @GetMapping
    public List<SwapView> mine(@AuthenticationPrincipal Jwt jwt) {
        return swaps.mine(CurrentUser.of(jwt).userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SwapView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSwap req) {
        CurrentUser cu = CurrentUser.of(jwt);
        return swaps.request(cu.userId(), cu.institutionId(), req.myDutyId(), req.targetDutyId(), req.note());
    }

    @PostMapping("/{id}/accept")
    public SwapView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return swaps.accept(CurrentUser.of(jwt).userId(), id);
    }

    @PostMapping("/{id}/reject")
    public SwapView reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return swaps.reject(CurrentUser.of(jwt).userId(), id);
    }

    @PostMapping("/{id}/cancel")
    public SwapView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return swaps.cancel(CurrentUser.of(jwt).userId(), id);
    }
}
