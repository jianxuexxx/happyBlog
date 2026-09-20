package com.blog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AdminTokenStore tokenStore;

    private AdminAuthInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(jwtUtil, tokenStore, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("缺少 Authorization 头：拒绝，响应体 code 为 40100")
    void rejectsWhenHeaderMissing() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("Authorization 头前缀不是 Bearer：拒绝")
    void rejectsWhenPrefixWrong() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("token 签名或过期时间无效：拒绝")
    void rejectsWhenTokenInvalid() throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        given(jwtUtil.verify("bad-token")).willReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("token 有效但存储中不存在（已登出）：拒绝")
    void rejectsWhenTokenRevoked() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        given(jwtUtil.verify("good-token")).willReturn("admin");
        given(tokenStore.matches("good-token")).willReturn(false);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("token 有效且存储中存在：放行")
    void allowsWhenTokenValidAndStored() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        given(jwtUtil.verify("good-token")).willReturn("admin");
        given(tokenStore.matches("good-token")).willReturn(true);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("【契约】拒绝时 HTTP 状态仍为 200，否则前端 axios 走错误分支读不到 code")
    void rejectsWithHttp200() throws Exception {
        interceptor.preHandle(request, response, new Object());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).contains("application/json");
    }
}
