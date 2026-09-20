package com.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.blog.dto.CategoryVO;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.util.CacheUtil;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private CacheUtil cacheUtil;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryMapper, articleMapper, cacheUtil);
    }

    private CategoryVO vo(long id, String name, int sortOrder, int articleCount) {
        CategoryVO vo = new CategoryVO();
        vo.setCategoryId(id);
        vo.setCategoryName(name);
        vo.setSortOrder(sortOrder);
        vo.setArticleCount(articleCount);
        return vo;
    }

    @Test
    @DisplayName("列表：缓存命中时直接返回，不查库")
    void listReturnsFromCacheWithoutHittingDb() {
        List<CategoryVO> cached = List.of(vo(1L, "技术", 0, 3));
        given(cacheUtil.getList("blog:category:list", CategoryVO.class)).willReturn(cached);

        List<CategoryVO> result = service.list();

        assertThat(result).isEqualTo(cached);
        verify(categoryMapper, never()).selectCategoryWithArticleCount();
    }

    @Test
    @DisplayName("列表：缓存未命中时查库并以 5 分钟 TTL 写入设计规格定义的键")
    void listLoadsFromDbAndCachesOnMiss() {
        List<CategoryVO> fromDb = List.of(vo(1L, "技术", 0, 3), vo(2L, "生活", 1, 0));
        given(cacheUtil.getList("blog:category:list", CategoryVO.class)).willReturn(null);
        given(categoryMapper.selectCategoryWithArticleCount()).willReturn(fromDb);

        List<CategoryVO> result = service.list();

        assertThat(result).isEqualTo(fromDb);
        verify(cacheUtil).set("blog:category:list", fromDb, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("列表：库中无数据时也写缓存（空集也要缓存，避免每次穿透）")
    void listCachesEmptyResult() {
        List<CategoryVO> empty = List.of();
        given(cacheUtil.getList("blog:category:list", CategoryVO.class)).willReturn(null);
        given(categoryMapper.selectCategoryWithArticleCount()).willReturn(empty);

        List<CategoryVO> result = service.list();

        assertThat(result).isEmpty();
        verify(cacheUtil).set("blog:category:list", empty, Duration.ofMinutes(5));
    }
}
