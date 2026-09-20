package com.blog.common;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页返回体。本切片不产生分页数据（分类量级小），
 * 此类型为后续 article 列表预留，同时在设计规格 §8 中已定义。
 */
@Getter
@Setter
public class PageResult<T> {

    private long total;
    private long page;
    private long pageSize;
    private List<T> list;

    public static <T> PageResult<T> of(long total, long page, long pageSize, List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setList(list);
        return result;
    }
}
