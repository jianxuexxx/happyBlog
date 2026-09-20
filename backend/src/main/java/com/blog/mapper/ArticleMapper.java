package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Article;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 分类被删除时，把该分类下未删文章的 categoryId 置空（设计规格 §4 关系约束）。
     * 手写 UPDATE 不受 @TableLogic 与自动填充保护，故 deleted 条件与 updatedAt 都显式写出。
     */
    @Update("""
            UPDATE article
               SET categoryId = NULL,
                   updatedAt = NOW()
             WHERE categoryId = #{categoryId}
               AND deleted = 0
            """)
    int clearCategoryId(@Param("categoryId") Long categoryId);
}
