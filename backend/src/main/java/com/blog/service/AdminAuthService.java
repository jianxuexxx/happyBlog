package com.blog.service;

import com.blog.dto.LoginDTO;

public interface AdminAuthService {

    /** 校验凭证，成功返回 JWT，失败抛 BizException(40101)。 */
    String login(LoginDTO dto);

    /** 登出，使当前 token 立即失效。 */
    void logout();
}
