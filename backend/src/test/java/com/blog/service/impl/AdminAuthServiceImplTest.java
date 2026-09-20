package com.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.blog.common.BizException;
import com.blog.dto.LoginDTO;
import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin123";

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AdminTokenStore tokenStore;

    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAuthServiceImpl(jwtUtil, tokenStore, USERNAME, PASSWORD);
    }

    private LoginDTO login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    @Test
    @DisplayName("登录：凭证正确时签发 token 并写入会话存储")
    void loginIssuesAndStoresToken() {
        given(jwtUtil.generate(USERNAME)).willReturn("signed-token");

        String token = service.login(login(USERNAME, PASSWORD));

        assertThat(token).isEqualTo("signed-token");
        verify(tokenStore).save("signed-token");
    }

    @Test
    @DisplayName("登录：密码错误抛 40101")
    void loginRejectsWrongPassword() {
        assertThatThrownBy(() -> service.login(login(USERNAME, "wrong")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40101));
    }

    @Test
    @DisplayName("登录：用户名错误抛 40101")
    void loginRejectsWrongUsername() {
        assertThatThrownBy(() -> service.login(login("other", PASSWORD)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40101));
    }

    @Test
    @DisplayName("登录：长度不同的用户名也抛 40101（常量时间比较不得因长度差异抛异常）")
    void loginRejectsUsernameOfDifferentLength() {
        assertThatThrownBy(() -> service.login(login("a-much-longer-username", PASSWORD)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40101));
    }

    @Test
    @DisplayName("登出：清除会话存储，使 token 立即失效")
    void logoutClearsTokenStore() {
        service.logout();

        verify(tokenStore).clear();
    }
}
