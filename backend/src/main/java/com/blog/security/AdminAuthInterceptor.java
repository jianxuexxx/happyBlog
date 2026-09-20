package com.blog.security;

import com.blog.common.Result;
import com.blog.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 保护 /api/admin/**（登录接口在 WebMvcConfig 中排除）。
 *
 * 【关键契约】拒绝时返回 HTTP 200 + 响应体 code=40100，不返回 HTTP 401。
 * 原因：frontend/src/api/http.ts:29-39 把 40100 的处理放在 axios 响应拦截器的
 * 成功回调里，axios 默认只把 2xx 视为成功。若这里返回 401，前端会走错误分支，
 * 清理 blog-admin-token 的逻辑永不执行，表现为「token 过期后卡在管理页」。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AdminTokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(JwtUtil jwtUtil,
                                AdminTokenStore tokenStore,
                                ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return reject(response);
        }

        String token = header.substring(PREFIX.length()).trim();
        if (jwtUtil.verify(token) == null || !tokenStore.matches(token)) {
            return reject(response);
        }
        return true;
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.TOKEN_INVALID)));
        return false;
    }
}
