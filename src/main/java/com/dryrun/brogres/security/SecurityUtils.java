package com.dryrun.brogres.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof BrogresPrincipal p)) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        return p.userId();
    }
}
