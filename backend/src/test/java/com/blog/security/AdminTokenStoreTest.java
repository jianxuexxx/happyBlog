package com.blog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.blog.util.CacheUtil;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminTokenStoreTest {

    @Mock
    private CacheUtil cacheUtil;

    @Mock
    private JwtUtil jwtUtil;

    private AdminTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new AdminTokenStore(cacheUtil, jwtUtil);
    }

    @Test
    @DisplayName("save：以设计规格 §9 定义的键写入，TTL 取自 token 有效期")
    void saveUsesSpecKeyAndTokenTtl() {
        given(jwtUtil.getTtl()).willReturn(Duration.ofDays(7));

        tokenStore.save("a-token");

        verify(cacheUtil).set("blog:admin:token", "a-token", Duration.ofDays(7));
    }

    @Test
    @DisplayName("matches：存储值一致返回 true")
    void matchesReturnsTrueForSameToken() {
        given(cacheUtil.get("blog:admin:token", String.class)).willReturn("a-token");

        assertThat(tokenStore.matches("a-token")).isTrue();
    }

    @Test
    @DisplayName("matches：存储值不一致返回 false（旧 token 被新登录顶掉）")
    void matchesReturnsFalseForDifferentToken() {
        given(cacheUtil.get("blog:admin:token", String.class)).willReturn("newer-token");

        assertThat(tokenStore.matches("a-token")).isFalse();
    }

    @Test
    @DisplayName("matches：键不存在返回 false（已登出或已过期）")
    void matchesReturnsFalseWhenAbsent() {
        given(cacheUtil.get("blog:admin:token", String.class)).willReturn(null);

        assertThat(tokenStore.matches("a-token")).isFalse();
    }

    @Test
    @DisplayName("clear：删除会话键，使登出立即生效")
    void clearDeletesSessionKey() {
        tokenStore.clear();

        verify(cacheUtil).delete("blog:admin:token");
    }
}
