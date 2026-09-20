package com.blog.common;

import lombok.Getter;

/** 业务异常，携带错误码。由 GlobalExceptionHandler 统一转译为响应体。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 用于需要覆盖默认文案的场景，例如「分类不存在」比通用的「资源不存在」更具体。 */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
