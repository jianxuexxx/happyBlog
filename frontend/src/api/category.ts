import http, { unwrapResult, type ApiResponse } from './http'

/** 分类列表项，字段与后端 com.blog.dto.CategoryVO 一一对应 */
export interface CategoryItem {
  categoryId: number
  categoryName: string
  /** 排序值，后端已按它排好序，前端不再重排 */
  sortOrder: number
  /** 该分类下未删除的文章数，由后端 JOIN 算出 */
  articleCount: number
}

/**
 * 公开分类列表（含文章数）。
 * 后端：GET /api/category/list → Result<List<CategoryVO>>，无需登录。
 */
export async function fetchCategoryList(): Promise<CategoryItem[]> {
  const res = await http.get<ApiResponse<CategoryItem[]>>('/category/list')
  return unwrapResult(res.data)
}
