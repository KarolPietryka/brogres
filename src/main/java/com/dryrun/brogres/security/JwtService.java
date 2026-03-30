package com.dryrun.brogres.security;

import com.dryrun.brogres.config.BrogresSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final BrogresSecurityProperties securityProperties;

    public String createAccessToken(long userId, String nick) {
        long nowMs = System.currentTimeMillis();
        long expMs = nowMs + securityProperties.getJwtExpirationMs();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("nick", nick)
                .issuedAt(new Date(nowMs))
                .expiration(new Date(expMs))
                .signWith(signingKey())
                .compact();
    }

    public BrogresPrincipal parsePrincipal(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        long userId = Long.parseLong(claims.getSubject());
        String nick = claims.get("nick", String.class);
        if (nick == null || nick.isBlank()) {
            throw new IllegalArgumentException("missing nick claim");
        }
        return new BrogresPrincipal(userId, nick);
    }

    private SecretKey signingKey() {
        byte[] bytes = securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}
