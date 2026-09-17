import { describe, it, expect, beforeEach, vi } from 'vitest'
import { getStorage, setStorage, removeStorage } from '../utils/storage'

describe('storage 安全读写', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('默认 JSON 序列化往返', () => {
    setStorage('k', { a: 1 })
    expect(getStorage('k')).toEqual({ a: 1 })
  })

  it('key 缺失返回默认值', () => {
    expect(getStorage('missing', 'fallback')).toBe('fallback')
  })

  it('坏 JSON 不抛错，返回默认值', () => {
    localStorage.setItem('bad', '{oops')
    expect(getStorage('bad', [])).toEqual([])
  })

  it('可删除', () => {
    setStorage('k', 1)
    removeStorage('k')
    expect(getStorage('k')).toBeNull()
  })
})