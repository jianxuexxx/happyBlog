package com.blog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blog.common.PageResult;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import com.blog.service.ArticleService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ArticleController.class)
@ActiveProfiles("test")
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleService articleService;

    // AdminAuthInterceptor 是 @Component 且实现 HandlerInterceptor，会被 @WebMvcTest 纳入，
    // 它的两个依赖必须提供桩，否则整个切片上下文加载失败 —— 那是与 Web 层代码无关的假失败
    // （CategoryControllerTest 出于同样原因也声明了这两个字段）。
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private AdminTokenStore adminTokenStore;

    private ArticleListVO vo(long id, String title, int viewCount) {
        ArticleListVO vo = new ArticleListVO();
        vo.setArticleId(id);
        vo.setTitle(title);
        vo.setSummary("摘要 " + id);
        vo.setCoverImage("/images/cover-" + id + ".jpg");
        vo.setViewCount(viewCount);
        return vo;
    }

    @Test
    @DisplayName("列表：返回 code=0 与 PageResult 四个字段")
    void listReturnsPageResultEnvelope() throws Exception {
        given(articleService.list(any()))
                .willReturn(PageResult.of(42, 1, 10, List.of(vo(7L, "示例文章", 128))));

        mockMvc.perform(get("/api/article/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(42))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.list[0].articleId").value(7))
                .andExpect(jsonPath("$.data.list[0].title").value("示例文章"))
                .andExpect(jsonPath("$.data.list[0].viewCount").value(128));
    }

    @Test
    @DisplayName("列表：无参调用时查询参数全为 null，钳制留给 Service 层")
    void listBindsNoParamsAsNulls() throws Exception {
        given(articleService.list(any())).willReturn(PageResult.of(0, 1, 10, List.of()));

        mockMvc.perform(get("/api/article/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isArray());

        ArgumentCaptor<ArticleQueryDTO> captor = ArgumentCaptor.forClass(ArticleQueryDTO.class);
        verify(articleService).list(captor.capture());
        ArticleQueryDTO q = captor.getValue();
        assertThat(q.getCategoryId()).isNull();
        assertThat(q.getTagId()).isNull();
        assertThat(q.getKeyword()).isNull();
        assertThat(q.getRecommended()).isNull();
        assertThat(q.getTop()).isNull();
        assertThat(q.getPage()).isNull();
        assertThat(q.getPageSize()).isNull();
    }

    @Test
    @DisplayName("列表：七个查询参数都正确绑定到 DTO")
    void listBindsQueryParams() throws Exception {
        given(articleService.list(any())).willReturn(PageResult.of(0, 2, 5, List.of()));

        mockMvc.perform(get("/api/article/list")
                        .param("categoryId", "3")
                        .param("tagId", "9")
                        .param("keyword", "spring")
                        .param("recommended", "true")
                        .param("top", "true")
                        .param("page", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<ArticleQueryDTO> captor = ArgumentCaptor.forClass(ArticleQueryDTO.class);
        verify(articleService).list(captor.capture());
        ArticleQueryDTO q = captor.getValue();
        assertThat(q.getCategoryId()).isEqualTo(3L);
        assertThat(q.getTagId()).isEqualTo(9L);
        assertThat(q.getKeyword()).isEqualTo("spring");
        assertThat(q.getRecommended()).isTrue();
        assertThat(q.getTop()).isTrue();
        assertThat(q.getPage()).isEqualTo(2);
        assertThat(q.getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("列表：categoryId 非数字时返回 40001，不是 50000")
    void listRejectsNonNumericCategoryId() throws Exception {
        // 客户端错误被误报成 50000 正是主规格 §10 点名要避免的
        mockMvc.perform(get("/api/article/list").param("categoryId", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("列表：不带 token 也能访问（不在 /api/admin/** 下，拦截器不该误伤）")
    void listIsPublicWithoutToken() throws Exception {
        given(articleService.list(any())).willReturn(PageResult.of(0, 1, 10, List.of()));

        mockMvc.perform(get("/api/article/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 真正的内容是「Service 被调用到了」—— 证明拦截器没有短路这次请求，
        // 而不只是「响应里恰好没有 40100」。
        verify(articleService).list(any());
    }
}
