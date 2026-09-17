import axios from 'axios'

/** 后端统一响应结构：{ code, message, data }（code=0 成功） */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/**
 * 解包统一响应：code=0 返回 data，否则抛错（错误信息为后端 message）。
 * 用于所有业务 API 调用。
 */
export function unwrapResult<T>(res: ApiResponse<T>): T {
  if (res.code !== 0) {
    throw new Error(res.message || '请求失败')
  }
  return res.data
}

export const API_BASE = import.meta.env.VITE_API_BASE || '/api'

const http = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
})

// 响应拦截器：40100（token 失效）时清理本地登录态
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse | undefined
    if (body && body.code === 40100) {
      localStorage.removeItem('blog-admin-token')
      if (import.meta.env.DEV) console.warn('[http] token 失效（40100），已清理登录态')
    }
    return response
  },
  (error) => Promise.reject(error),
)

export default http