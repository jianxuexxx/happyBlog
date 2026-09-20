package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.CategoryVO;
import com.blog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开的分类接口。
 * 路径显式带 /api 前缀：前端 vite 代理 '/api' 无 rewrite，原样转发到本服务的 18088 端口。
 */
@Tag(name = "分类（前台）")
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "分类列表（含文章数，走缓存）")
    @GetMapping("/list")
    public Result<List<CategoryVO>> list() {
        return Result.ok(categoryService.list());
    }
}
