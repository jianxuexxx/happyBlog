import http, { unwrapResult, type ApiResponse } from './http'

/** 文章列表项，字段与后端 com.blog.dto.ArticleListVO 一一对应 */
export interface ArticleListItem {
  articleId: number
  title: string
  /** 摘要；后端未填时为 null（article.summary 允许 NULL，后端 VO 原样透传） */
  summary: string | null
  /** 封面图路径；后端未设封面时为 null（article.coverImage 允许 NULL），前端走占位 */
  coverImage: string | null
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
  /**
   * 页码回显。注意后端**只钳下界**（小于 1 归 1），**不钳上界**：
   * 请求 page=9 而该分类只有 2 页时会原样回显 9，此时 list 为空但 total > 0。
   * 消费方不能假设「list 为空 ⇔ total 为 0」。
   */
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
