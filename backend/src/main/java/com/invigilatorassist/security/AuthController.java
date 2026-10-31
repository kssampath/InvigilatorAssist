package com.invigilatorassist.security;

import com.invigilatorassist.common.ApiException;
import com.invigilatorassist.domain.AppUser;
import com.invigilatorassist.domain.Role;
import com.invigilatorassist.repository.AppUserRepository;
import com.invigilatorassist.repository.InstitutionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    public record LoginRequest(@NotBlank String institutionCode, @NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, Role role, String fullName, boolean mustChangePassword) {}

    public record Me(long userId, String username, String fullName, Role role, String institutionCode,
                     String institutionName, boolean mustChangePassword) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {}

    private final InstitutionRepository institutions;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public AuthController(InstitutionRepository institutions, AppUserRepository users, PasswordEncoder passwords,
                          TokenService tokens) {
        this.institutions = institutions;
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @PostMapping("/auth/login")
    @Transactional(readOnly = true)
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        // One generic message for every failure so the endpoint does not reveal which part was wrong.
        ApiException invalid = new ApiException(HttpStatus.UNAUTHORIZED, "Invalid institution, username or password");
        var inst = institutions.findByCode(req.institutionCode().trim().toUpperCase()).orElseThrow(() -> invalid);
        AppUser user = users.findByInstitutionIdAndUsername(inst.getId(), normalise(req.username()))
                .filter(AppUser::isActive)
                .orElseThrow(() -> invalid);
        if (!passwords.matches(req.password(), user.getPasswordHash())) {
            throw invalid;
        }
        return new LoginResponse(tokens.issue(user), user.getRole(), user.getFullName(), user.isMustChangePassword());
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser cu = CurrentUser.of(jwt);
        AppUser user = users.findById(cu.userId()).orElseThrow(() -> ApiException.notFound("User"));
        var inst = institutions.findById(user.getInstitutionId()).orElseThrow();
        return new Me(user.getId(), user.getUsername(), user.getFullName(), user.getRole(), inst.getCode(),
                inst.getName(), user.isMustChangePassword());
    }

    @PostMapping("/auth/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest req) {
        AppUser user = users.findById(CurrentUser.of(jwt).userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwords.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is wrong");
        }
        user.setPasswordHash(passwords.encode(req.newPassword()));
        user.setMustChangePassword(false);
    }

    /** Staff log in with email (case-insensitive); students with their registration number. */
    public static String normalise(String username) {
        String u = username.trim();
        return u.contains("@") ? u.toLowerCase() : u.toUpperCase();
    }
}
