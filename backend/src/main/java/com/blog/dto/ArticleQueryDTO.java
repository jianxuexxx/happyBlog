package com.blog.dto;

import lombok.Data;

/**
 * 前台文章列表查询参数，全部可选。
 * 分页钳制刻意不在这里做 —— 放在 Service 层，这样绕过 Controller 的调用方（将来的
 * 管理端列表、定时任务）也受同一层保护（设计规格 §4）。
 */
@Data
public class ArticleQueryDTO {

    private Long categoryId;
    private Long tagId;
    private String keyword;
    private Boolean recommended;
    private Boolean top;
    private Integer page;
    private Integer pageSize;
}
