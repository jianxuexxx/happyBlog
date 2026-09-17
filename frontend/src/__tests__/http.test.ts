import { describe, it, expect } from 'vitest'
import { unwrapResult, type ApiResponse } from '../api/http'

describe('统一响应解包 unwrapResult', () => {
  it('code=0 返回 data', () => {
    const res: ApiResponse<{ id: number }> = { code: 0, message: 'ok', data: { id: 1 } }
    expect(unwrapResult(res)).toEqual({ id: 1 })
  })

  it('code!=0 抛错', () => {
    const res: ApiResponse<null> = { code: 40101, message: '用户名或密码错误', data: null }
    expect(() => unwrapResult(res)).toThrow(/用户名或密码错误/)
  })

  it('data 为原始类型也按原样返回', () => {
    const res: ApiResponse<number> = { code: 0, message: 'ok', data: 42 }
    expect(unwrapResult(res)).toBe(42)
  })
})