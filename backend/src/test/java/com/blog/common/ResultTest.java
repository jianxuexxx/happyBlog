package com.blog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultTest {

    @Test
    @DisplayName("ok 带数据：code 为 0，message 为 success，data 为传入值")
    void okWithData() {
        Result<String> result = Result.ok("hello");

        assertThat(result.getCode()).isZero();
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("ok 不带数据：data 为 null")
    void okWithoutData() {
        Result<Void> result = Result.ok();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("fail 用枚举：携带枚举的 code 与 message，data 为 null")
    void failWithResultCode() {
        Result<Void> result = Result.fail(ResultCode.CATEGORY_NAME_EXISTS);

        assertThat(result.getCode()).isEqualTo(40002);
        assertThat(result.getMessage()).isEqualTo("分类名已存在");
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("fail 用自定义文案：code 取自枚举，message 用传入值")
    void failWithCustomMessage() {
        Result<Void> result = Result.fail(ResultCode.NOT_FOUND.getCode(), "分类不存在");

        assertThat(result.getCode()).isEqualTo(40400);
        assertThat(result.getMessage()).isEqualTo("分类不存在");
    }
}
