package com.blog.service;

import com.blog.dto.CategorySaveDTO;
import com.blog.dto.CategoryVO;
import java.util.List;

public interface CategoryService {

    /** 分类列表含文章数，走 Redis 缓存。 */
    List<CategoryVO> list();

    /** 新增，返回新建的分类 ID。 */
    Long create(CategorySaveDTO dto);

    void update(CategorySaveDTO dto);

    /** 逻辑删除分类，并把该分类下文章的 categoryId 置空。 */
    void delete(Long categoryId);
}
