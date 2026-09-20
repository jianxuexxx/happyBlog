package com.blog.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 签发与校验（HS256）。校验失败一律返回 null，不抛异常 —— 调用方只需判空。 */
@Component
public class JwtUtil {

    private final String secret;
    private final int expireDays;

    public JwtUtil(@Value("${blog.jwt.secret}") String secret,
                   @Value("${blog.jwt.expire-days:7}") int expireDays) {
        this.secret = secret;
        this.expireDays = expireDays;
    }

    public String generate(String username) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(username)
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(now)
                .withExpiresAt(now.plus(expireDays, ChronoUnit.DAYS))
                .sign(Algorithm.HMAC256(secret));
    }

    /** 校验签名与过期时间。通过返回用户名，失败返回 null。 */
    public String verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
            return verifier.verify(token).getSubject();
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    public Duration getTtl() {
        return Duration.ofDays(expireDays);
    }
}
