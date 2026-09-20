import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import SidebarCategories from '../components/home/SidebarCategories.vue'
import { fetchCategoryList, type CategoryItem } from '../api/category'

// 只桩掉取数函数：组件行为（加载/错误/空/列表四态）才是被测对象。
vi.mock('../api/category', () => ({ fetchCategoryList: vi.fn() }))

const mockedFetch = vi.mocked(fetchCategoryList)

const ROWS: CategoryItem[] = [
  { categoryId: 3, categoryName: '技术', sortOrder: 1, articleCount: 12 },
  { categoryId: 5, categoryName: '随笔', sortOrder: 2, articleCount: 0 },
]

function mountSidebar() {
  return mount(SidebarCategories, {
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('SidebarCategories 分类卡片', () => {
  beforeEach(() => {
    mockedFetch.mockReset()
  })

  it('加载中先显示占位，不发散成空态', async () => {
    mockedFetch.mockReturnValue(new Promise(() => {})) // 永不 resolve
    const wrapper = mountSidebar()

    expect(wrapper.text()).toContain('加载中')
    expect(wrapper.text()).not.toContain('暂无分类')
  })

  it('渲染后端返回的分类名与文章数', async () => {
    mockedFetch.mockResolvedValue(ROWS)
    const wrapper = mountSidebar()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('技术')
    expect(text).toContain('随笔')
    expect(text).toContain('12')
    // 文章数为 0 也要渲染出这一项，不能当成"空"过滤掉
    expect(text).not.toContain('暂无分类')
  })

  it('每个分类链到 /category/{categoryId}', async () => {
    mockedFetch.mockResolvedValue(ROWS)
    const wrapper = mountSidebar()
    await flushPromises()

    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(links.map((l) => l.props('to'))).toEqual(['/category/3', '/category/5'])
  })

  it('空数组显示空态而不是错误态', async () => {
    mockedFetch.mockResolvedValue([])
    const wrapper = mountSidebar()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无分类')
    expect(wrapper.text()).not.toContain('加载失败')
  })

  it('取数失败显示错误态与后端 message，重试可恢复', async () => {
    mockedFetch.mockRejectedValueOnce(new Error('服务暂不可用'))
    const wrapper = mountSidebar()
    await flushPromises()

    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).toContain('服务暂不可用')

    // 点重试：这次成功，错误态要被真数据替换掉
    mockedFetch.mockResolvedValueOnce(ROWS)
    await wrapper.get('.sidebar-retry').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('加载失败')
    expect(wrapper.text()).toContain('技术')
  })
})
