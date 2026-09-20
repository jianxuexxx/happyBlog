package com.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录返回。前端把它存进 localStorage 的 blog-admin-token。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;
}
