package com.clanmanager.clanmanager.security;

import com.clanmanager.clanmanager.entity.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration accessLifetime;
    private final Duration refreshLifetime;

    public JwtTokenProvider(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.access-lifetime:PT30M}") Duration accessLifetime,
            @Value("${auth.jwt.refresh-lifetime:P30D}") Duration refreshLifetime
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessLifetime = accessLifetime;
        this.refreshLifetime = refreshLifetime;
    }

    public String createAccessToken(Member member) {
        return createToken(member.getMemberId(), "access", accessLifetime);
    }

    public String createRefreshToken(Member member) {
        return createToken(member.getMemberId(), "refresh", refreshLifetime);
    }

    private String createToken(Long memberId, String type, Duration lifetime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    public Long validateAndGetMemberId(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!expectedType.equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Invalid token type");
        }
        return Long.valueOf(claims.getSubject());
    }

    public Instant getExpiration(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                .getPayload().getExpiration().toInstant();
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

