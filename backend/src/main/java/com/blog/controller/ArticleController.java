package com.blog.controller;

import com.blog.common.PageResult;
import com.blog.common.Result;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开的文章接口。
 * 路径显式带 /api 前缀：前端 vite 代理 '/api' 无 rewrite，原样转发到本服务的 18088 端口。
 * 不在 /api/admin/** 下，故不受 AdminAuthInterceptor 保护（WebMvcConfig 只拦 admin 前缀）。
 */
@Tag(name = "文章（前台）")
@RestController
@RequestMapping("/api/article")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    /**
     * 参数不带 @RequestParam 注解：Spring MVC 会把查询串绑定到这个 POJO 上。
     * 分页与筛选的钳制一律在 Service 层做，此处只做转发（设计规格 §4）。
     */
    @Operation(summary = "公开文章分页列表（categoryId/tagId/keyword/recommended/top + page/pageSize）")
    @GetMapping("/list")
    public Result<PageResult<ArticleListVO>> list(ArticleQueryDTO query) {
        return Result.ok(articleService.list(query));
    }
}
