import http, { unwrapResult, type ApiResponse } from './http'

/** 文章列表项，字段与后端 com.blog.dto.ArticleListVO 一一对应 */
export interface ArticleListItem {
  articleId: number
  title: string
  summary: string
  /** 封面图路径；后端可能返回空串，前端走占位 */
  coverImage: string
  /**
   * ISO-8601，如 2026-09-21T14:30:00。
   * 后端刻意保留 Spring Boot 默认格式（规格 §2）：各 JS 引擎都能正确解析，
   * 而 "yyyy-MM-dd HH:mm:ss" 在 Safari 上会得到 Invalid Date。
   */
  createdAt: string
  viewCount: number
}

/** 分页返回体，字段与后端 com.blog.common.PageResult 一一对应 */
export interface PageResult<T> {
  total: number
  /** 后端回显的是钳制生效后的值，不是请求值 */
  page: number
  pageSize: number
  list: T[]
}

/** 查询参数，与后端 com.blog.dto.ArticleQueryDTO 一一对应；全部可选 */
export interface ArticleQuery {
  categoryId?: number
  tagId?: number
  keyword?: string
  /** 仅 true 生效；false 与不传等价，都表示不筛选 */
  recommended?: boolean
  /** 仅 true 生效；false 与不传等价，都表示不筛选 */
  top?: boolean
  page?: number
  pageSize?: number
}

/**
 * 公开文章分页列表。
 * 后端：GET /api/article/list → Result<PageResult<ArticleListVO>>，无需登录。
 */
export async function fetchArticleList(
  query: ArticleQuery = {},
): Promise<PageResult<ArticleListItem>> {
  const res = await http.get<ApiResponse<PageResult<ArticleListItem>>>('/article/list', {
    params: query,
  })
  return unwrapResult(res.data)
}
