package com.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("article")
public class Article {

    @TableId(value = "articleId", type = IdType.AUTO)
    private Long articleId;

    private String title;
    private String summary;
    private String content;
    private String coverImage;
    private Long categoryId;

    /** 0草稿 / 1公开 / 2私密 */
    private Integer status;
    private Integer isTop;
    private Integer isRecommended;
    private Integer viewCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
