package com.blog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blog.dto.CategoryVO;
import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import com.blog.service.CategoryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CategoryController.class, AdminCategoryController.class})
@ActiveProfiles("test")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private AdminTokenStore adminTokenStore;

    private void givenValidToken() {
        given(jwtUtil.verify("good-token")).willReturn("admin");
        given(adminTokenStore.matches("good-token")).willReturn(true);
    }

    @Test
    @DisplayName("公开列表：返回 code=0，data 元素含 articleCount")
    void listReturnsArticleCount() throws Exception {
        CategoryVO vo = new CategoryVO();
        vo.setCategoryId(1L);
        vo.setCategoryName("技术");
        vo.setSortOrder(0);
        vo.setArticleCount(3);
        given(categoryService.list()).willReturn(List.of(vo));

        mockMvc.perform(get("/api/category/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].categoryId").value(1))
                .andExpect(jsonPath("$.data[0].categoryName").value("技术"))
                .andExpect(jsonPath("$.data[0].articleCount").value(3));
    }

    @Test
    @DisplayName("管理端新增：无 token 时 HTTP 200 且 code=40100")
    void adminCreateWithoutTokenIsRejected() throws Exception {
        // HTTP 200 是断言的一部分：前端 axios 只在成功回调里读取 40100
        mockMvc.perform(post("/api/admin/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"技术\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("管理端新增：带有效 token 时返回新建的 categoryId")
    void adminCreateWithTokenSucceeds() throws Exception {
        givenValidToken();
        given(categoryService.create(any())).willReturn(9L);

        mockMvc.perform(post("/api/admin/category")
                        .header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"技术\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(9));

        verify(categoryService).create(any());
    }

    @Test
    @DisplayName("管理端新增：分类名为空时参数校验失败 code=40001")
    void adminCreateValidatesBlankName() throws Exception {
        givenValidToken();

        mockMvc.perform(post("/api/admin/category")
                        .header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("categoryName")));
    }

    @Test
    @DisplayName("管理端新增：请求体带 categoryId 时校验失败（新增场景该字段必须为空）")
    void adminCreateRejectsProvidedId() throws Exception {
        givenValidToken();

        mockMvc.perform(post("/api/admin/category")
                        .header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":5,\"categoryName\":\"技术\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("管理端删除：带有效 token 时成功")
    void adminDeleteWithTokenSucceeds() throws Exception {
        givenValidToken();

        mockMvc.perform(delete("/api/admin/category/9")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(categoryService).delete(9L);
    }

    @Test
    @DisplayName("管理端删除：无 token 时被拦截，Service 不被调用")
    void adminDeleteWithoutTokenDoesNotReachService() throws Exception {
        mockMvc.perform(delete("/api/admin/category/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));

        verify(categoryService, org.mockito.Mockito.never()).delete(any());
    }
}
