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
    @DisplayName("登录接口本身不受鉴权拦截：带畸形 Authorization 头也照样放行")
    void loginIsExemptFromAuthInterceptor() throws Exception {
        // 这条与 loginReturnsToken 的区别不在「没有 Authorization 头」，而在「有、但是畸形的」：
        // 若只发一个不带 Authorization 的请求，它与 loginReturnsToken 是严格子集关系
        // （同样 200 + code=0），不产生任何新断言，用例名会虚报覆盖率。
        // 发一个 Basic 开头的畸形头才真正区分得开两种机制：
        //   · 路径被排除（excludePathPatterns / 拦截器直接放行）—— 拦截器根本不解析这个头，登录成功；
        //   · 路径没被排除、只是「恰好」token 被接受 —— 拦截器会尝试解析 'Basic xyz' 而拒绝，返回 40100。
        // 这里断言 code=0，就是在钉死前者。注意 adminAuthService/login 与 jwtUtil 都未对该头打桩，
        // 若拦截器真的去解析它，jwtUtil.verify 返回 null（默认 mock 行为），用例必然失败。
        given(adminAuthService.login(any())).willReturn("signed-token");

        mockMvc.perform(post("/api/admin/login")
                        .header("Authorization", "Basic xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("signed-token"));
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
