package com.blog.service.impl;

import com.blog.common.BizException;
import com.blog.common.ResultCode;
import com.blog.dto.LoginDTO;
import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import com.blog.service.AdminAuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 单管理员鉴权（设计规格 §13 YAGNI：不建 admin 表，凭证来自配置）。
 * 凭证比对用 MessageDigest.isEqual 做常量时间比较，避免通过响应耗时逐字符
 * 爆破用户名密码。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final JwtUtil jwtUtil;
    private final AdminTokenStore tokenStore;
    private final String adminUsername;
    private final String adminPassword;

    public AdminAuthServiceImpl(JwtUtil jwtUtil,
                                AdminTokenStore tokenStore,
                                @Value("${blog.admin.username}") String adminUsername,
                                @Value("${blog.admin.password}") String adminPassword) {
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public String login(LoginDTO dto) {
        boolean usernameOk = constantTimeEquals(dto.getUsername(), adminUsername);
        boolean passwordOk = constantTimeEquals(dto.getPassword(), adminPassword);
        // 短路放在最后：两个比较都要执行完，避免用户名错误时提前返回泄露信息
        if (!usernameOk || !passwordOk) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        String token = jwtUtil.generate(adminUsername);
        tokenStore.save(token);
        return token;
    }

    @Override
    public void logout() {
        tokenStore.clear();
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
