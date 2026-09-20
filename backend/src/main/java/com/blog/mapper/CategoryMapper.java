package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.dto.CategoryVO;
import com.blog.entity.Category;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 分类列表含文章数。逻辑删除条件必须显式写进 JOIN 条件 ——
     * @TableLogic 只对 MyBatis-Plus 生成的 SQL 生效，手写 SQL 不受其保护。
     * 若把 a.deleted = 0 写进 WHERE，会把「分类下文章全被删」的分类也过滤掉。
     */
    @Select("""
            SELECT c.categoryId, c.categoryName, c.sortOrder,
                   COUNT(a.articleId) AS articleCount
            FROM category c
            LEFT JOIN article a
                   ON a.categoryId = c.categoryId
                  AND a.deleted = 0
            WHERE c.deleted = 0
            GROUP BY c.categoryId, c.categoryName, c.sortOrder
            ORDER BY c.sortOrder ASC, c.categoryId ASC
            """)
    List<CategoryVO> selectCategoryWithArticleCount();
}
