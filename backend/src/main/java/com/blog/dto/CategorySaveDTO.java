package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增/更新入参。
 * categoryId 仅在更新时必填，用校验分组区分两种场景：
 * 新增时 @Null（必须为空），更新时 @NotNull。
 */
@Data
public class CategorySaveDTO {

    @Null(groups = ValidateGroups.Create.class, message = "新增时不允许指定分类ID")
    @NotNull(groups = ValidateGroups.Update.class, message = "更新时必须指定分类ID")
    private Long categoryId;

    @NotBlank(message = "分类名不能为空")
    @Size(max = 50, message = "分类名长度不能超过 50")
    private String categoryName;

    private Integer sortOrder;
}
