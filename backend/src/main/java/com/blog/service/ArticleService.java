package com.blog.service;

import com.blog.common.PageResult;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;

public interface ArticleService {

    /**
     * 前台公开文章分页列表。
     * 契约见 docs/superpowers/specs/2026-09-21-article-list-slice-design.md §3。
     */
    PageResult<ArticleListVO> list(ArticleQueryDTO query);
}
