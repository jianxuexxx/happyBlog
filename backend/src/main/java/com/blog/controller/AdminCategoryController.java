package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.CategorySaveDTO;
import com.blog.dto.ValidateGroups;
import com.blog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端分类接口。整个 /api/admin/** 由 AdminAuthInterceptor 保护。 */
@Tag(name = "分类（管理端）")
@RestController
@RequestMapping("/api/admin/category")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "新增分类，返回新建的分类 ID")
    @PostMapping
    public Result<Long> create(@Validated(ValidateGroups.Create.class) @RequestBody CategorySaveDTO dto) {
        return Result.ok(categoryService.create(dto));
    }

    @Operation(summary = "更新分类")
    @PutMapping
    public Result<Void> update(@Validated(ValidateGroups.Update.class) @RequestBody CategorySaveDTO dto) {
        categoryService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "逻辑删除分类，并把该分类下文章的分类置空")
    @DeleteMapping("/{categoryId}")
    public Result<Void> delete(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
        return Result.ok();
    }
}
