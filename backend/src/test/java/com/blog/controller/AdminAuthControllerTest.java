package com.blog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import com.blog.service.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminAuthController.class)
@ActiveProfiles("test")
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private AdminTokenStore adminTokenStore;

    @Test
    @DisplayName("登录：成功返回 code=0 且 data.token 存在")
    void loginReturnsToken() throws Exception {
        given(adminAuthService.login(any())).willReturn("signed-token");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("signed-token"));
    }

    @Test
    @DisplayName("登录：入参为空时参数校验失败 code=40001")
    void loginValidatesBlankFields() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("登录接口本身不受鉴权拦截（无需 token 即可访问）")
    void loginIsExemptFromAuthInterceptor() throws Exception {
        given(adminAuthService.login(any())).willReturn("signed-token");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("登出：无 token 时被拦截 code=40100")
    void logoutRequiresToken() throws Exception {
        mockMvc.perform(post("/api/admin/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("登出：带有效 token 时成功并清除会话")
    void logoutWithTokenClearsSession() throws Exception {
        given(jwtUtil.verify("good-token")).willReturn("admin");
        given(adminTokenStore.matches("good-token")).willReturn(true);

        mockMvc.perform(post("/api/admin/logout")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(adminAuthService).logout();
    }
}
