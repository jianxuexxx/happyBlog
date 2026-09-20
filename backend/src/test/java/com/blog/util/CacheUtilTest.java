package com.blog.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.blog.dto.CategoryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CacheUtilTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheUtil cacheUtil;

    @BeforeEach
    void setUp() {
        cacheUtil = new CacheUtil(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("set：写入键值并带上 TTL")
    void setWritesValueWithTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        cacheUtil.set("blog:category:list", List.of(), Duration.ofMinutes(5));

        verify(valueOperations).set("blog:category:list", List.of(), Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("get：命中时反序列化为目标类型")
    void getConvertsHit() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("categoryId", 1);
        raw.put("categoryName", "技术");
        raw.put("sortOrder", 0);
        raw.put("articleCount", 3);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("blog:category:list")).willReturn(raw);

        CategoryVO vo = cacheUtil.get("blog:category:list", CategoryVO.class);

        assertThat(vo).isNotNull();
        assertThat(vo.getCategoryName()).isEqualTo("技术");
        assertThat(vo.getArticleCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("get：未命中返回 null")
    void getReturnsNullOnMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("blog:category:list")).willReturn(null);

        assertThat(cacheUtil.get("blog:category:list", CategoryVO.class)).isNull();
    }

    @Test
    @DisplayName("getList：命中时反序列化为元素列表")
    void getListConvertsElementType() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("categoryId", 1);
        first.put("categoryName", "技术");
        first.put("sortOrder", 0);
        first.put("articleCount", 3);
        List<Object> raw = new ArrayList<>();
        raw.add(first);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("blog:category:list")).willReturn(raw);

        List<CategoryVO> list = cacheUtil.getList("blog:category:list", CategoryVO.class);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCategoryName()).isEqualTo("技术");
        assertThat(list.get(0).getArticleCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getList：未命中返回 null（而非空列表，以便调用方区分「无缓存」与「缓存了空集」）")
    void getListReturnsNullOnMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("blog:category:list")).willReturn(null);

        assertThat(cacheUtil.getList("blog:category:list", CategoryVO.class)).isNull();
    }

    @Test
    @DisplayName("delete：删除键")
    void deleteRemovesKey() {
        cacheUtil.delete("blog:category:list");

        verify(redisTemplate).delete("blog:category:list");
    }
}
