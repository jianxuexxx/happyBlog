package com.blog.service.impl;

import com.blog.common.BizException;
import com.blog.common.ResultCode;
import com.blog.dto.CategorySaveDTO;
import com.blog.dto.CategoryVO;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.service.CategoryService;
import com.blog.util.CacheUtil;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        throw new UnsupportedOperationException("任务 8 实现");
    }

    @Override
    public void update(CategorySaveDTO dto) {
        throw new UnsupportedOperationException("任务 8 实现");
    }

    @Override
    public void delete(Long categoryId) {
        throw new UnsupportedOperationException("任务 8 实现");
    }
}
