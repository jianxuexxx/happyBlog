package com.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.blog.common.BizException;
import com.blog.dto.CategorySaveDTO;
import com.blog.dto.CategoryVO;
import com.blog.entity.Category;
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

    // ---------- 写链路 ----------

    private CategorySaveDTO saveDto(Long id, String name, Integer sortOrder) {
        CategorySaveDTO dto = new CategorySaveDTO();
        dto.setCategoryId(id);
        dto.setCategoryName(name);
        dto.setSortOrder(sortOrder);
        return dto;
    }

    @Test
    @DisplayName("新增：名称已存在抛 40002")
    void createRejectsDuplicateName() {
        given(categoryMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> service.create(saveDto(null, "技术", 0)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40002));
    }

    @Test
    @DisplayName("新增：成功时返回自增主键并清除列表缓存")
    void createReturnsIdAndEvictsCache() {
        given(categoryMapper.selectCount(any())).willReturn(0L);
        doAnswer(invocation -> {
            ((Category) invocation.getArgument(0)).setCategoryId(9L);
            return 1;
        }).when(categoryMapper).insert(any(Category.class));

        Long id = service.create(saveDto(null, "技术", 5));

        assertThat(id).isEqualTo(9L);
        verify(cacheUtil).delete("blog:category:list");
    }

    @Test
    @DisplayName("新增：sortOrder 为空时落库为 0")
    void createDefaultsSortOrderToZero() {
        given(categoryMapper.selectCount(any())).willReturn(0L);

        service.create(saveDto(null, "技术", null));

        verify(categoryMapper).insert(org.mockito.ArgumentMatchers.argThat(
                (Category c) -> c.getSortOrder() == 0));
    }

    @Test
    @DisplayName("更新：分类不存在抛 40400")
    void updateRejectsMissingCategory() {
        given(categoryMapper.selectById(9L)).willReturn(null);

        assertThatThrownBy(() -> service.update(saveDto(9L, "技术", 0)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40400));
    }

    @Test
    @DisplayName("更新：与另一个分类重名抛 40002")
    void updateRejectsNameTakenByAnotherCategory() {
        Category existing = new Category();
        existing.setCategoryId(9L);
        given(categoryMapper.selectById(9L)).willReturn(existing);
        given(categoryMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> service.update(saveDto(9L, "生活", 0)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40002));
    }

    @Test
    @DisplayName("更新：成功时清除列表缓存")
    void updateEvictsCache() {
        Category existing = new Category();
        existing.setCategoryId(9L);
        given(categoryMapper.selectById(9L)).willReturn(existing);
        given(categoryMapper.selectCount(any())).willReturn(0L);

        service.update(saveDto(9L, "技术", 3));

        verify(categoryMapper).updateById(any(Category.class));
        verify(cacheUtil).delete("blog:category:list");
    }

    @Test
    @DisplayName("删除：分类不存在抛 40400")
    void deleteRejectsMissingCategory() {
        given(categoryMapper.selectById(9L)).willReturn(null);

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40400));
    }

    @Test
    @DisplayName("删除：逻辑删除分类、把该分类下文章的 categoryId 置空、并清除列表缓存")
    void deleteClearsArticleReferencesAndEvictsCache() {
        Category existing = new Category();
        existing.setCategoryId(9L);
        given(categoryMapper.selectById(9L)).willReturn(existing);

        service.delete(9L);

        verify(categoryMapper).deleteById(9L);
        verify(articleMapper).clearCategoryId(9L);
        verify(cacheUtil).delete("blog:category:list");
    }
}
