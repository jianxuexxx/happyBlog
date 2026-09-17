/**
 * localStorage 安全读写（JSON 序列化）
 * 隐私模式/损坏 JSON 均不抛错，返回默认值。
 */

export function setStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* 隐私模式 / 配额满：静默忽略 */
  }
}

export function getStorage<T>(key: string, fallback: T | null = null): T | null {
  try {
    const raw = localStorage.getItem(key)
    return raw === null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

export function removeStorage(key: string): void {
  localStorage.removeItem(key)
}