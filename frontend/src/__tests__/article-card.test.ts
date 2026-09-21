import { describe, it, expect } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import ArticleCard from '../components/ArticleCard.vue'

const BASE = {
  articleId: 7,
  title: '示例文章',
  summary: '摘要文本',
  coverImage: '/images/cover-example.jpg',
  createdAt: '2026-09-21T14:30:00',
  viewCount: 128,
}

function mountCard(overrides: Partial<typeof BASE> = {}) {
  return mount(ArticleCard, {
    props: { ...BASE, ...overrides },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('ArticleCard 文章卡片', () => {
  it('整卡链到 /article/{articleId}', () => {
    const wrapper = mountCard()
    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/article/7')
  })

  it('渲染标题、摘要、浏览量', () => {
    const text = mountCard().text()
    expect(text).toContain('示例文章')
    expect(text).toContain('摘要文本')
    expect(text).toContain('128')
  })

  it('时间只显示日期部分，不显示时分秒', () => {
    // 后端返回 ISO-8601（规格 §2），卡片只取前 10 位
    const text = mountCard().text()
    expect(text).toContain('2026-09-21')
    expect(text).not.toContain('14:30')
  })

  it('coverImage 为空时走占位，不渲染 img', () => {
    const wrapper = mountCard({ coverImage: '' })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('.img-placeholder').exists()).toBe(true)
  })

  it('coverImage 非空时渲染 img，且不出现占位元素', () => {
    const wrapper = mountCard()
    expect(wrapper.find('img').attributes('src')).toBe('/images/cover-example.jpg')
    expect(wrapper.find('.img-placeholder').exists()).toBe(false)
  })
})
