import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import { defineComponent, h } from 'vue'
// el-pagination 在 main.ts 里是全局注册的，测试环境必须自己装上，
// 否则 Vue 只会打一条 "Failed to resolve component" 警告并渲染成空标签，
// 翻页用例会因为找不到 .el-pager li 而红——而那是环境问题，不是组件问题。
import ElementPlus from 'element-plus'
import Category from '../views/Category.vue'
import { fetchArticleList, type ArticleListItem, type PageResult } from '../api/article'
import { fetchCategoryList } from '../api/category'

// 只桩掉取数函数：组件行为（三态、翻页、路由参数响应）才是被测对象。
vi.mock('../api/article', () => ({ fetchArticleList: vi.fn() }))
vi.mock('../api/category', () => ({ fetchCategoryList: vi.fn() }))

const mockedArticles = vi.mocked(fetchArticleList)
const mockedCategories = vi.mocked(fetchCategoryList)

const ROWS: ArticleListItem[] = [
  {
    articleId: 7,
    title: '第一篇',
    summary: '摘要一',
    coverImage: '',
    createdAt: '2026-09-21T14:30:00',
    viewCount: 128,
  },
  {
    articleId: 8,
    title: '第二篇',
    summary: '摘要二',
    coverImage: '/images/cover-8.jpg',
    createdAt: '2026-09-20T09:00:00',
    viewCount: 3,
  },
]

function page(list: ArticleListItem[], total = list.length, pageNo = 1, pageSize = 10) {
  return { total, page: pageNo, pageSize, list }
}

/** 手动控制解决时机的 promise：用来制造「先发的请求后返回」的乱序。 */
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

/** 用于乱序用例的两份可区分数据；coverImage 取 null，顺带覆盖「后端未设封面」这条真实形态。 */
const FRESH_ROW: ArticleListItem = {
  articleId: 7,
  title: '第一页文章',
  summary: '摘要一',
  coverImage: null,
  createdAt: '2026-09-21T14:30:00',
  viewCount: 1,
}

const STALE_ROW: ArticleListItem = {
  articleId: 99,
  title: '过期页文章',
  summary: '摘要九十九',
  coverImage: null,
  createdAt: '2026-09-21T14:30:00',
  viewCount: 1,
}

const Blank = defineComponent({ render: () => h('div') })

async function mountAt(path: string): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Blank },
      { path: '/article/:articleId', component: Blank },
      { path: '/category/:id', component: Category },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(Category, {
    global: { plugins: [router, ElementPlus] },
  })
  return { wrapper, router }
}

describe('Category 分类页', () => {
  beforeEach(() => {
    mockedArticles.mockReset()
    mockedCategories.mockReset()
    mockedCategories.mockResolvedValue([])
  })

  it('渲染后端返回的文章卡片', async () => {
    mockedArticles.mockResolvedValue(page(ROWS))
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('第一篇')
    expect(wrapper.text()).toContain('第二篇')
    expect(wrapper.text()).not.toContain('暂无文章')
  })

  it('把路由里的 categoryId 作为筛选条件传给后端', async () => {
    mockedArticles.mockResolvedValue(page(ROWS))
    await mountAt('/category/3')
    await flushPromises()

    expect(mockedArticles).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 3, page: 1 }))
  })

  it('空结果显示空态而不是错误态', async () => {
    mockedArticles.mockResolvedValue(page([], 0))
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('暂无文章')
    expect(wrapper.text()).not.toContain('加载失败')
  })

  it('取数失败显示错误态与后端 message，重试可恢复', async () => {
    mockedArticles.mockRejectedValueOnce(new Error('服务暂不可用'))
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).toContain('服务暂不可用')

    mockedArticles.mockResolvedValueOnce(page(ROWS))
    await wrapper.get('.category-retry').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('加载失败')
    expect(wrapper.text()).toContain('第一篇')
  })

  it('路由参数从 /category/1 变到 /category/2 时重新取数', async () => {
    // 两个路径命中同一个组件实例，Vue 会复用而不重建 —— 只在 onMounted 里取数的写法
    // 在这里必红（页面不会变）。这是本用例专门守住的坑（规格 §5）。
    mockedArticles.mockResolvedValue(page(ROWS))
    const { router } = await mountAt('/category/1')
    await flushPromises()

    expect(mockedArticles).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 1 }))

    await router.push('/category/2')
    await flushPromises()

    expect(mockedArticles).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 2 }))
  })

  it('切分类时页码归 1（URL 上带着旧页码的情况）', async () => {
    // 刻意用 /category/2?page=2 而不是干净的 /category/2：后者本身就不带 query，
    // 页码自然没了，等于没测到这个风险。真正的风险路径是手改地址栏、浏览器前进后退、
    // 或将来某个带 query 的分类链接 —— 那样会去请求新分类的第 2 页，很可能直接空列表。
    mockedArticles.mockResolvedValue(page(ROWS, 50, 2))
    const { router } = await mountAt('/category/1?page=2')
    await flushPromises()

    mockedArticles.mockClear()
    mockedArticles.mockResolvedValue(page(ROWS))

    await router.push('/category/2?page=2')
    // 这里有两个串联的异步跳：先按新分类发一次请求，归 1 的 watcher 再 push 一次
    // 清掉 query，引发第二次请求。所以 flushPromises 要跟两次，否则断言时机太早。
    await flushPromises()
    await flushPromises()

    // 用 LastCalledWith：中间那次 page=2 的请求是这条链路的副产品，最终落定的那次才是要断言的。
    expect(mockedArticles).toHaveBeenLastCalledWith(
      expect.objectContaining({ categoryId: 2, page: 1 }),
    )
  })

  it('乱序响应：先发的请求后返回时不覆盖后发请求的结果', async () => {
    // 上面那条链路里两个请求是赛跑的。测试默认让它们按发起顺序返回，于是竞态被掩盖；
    // 这里刻意让**先发的那个后返回**：没有请求序号守卫时，慢响应会盖掉新结果，
    // 用户看到的是新分类第 2 页的文章，而 URL 上已经没有 page、分页器高亮第 1 页。
    mockedArticles.mockResolvedValue(page(ROWS, 50, 2)) // 挂载那次（category 1, page 2）
    const { wrapper, router } = await mountAt('/category/1?page=2')
    await flushPromises()

    const stale = deferred<PageResult<ArticleListItem>>() // 先发：categoryId 2, page 2
    const fresh = deferred<PageResult<ArticleListItem>>() // 后发：categoryId 2, page 1
    mockedArticles.mockReturnValueOnce(stale.promise)
    mockedArticles.mockReturnValueOnce(fresh.promise)

    await router.push('/category/2?page=2')
    await flushPromises()
    await flushPromises()

    // 前置条件：两个请求确实都已发出且都还悬着（否则本用例根本没测到竞态）。
    // 同时钉死发起顺序，防止将来 watcher 顺序变化后这里静默测反。
    expect(mockedArticles).toHaveBeenCalledTimes(3)
    expect(mockedArticles).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ categoryId: 2, page: 2 }),
    )
    expect(mockedArticles).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({ categoryId: 2, page: 1 }),
    )

    fresh.resolve(page([FRESH_ROW], 20, 1)) // 后发的先回
    await flushPromises()
    stale.resolve(page([STALE_ROW], 50, 2)) // 先发的后回
    await flushPromises()

    expect(wrapper.text()).toContain('第一页文章')
    expect(wrapper.text()).not.toContain('过期页文章')
    // total 也不能被旧响应改回去（否则页头篇数与分页器页数都会跟着错）
    expect(wrapper.text()).toContain('共 20 篇')
    expect(wrapper.text()).not.toContain('共 50 篇')
  })

  it('翻页把页码写进 URL', async () => {
    mockedArticles.mockResolvedValue(page(ROWS, 30, 1))
    const { wrapper, router } = await mountAt('/category/3')
    await flushPromises()

    // el-pagination 的第 2 页按钮
    const secondPage = wrapper.findAll('.el-pager li').find((li) => li.text() === '2')
    expect(secondPage).toBeTruthy()
    await secondPage!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('2')
  })

  it('页码超出末页（当前页为空但 total > 0）时仍渲染分页器', async () => {
    // 触发路径：手改地址栏 / 旧书签 / 被分享收录的深链打开 ?page=9，而该分类只有 2 页（20 篇）。
    // 后端只钳下界不钳上界，page=9 原样回显 → records 为空但 total=20。
    // 分页器若长在「列表非空」的分支里，此时整页没有任何翻页控件，用户回不到第 1 页（死胡同）。
    mockedArticles.mockResolvedValue(page([], 20, 9))
    const { wrapper } = await mountAt('/category/3?page=9')
    await flushPromises()

    expect(wrapper.text()).toContain('共 20 篇')
    expect(wrapper.text()).toContain('暂无文章')

    // 关键断言：空列表时翻页控件仍在，并且给出了回到第 1 页的路
    const pagerItems = wrapper.findAll('.el-pager li')
    expect(pagerItems.length).toBeGreaterThan(0)
    expect(pagerItems.map((li) => li.text())).toContain('1')
  })

  it('非法 page 退化为第 1 页', async () => {
    // ?page= 是用户可手改的（规格 §5）
    mockedArticles.mockResolvedValue(page(ROWS))
    await mountAt('/category/3?page=abc')
    await flushPromises()

    expect(mockedArticles).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
  })

  it('用分类名做页头标题', async () => {
    mockedArticles.mockResolvedValue(page(ROWS))
    mockedCategories.mockResolvedValue([
      { categoryId: 3, categoryName: '技术', sortOrder: 1, articleCount: 2 },
    ])
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('技术')
  })

  it('分类名取不到时退化为「分类」二字，且不影响文章列表渲染', async () => {
    // 分类名只是标题装饰，为它把整页拖垮不划算（规格 §5）
    mockedArticles.mockResolvedValue(page(ROWS))
    mockedCategories.mockRejectedValue(new Error('分类接口挂了'))
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('分类')
    expect(wrapper.text()).toContain('第一篇')
    expect(wrapper.text()).not.toContain('分类接口挂了')
  })
})
