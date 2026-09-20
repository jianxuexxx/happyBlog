package com.blog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 元信息。UI 在 /swagger-ui.html，JSON 在 /v3/api-docs。
 * 导出该 JSON 是为了统一交付接口文档（Apifox 等工具可导入），
 * 避免手工维护接口清单与代码脱节。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI myBlogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("我的博客系统 API")
                .description("个人博客后端接口。管理端接口需在 Authorization 头携带 Bearer token。"
                        + "所有响应 HTTP 状态码均为 200，业务结果见响应体 code 字段。")
                .version("0.0.1-SNAPSHOT"));
    }
}
