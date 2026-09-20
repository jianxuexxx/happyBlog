package com.blog.dto;

import jakarta.validation.groups.Default;

/** JSR-380 校验分组标记。接口内不放任何成员，仅作类型标签。 */
public final class ValidateGroups {

    private ValidateGroups() {
    }

    /** 新增场景：categoryId 必须为空。 */
    public interface Create extends Default {
    }

    /** 更新场景：categoryId 必须非空。 */
    public interface Update extends Default {
    }
}
