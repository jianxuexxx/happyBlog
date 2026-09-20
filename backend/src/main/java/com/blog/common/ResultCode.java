package com.blog.common;

/**
 * 错误码。取值依据 docs/superpowers/specs/2026-09-20-backend-skeleton-design.md §6。
 * 前端 frontend/src/api/http.ts 依赖 40100 清理登录态，改动需同步前端。
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_INVALID(40001, "参数校验失败"),
    CATEGORY_NAME_EXISTS(40002, "分类名已存在"),
    TOKEN_INVALID(40100, "登录已失效，请重新登录"),
    LOGIN_FAILED(40101, "用户名或密码错误"),
    NOT_FOUND(40400, "资源不存在"),
    SERVER_ERROR(50000, "服务器开小差了"),
    UPLOAD_FAILED(50300, "文件上传失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
