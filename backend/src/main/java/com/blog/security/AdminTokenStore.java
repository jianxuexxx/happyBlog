package com.blog.security;

import com.blog.util.CacheUtil;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 管理员会话存储（设计规格 §9 的 blog:admin:token 键）。
 * 单管理员模型，单值键 —— 再次登录会顶掉上一个 token。
 * 之所以在 JWT 之外另存一份：JWT 一旦签发就无法撤回，
 * 存一份才能让「登出」立即生效（校验签名 + 校验仍在存储中）。
 */
@Component
public class AdminTokenStore {

    /** 设计规格 §9 定义的键，勿改。 */
    public static final String KEY = "blog:admin:token";

    private final CacheUtil cacheUtil;
    private final JwtUtil jwtUtil;

    public AdminTokenStore(CacheUtil cacheUtil, JwtUtil jwtUtil) {
        this.cacheUtil = cacheUtil;
        this.jwtUtil = jwtUtil;
    }

    public void save(String token) {
        cacheUtil.set(KEY, token, jwtUtil.getTtl());
    }

    public boolean matches(String token) {
        return Objects.equals(token, cacheUtil.get(KEY, String.class));
    }

    public void clear() {
        cacheUtil.delete(KEY);
    }
}
