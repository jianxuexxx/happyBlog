package com.blog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常：原样转译 code 与 message")
    void handlesBizException() {
        Result<Void> result = handler.handleBiz(new BizException(ResultCode.CATEGORY_NAME_EXISTS));

        assertThat(result.getCode()).isEqualTo(40002);
        assertThat(result.getMessage()).isEqualTo("分类名已存在");
    }

    @Test
    @DisplayName("业务异常可覆盖文案")
    void handlesBizExceptionWithCustomMessage() {
        Result<Void> result = handler.handleBiz(new BizException(ResultCode.NOT_FOUND, "分类不存在"));

        assertThat(result.getCode()).isEqualTo(40400);
        assertThat(result.getMessage()).isEqualTo("分类不存在");
    }

    @Test
    @DisplayName("参数校验失败：code 为 40001，message 含字段名")
    void handlesValidationException() throws Exception {
        LoginDTOForTest target = new LoginDTOForTest();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "dto");
        bindingResult.addError(new FieldError("dto", "username", "不能为空"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        Result<Void> result = handler.handleValidation(ex);

        assertThat(result.getCode()).isEqualTo(40001);
        assertThat(result.getMessage()).contains("username").contains("不能为空");
    }

    @Test
    @DisplayName("未知异常：code 为 50000，且响应体不泄漏堆栈信息")
    void handlesUnknownExceptionWithoutLeakingStackTrace() {
        RuntimeException ex = new RuntimeException("jdbc connection refused: password=secret");

        Result<Void> result = handler.handleUnknown(ex);

        assertThat(result.getCode()).isEqualTo(50000);
        assertThat(result.getMessage()).isEqualTo("服务器开小差了");
        assertThat(result.getMessage()).doesNotContain("jdbc").doesNotContain("password");
        assertThat(result.getData()).isNull();
    }

    /** MethodArgumentNotValidException 需要一个非 null 的目标对象承载 bindingResult。 */
    static class LoginDTOForTest {
        private String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }
}
