package com.blog.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文章列表项（卡片字段）。
 * 刻意不含 content（LONGTEXT）与 deleted —— 列表用不着，带上只是徒增载荷与泄漏面。
 * createdAt 由 Jackson 按 Spring Boot 默认的 ISO-8601 序列化（如 2026-09-21T14:30:00）：
 * 这是 JS 各引擎都能正确解析的格式，而 "yyyy-MM-dd HH:mm:ss" 在 Safari 上会得到
 * Invalid Date（设计规格 §2 有完整理由，别改成 @JsonFormat）。
 */
@Data
public class ArticleListVO {

    private Long articleId;
    private String title;
    private String summary;
    private String coverImage;
    private LocalDateTime createdAt;
    private Integer viewCount;
}
