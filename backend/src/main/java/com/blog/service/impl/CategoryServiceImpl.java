package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blog.common.BizException;
import com.blog.common.ResultCode;
import com.blog.dto.CategorySaveDTO;
import com.blog.dto.CategoryVO;
import com.blog.entity.Category;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.service.CategoryService;
import com.blog.util.CacheUtil;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    /** 设计规格 §8 定义的键，勿改。 */
    static final String CACHE_KEY = "blog:category:list";

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;
    private final CacheUtil cacheUtil;

    public CategoryServiceImpl(CategoryMapper categoryMapper,
                               ArticleMapper articleMapper,
                               CacheUtil cacheUtil) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
        this.cacheUtil = cacheUtil;
    }

    @Override
    public List<CategoryVO> list() {
        List<CategoryVO> cached = cacheUtil.getList(CACHE_KEY, CategoryVO.class);
        if (cached != null) {
            return cached;
        }
        List<CategoryVO> list = categoryMapper.selectCategoryWithArticleCount();
        cacheUtil.set(CACHE_KEY, list, CACHE_TTL);
        return list;
    }

    @Override
    public Long create(CategorySaveDTO dto) {
        if (existsByName(dto.getCategoryName(), null)) {
            throw new BizException(ResultCode.CATEGORY_NAME_EXISTS);
        }
        Category entity = new Category();
        entity.setCategoryName(dto.getCategoryName());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        categoryMapper.insert(entity);
        cacheUtil.delete(CACHE_KEY);
        return entity.getCategoryId();
    }

    @Override
    public void update(CategorySaveDTO dto) {
        Category existing = categoryMapper.selectById(dto.getCategoryId());
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        }
        if (existsByName(dto.getCategoryName(), dto.getCategoryId())) {
            throw new BizException(ResultCode.CATEGORY_NAME_EXISTS);
        }
        Category entity = new Category();
        entity.setCategoryId(dto.getCategoryId());
        entity.setCategoryName(dto.getCategoryName());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        categoryMapper.updateById(entity);
        cacheUtil.delete(CACHE_KEY);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long categoryId) {
        Category existing = categoryMapper.selectById(categoryId);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        }
        // 逻辑删除（@TableLogic 生效）
        categoryMapper.deleteById(categoryId);
        // 该分类下文章的 categoryId 置空（设计规格 §4 关系约束），与上面同事务
        articleMapper.clearCategoryId(categoryId);
        cacheUtil.delete(CACHE_KEY);
    }

    /**
     * 分类名是否已被占用。
     * excludeId 用于更新场景排除自身。
     * 唯一性只由本方法保证：category 表刻意不建唯一索引（「列 + deleted」的组合唯一键只能
     * 容纳一行 deleted=1，撑不起删除历史，同名记录的第二次逻辑删除会抛 MySQL 1062）。
     * 已逻辑删除的记录不参与判重，因此删掉分类后可以同名重建（设计规格 §4）。
     */
    private boolean existsByName(String categoryName, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.<Category>lambdaQuery()
                .eq(Category::getCategoryName, categoryName);
        if (excludeId != null) {
            wrapper.ne(Category::getCategoryId, excludeId);
        }
        return categoryMapper.selectCount(wrapper) > 0;
    }
}
