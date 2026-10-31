package com.invigilatorassist.security;

import com.invigilatorassist.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** Who is calling, read from the verified access token. Tenant isolation hangs off institutionId. */
public record CurrentUser(long userId, long institutionId, Role role) {

    public static CurrentUser of(Jwt jwt) {
        Number inst = jwt.getClaim(TokenService.CLAIM_INSTITUTION);
        return new CurrentUser(
                Long.parseLong(jwt.getSubject()),
                inst.longValue(),
                Role.valueOf(jwt.getClaimAsString(TokenService.CLAIM_ROLE)));
    }
}
