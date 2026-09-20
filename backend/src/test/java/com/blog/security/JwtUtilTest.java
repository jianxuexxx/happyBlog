package com.blog.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef0123456789abcdef";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 7);

    @Test
    @DisplayName("签发的 token 校验通过并返回用户名")
    void generatedTokenVerifies() {
        String token = jwtUtil.generate("admin");

        assertThat(jwtUtil.verify(token)).isEqualTo("admin");
    }

    @Test
    @DisplayName("被篡改的 token 校验失败返回 null")
    void tamperedTokenFailsVerification() {
        String token = jwtUtil.generate("admin");
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        assertThat(jwtUtil.verify(tampered)).isNull();
    }

    @Test
    @DisplayName("用其他密钥签发的 token 校验失败")
    void tokenSignedWithOtherSecretFails() {
        JwtUtil other = new JwtUtil("another-secret-0123456789abcdef0123456789abcdef", 7);
        String foreign = other.generate("admin");

        assertThat(jwtUtil.verify(foreign)).isNull();
    }

    @Test
    @DisplayName("已过期的 token 校验失败")
    void expiredTokenFailsVerification() {
        // expireDays 为负 → 过期时间落在过去
        JwtUtil expiredIssuer = new JwtUtil(SECRET, -1);
        String expired = expiredIssuer.generate("admin");

        assertThat(jwtUtil.verify(expired)).isNull();
    }

    @Test
    @DisplayName("非 JWT 格式的字符串校验失败返回 null 而非抛异常")
    void garbageTokenReturnsNullInsteadOfThrowing() {
        assertThat(jwtUtil.verify("not-a-jwt")).isNull();
        assertThat(jwtUtil.verify("")).isNull();
    }

    @Test
    @DisplayName("getTtl 返回配置的天数")
    void getTtlReturnsConfiguredDuration() {
        assertThat(jwtUtil.getTtl()).isEqualTo(Duration.ofDays(7));

        assertThat(new JwtUtil(SECRET, 3).getTtl()).isEqualTo(Duration.ofDays(3));
    }
}
