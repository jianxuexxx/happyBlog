package com.blog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 错误码是跨前后端的契约（前端 http.ts 硬编码依赖 40100）。
 * 此测试锁死取值，防止有人「顺手」改号。
 */
class ResultCodeTest {

    @Test
    @DisplayName("错误码取值与设计规格 §6 一致")
    void codesMatchDesignSpec() {
        assertThat(ResultCode.SUCCESS.getCode()).isZero();
        assertThat(ResultCode.PARAM_INVALID.getCode()).isEqualTo(40001);
        assertThat(ResultCode.CATEGORY_NAME_EXISTS.getCode()).isEqualTo(40002);
        assertThat(ResultCode.TOKEN_INVALID.getCode()).isEqualTo(40100);
        assertThat(ResultCode.LOGIN_FAILED.getCode()).isEqualTo(40101);
        assertThat(ResultCode.NOT_FOUND.getCode()).isEqualTo(40400);
        assertThat(ResultCode.SERVER_ERROR.getCode()).isEqualTo(50000);
        assertThat(ResultCode.UPLOAD_FAILED.getCode()).isEqualTo(50300);
    }

    @Test
    @DisplayName("成功码的文案为 success，前端以此为成功判据")
    void successMessageIsSuccess() {
        assertThat(ResultCode.SUCCESS.getMessage()).isEqualTo("success");
    }
}
