package com.blog.dto;

import lombok.Data;

/** 分类列表项。articleCount 由 CategoryMapper 的 JOIN 查询算出。 */
@Data
public class CategoryVO {

    private Long categoryId;
    private String categoryName;
    private Integer sortOrder;
    private Integer articleCount;
}
