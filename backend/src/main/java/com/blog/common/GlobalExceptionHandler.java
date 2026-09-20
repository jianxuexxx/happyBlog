package com.blog.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 * 关键约束：不设置非 200 的 HTTP 状态码 —— 前端 axios 拦截器只在成功回调里
 * 读取响应体 code（frontend/src/api/http.ts:29-39），返回 4xx/5xx 会让前端
 * 拿不到业务错误码。错误只体现在响应体 code 字段。
 * 另一个关键约束：响应体绝不包含堆栈或 SQL 片段，完整堆栈只进日志。
 * 分层意图：客户端错误（路径/方法/请求体/参数类型）→ 4xxxx + WARN 不打堆栈；
 * 真正的未预期异常 → 50000 + ERROR 打全堆栈。精确 handler 拦在前面，catch-all 只兜未预期。
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

    /** 路径不存在。Spring Boot 3.2+ 对未匹配的请求抛此异常（含 favicon、扫描器探测）。属客户端错误。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("路径不存在: {}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /** 请求方法不匹配，例如对只支持 GET 的路径发 POST。属客户端错误。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), "请求方法不支持");
    }

    /** 请求体畸形或无法解析。属客户端错误，不该报「服务器开小差了」。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        // 只记简短原因，不打全堆栈 —— 畸形请求体是常见噪声，不是服务端故障
        log.warn("请求体无法解析: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), "请求体格式错误");
    }

    /** 路径变量或查询参数类型不匹配，例如 /api/admin/category/abc 期望 Long。属客户端错误。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={} value={}", e.getName(), e.getValue());
        return Result.fail(ResultCode.PARAM_INVALID.getCode(),
                "参数 " + e.getName() + " 类型不正确");
    }

    /** 真正的未预期异常。客户端错误应在此之前的精确 handler 里被拦下。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        // 完整堆栈只进日志，响应体只给固定友好文案
        log.error("未预期异常", e);
        return Result.fail(ResultCode.SERVER_ERROR);
    }
}
