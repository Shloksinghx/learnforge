package com.learnplatform.config;

import com.learnplatform.domain.AppUser;
import com.learnplatform.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    public String issue(AppUser u) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(u.getId().toString())
                .claim("org", u.getOrgId().toString())
                .claim("role", u.getRole().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMinutes * 60_000))
                .signWith(key)
                .compact();
    }

    public AuthUser parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new AuthUser(UUID.fromString(c.getSubject()),
                UUID.fromString(c.get("org", String.class)),
                Role.valueOf(c.get("role", String.class)));
    }
}
