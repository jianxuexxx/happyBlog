import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const css = readFileSync(
  fileURLToPath(new URL('../styles/tokens.css', import.meta.url)),
  'utf8',
)

describe('tokens.css 契约', () => {
  it('含蓝×粉品牌色', () => {
    expect(css).toContain('--brand-primary: #5b9cf5')
    expect(css).toContain('--brand-secondary: #f48fb1')
  })
  it('含暗色体系根选择器', () => {
    expect(css).toContain(':root.dark')
  })
  it('含 iPhone 准则 token（圆角/间距/spring 曲线）', () => {
    expect(css).toContain('--radius-card')
    expect(css).toContain('--space-unit: 8px')
    expect(css).toContain('--spring-curve')
  })
})