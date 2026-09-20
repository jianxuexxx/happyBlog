package com.blog.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 * 关键约束：不设置非 200 的 HTTP 状态码 —— 前端 axios 拦截器只在成功回调里
 * 读取响应体 code（frontend/src/api/http.ts:29-39），返回 4xx/5xx 会让前端
 * 拿不到业务错误码。错误只体现在响应体 code 字段。
 * 另一个关键约束：响应体绝不包含堆栈或 SQL 片段，完整堆栈只进日志。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? ResultCode.PARAM_INVALID.getMessage()
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        // 完整堆栈只进日志，响应体只给固定友好文案
        log.error("未预期异常", e);
        return Result.fail(ResultCode.SERVER_ERROR);
    }
}
