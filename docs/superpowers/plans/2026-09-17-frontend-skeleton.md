# 前端骨架 + 主题系统 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `frontend/` 下搭建可运行的 Vue 3 单应用：蓝粉淡色 + iPhone 设计准则的主题系统、前台与 `/admin` 路由骨架、基础布局（毛玻璃导航）、Axios 统一 API 层、Pinia 状态骨架，全部采用驼峰命名。

**架构：** Vite + Vue3 + TypeScript + Element Plus 单应用。CSS 变量驱动主题（`:root` 亮色 / `:root.dark` 暗色双体系），路由 lazyload 分离前台与 admin，Axios 拦截器统一处理 `{code, message, data}` 响应与 401。图片一律名称占位（见设计规格 §14）。

**技术栈：** Vue 3.5.42, Vite 8.3.0, TypeScript, Element Plus 2.14.5, Vue Router 4, Pinia, Axios, Vitest + Vue Test Utils。

**外部依赖约定：**
- npm registry 默认可达；如网络受限在后端阶段再处理镜像
- 所有新文件路径以 `D:\projects\myblog\frontend\` 为前缀

---

## 文件结构

`frontend/`（全部新建或修改，相对于 frontend/）：
- `package.json` / `vite.config.ts` / `tsconfig.json` — 构建配置
- `index.html` — 入口 HTML
- `src/main.ts` — 应用入口
- `src/App.vue` — 根组件（挂 RouterView + 全局布局）
- `src/styles/tokens.css` — 主题 token（蓝粉淡色 + 暗色变量，**核心交付物**）
- `src/styles/global.css` — 全局基础样式（字体/间距/阴影/滚动条/占位图）
- `src/router/index.ts` — 路由表（前台 + admin 懒加载 + 401/404）
- `src/store/index.ts` — Pinia 入口
- `src/store/theme.ts` — 主题（亮/暗切换，localStorage 持久化）
- `src/store/site.ts` — 站点配置占位 store（后续接后端）
- `src/store/auth.ts` — 管理员 token/登录态占位 store
- `src/api/http.ts` — Axios 实例 + 拦截器（`{code,message,data}` 解包 + 401 处理）
- `src/utils/storage.ts` — localStorage 安全读写（JSON 序列化，供 theme/auth 使用）
- `src/layouts/AppLayout.vue` — 前台主布局（Navbar + RouterView + Footer）
- `src/layouts/AdminLayout.vue` — 管理端布局（侧边栏 + 顶栏 + 内容区）
- `src/components/layout/AppNavbar.vue` — 毛玻璃导航（Logo 占位 / 菜单 / 暗黑切换 / 搜索入口）
- `src/components/layout/AppFooter.vue` — 页脚（版权 + 备案占位）
- `src/views/Home.vue` — 首页占位（Hero + 文章卡片骨架 + 图片占位）
- `src/views/Archive.vue` / `src/views/Search.vue` / `src/views/Friends.vue` / `src/views/Category.vue` / `src/views/Tag.vue` / `src/views/About.vue` — 前台占位页
- `src/views/ArticleDetail.vue` — 文章详情占位页
- `src/views/admin/Login.vue` / `src/views/admin/Dashboard.vue` — 管理端占位页
- `src/views/NotFound.vue` — 404 占位页
- `public/images/` — 图片占位目录（见设计规格 §14，仅放占位说明文件）
- `src/__tests__/` — Vitest 测试（storage / theme / api 解包逻辑）

**每个文件的职责边界：**
- `tokens.css` 只放 CSS 变量（色值/间距/圆角/阴影/字体）；`global.css` 只放落地的元素样式（body 背景、占位图、通用工具类）
- `http.ts` 只负责请求/响应/错误拦截，不感知业务；业务 API 调用放各 `src/api/*.ts`（骨架阶段暂不建，交给页面任务）
- store 之间不互相 import，通过组件联动

---

### 任务 1：Vite + Vue3 + TS 脚手架初始化

**文件：**
- 创建：`frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`, `frontend/tsconfig.node.json`, `frontend/index.html`, `frontend/src/main.ts`, `frontend/src/App.vue`
- 修改：`frontend/public` 静态目录

- [ ] **步骤 1：生成脚手架**

在 `D:\projects\myblog\frontend` 下执行：

```bash
npm create vite@latest . -- --template vue-ts
```

预期：生成 `package.json`、`vite.config.ts`、`index.html`、`src/main.ts`、`src/App.vue`、`public/`、`tsconfig*.json`。

（若脚手架交互询问，选默认 vue-ts 模板。目录非空时的提示选择 overwrite —— 当前 frontend 为空，探头知会无冲突。）

- [ ] **步骤 2：安装依赖**

```bash
npm install
npm install element-plus pinia vue-router axios
npm install -D @types/node
```

预期：`npm install` 成功无 EPERM 报错；`npm run dev` 可启动（Ctrl+C 停止）。

- [ ] **步骤 3：锁定脚本，验证 dev 与 build**

修改 `package.json` 的 `scripts`，确保含：
```json
{
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  }
}
```

运行：
```bash
npm run build
```

预期：在 `frontend/node_modules/.bin` 下能解析 `vue-tsc`（无则 `npm install -D vue-tsc` 补装），构建输出 `frontend/dist/` 成功，退出码 0。

- [ ] **步骤 4：快速启动冒烟**

```bash
npm run dev -- --port 17532 &
sleep 8 && curl -s http://localhost:17532 | grep -o "<title>[^<]*</title>"
```

预期：返回页面含 `<title>Vite + Vue + TS</title>` 或自定义标题。然后 `kill` 掉后台进程。

- [ ] **步骤 5：Commit**

```bash
cd D:/projects/myblog
git add frontend/
git commit -m "chore: vite vue-ts 脚手架初始化"

Co-Authored-By: Claude Code <noreply@anthropic.com>
```

---

### 任务 2：主题 token 系统（核心）

**文件：**
- 创建：`frontend/src/styles/tokens.css`
- 创建：`frontend/src/styles/global.css`
- 修改：`frontend/src/main.ts`（引入样式）

- [ ] **步骤 1：编写 tokens.css（含先失败的"暗色主体类名契约"测试）**

创建 `frontend/src/styles/tokens.css`，**必须包含以下变量的完整定义**（蓝粉淡色系 + iPhone 准则，约束见记忆 design-constraints）：

```css
:root {
  /* 主色系：蓝×粉 */
  --brand-primary: #5b9cf5;
  --brand-primary-soft: #8bb8f8;
  --brand-secondary: #f48fb1;
  --brand-secondary-soft: #ffd6e8;
  --brand-accent: #b2d8f0;
  --brand-accent-soft: #fef2f7;

  /* 背景：淡色渐变（雾蓝→白→淡粉） */
  --bg-gradient: linear-gradient(180deg, #f4f6ff, #ffffff 50%, #fef2f7);
  --bg-color: #f4f6ff;

  /* 文字 */
  --text-primary: #1f2a3a;
  --text-secondary: #616161;
  --text-muted: #9aa5b1;

  /* iPhone 准则 */
  --radius-card: 18px;        /* 卡片 16–20px */
  --radius-btn: 12px;         /* 按钮/输入 10–14px */
  --radius-dialog: 24px;      /* 弹窗 24px+ */
  --space-unit: 8px;          /* 8pt 栅格 */
  --shadow-soft: 0 2px 12px rgba(31, 42, 58, 0.08);
  --shadow-hover: 0 8px 24px rgba(31, 42, 58, 0.12);
  --spring-curve: cubic-bezier(0.32, 0.72, 0, 1);
  --duration-base: 250ms;

  /* 字体 */
  --font-sans: 'PingFang SC', 'SF Pro Display', -apple-system, 'Segoe UI', 'Microsoft YaHei', sans-serif;
  --font-num: 'SF Pro Text', 'SF Mono', 'DIN Alternate', 'Segoe UI', sans-serif;

  /* 毛玻璃 */
  --glass-bg: rgba(255, 255, 255, 0.7);
  --glass-blur: saturate(180%) blur(16px);
  --glass-border: rgba(255, 255, 255, 0.35);
}

:root.dark {
  --bg-gradient: linear-gradient(180deg, #221831, #1b1b2e 50%, #15151c);
  --bg-color: #1b1b2e;
  --text-primary: #e4e4e4;
  --text-secondary: #d4d4d4;
  --text-muted: #9aa5b1;
  --glass-bg: rgba(27, 27, 46, 0.7);
  --glass-border: rgba(255, 255, 255, 0.12);
  --shadow-soft: 0 2px 12px rgba(0, 0, 0, 0.4);
  --shadow-hover: 0 8px 24px rgba(0, 0, 0, 0.5);
}
```

同时在 `frontend/src/__tests__/theme-tokens.test.ts` 写断言（先跑失败→后通过）：

```ts
import { describe, it, expect } from 'vitest'
// 读取 tokens.css 源码文本断言关键 token 存在。
// 用 Node fs 读文件（vitest 默认 node 环境可读写）：
import { readFileSync } from 'node:fs'
const css = readFileSync(new URL('../styles/tokens.css', import.meta.url), 'utf8')

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
```

- [ ] **步骤 2：运行测试确认失败（此时文件不存在）**

```bash
cd D:/projects/myblog/frontend && npx vitest run src/__tests__/theme-tokens.test.ts
```

预期：FAIL，报错 `ENOENT ... tokens.css`。

- [ ] **步骤 3：创建 tokens.css 与 global.css**

tokens.css 内容如步骤 1。创建 `frontend/src/styles/global.css`：

```css
* { box-sizing: border-box; }
html, body, #app { height: 100%; }
body {
  margin: 0;
  font-family: var(--font-sans);
  color: var(--text-primary);
  background: var(--bg-gradient);
  background-attachment: fixed;
  transition: background var(--duration-base) var(--spring-curve), color var(--duration-base) var(--spring-curve);
}

/* 图片占位（设计规格 §14） */
.img-placeholder {
  display:flex; align-items:center; justify-content:center;
  background: linear-gradient(135deg, var(--brand-accent-soft), var(--brand-secondary-soft));
  color: var(--text-muted); font-size: 14px; border-radius: var(--radius-card);
}
```

- [ ] **步骤 4：main.ts 引入样式**

修改 `frontend/src/main.ts`，在 `createApp(App)` 之前：

```ts
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/global.css'
```

- [ ] **步骤 5：运行测试确认通过**

```bash
npx vitest run src/__tests__/theme-tokens.test.ts
```

预期：PASS（3 个用例）。

- [ ] **步骤 6：Commit**

```bash
cd D:/projects/myblog && git add frontend/ && git commit -m "feat: 蓝粉淡色主题 token 与全局样式

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### 任务 3：存储工具 + 主题 store（暗黑切换）

**文件：**
- 创建：`frontend/src/utils/storage.ts`
- 创建：`frontend/src/store/theme.ts`
- 创建：`frontend/src/__tests__/storage.test.ts`

- [ ] **步骤 1：编写失败测试**

`frontend/src/__tests__/storage.test.ts`：

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { getStorage, setStorage, removeStorage } from '../utils/storage'

describe('storage 安全读写', () => {
  beforeEach(() => { localStorage.clear() })
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
    setStorage('k', 1); removeStorage('k')
    expect(getStorage('k')).toBeNull()
  })
})
```

- [ ] **步骤 2：运行测试确认失败**

`cd D:/projects/myblog/frontend && npx vitest run src/__tests__/storage.test.ts`
预期：FAIL，模块不存在。

- [ ] **步骤 3：实现 storage.ts**

```ts
export function setStorage<T>(key: string, value: T): void {
  try { localStorage.setItem(key, JSON.stringify(value)) } catch { /* 隐私模式忽略 */ }
}
export function getStorage<T>(key: string, fallback: T | null = null): T | null {
  try {
    const raw = localStorage.getItem(key)
    return raw === null ? fallback : JSON.parse(raw) as T
  } catch { return fallback }
}
export function removeStorage(key: string): void {
  localStorage.removeItem(key)
}
```

- [ ] **步骤 4：运行测试确认通过**

预期：PASS（4 用例）。

- [ ] **步骤 5：实现 theme store**

`frontend/src/store/theme.ts`：

```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getStorage, setStorage } from '../utils/storage'

const THEME_KEY = 'blog-theme'

export const useThemeStore = defineStore('theme', () => {
  const isDark = ref<boolean>(getStorage<boolean>(THEME_KEY, false) ?? false)

  function apply() {
    document.documentElement.classList.toggle('dark', isDark.value)
  }
  function toggle() {
    isDark.value = !isDark.value
    setStorage(THEME_KEY, isDark.value)
    apply()
  }
  function init() { apply() }

  return { isDark, toggle, init }
})
```

（骨架阶段 theme 测试可并入 tokens 测试，不单独开组件测试；`apply()` 的 DOM 断言放到任务 7 的表单/导航测试中。）

- [ ] **步骤 6：Commit**

```bash
cd D:/projects/myblog && git add frontend/ && git commit -m "feat: storage 工具 + 主题 store

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### 任务 4：Axios 统一 API 层

**文件：**
- 创建：`frontend/src/api/http.ts`
- 创建：`frontend/src/__tests__/http.test.ts`

- [ ] **步骤 1：编写失败测试**

`frontend/src/__tests__/http.test.ts`（用 axios 的 mock adapter 思路——骨架阶段用最小依赖：直接测解包函数而非完整请求）：

```ts
import { describe, it, expect } from 'vitest'
import { unwrapResult } from '../api/http'

describe('统一响应解包 http.unwrapResult', () => {
  it('code=0 返回 data', () => {
    expect(unwrapResult({ code: 0, message: 'ok', data: { id: 1 } })).toEqual({ id: 1 })
  })
  it('code!=0 抛错', () => {
    expect(() => unwrapResult({ code: 40101, message: 'bad', data: null }))
      .toThrow(/bad/)
  })
})
```

- [ ] **步骤 2：运行测试确认失败**

预期：FAIL，`Cannot find module '../api/http'`。

- [ ] **步骤 3：实现 http.ts**

```ts
import axios from 'axios'

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export function unwrapResult<T>(res: ApiResponse<T>): T {
  if (res.code !== 0) throw new Error(res.message || '请求失败')
  return res.data
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000,
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse
    if (body?.code === 40100) {
      // token 失效：清理本地登录态并跳 /admin/login（骨架阶段仅清理）
      localStorage.removeItem('blog-admin-token')
      if (import.meta.env.DEV) console.warn('[http] 40100 token 失效')
    }
    return response
  },
  (error) => Promise.reject(error),
)

export default http
```

- [ ] **步骤 4：运行测试确认通过**

预期：PASS（2 用例）。

- [ ] **步骤 5：Commit**

```bash
cd D:/projects/myblog && git add frontend/ && git commit -m "feat: axios 统一响应解包与拦截

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### 任务 5：路由骨架（前台 + admin 懒加载 + 404 + 守卫）

**文件：**
- 创建：`frontend/src/router/index.ts`
- 创建：`frontend/src/views/Home.vue` 等占位页（本任务先建会不会被任何路由引用的证明——页组件内容极简）
- 修改：`frontend/src/main.ts`（注册 router）

- [ ] **步骤 1：实现 router/index.ts**

```ts
import { createRouter, createWebHistory } from 'vue-router'
import { getStorage } from '../utils/storage'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/Home.vue') },
    { path: '/article/:articleId', name: 'article', component: () => import('../views/ArticleDetail.vue') },
    { path: '/category/:id', name: 'category', component: () => import('../views/Category.vue') },
    { path: '/tag/:id', name: 'tag', component: () => import('../views/Tag.vue') },
    { path: '/archive', name: 'archive', component: () => import('../views/Archive.vue') },
    { path: '/search', name: 'search', component: () => import('../views/Search.vue') },
    { path: '/friends', name: 'friends', component: () => import('../views/Friends.vue') },
    { path: '/about', name: 'about', component: () => import('../views/About.vue') },
    {
      path: '/admin',
      component: () => import('../layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'login', component: () => import('../views/admin/Login.vue'), meta: { guest: true } },
        { path: 'dashboard', component: () => import('../views/admin/Dashboard.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFound.vue') },
  ],
})

router.beforeEach((to) => {
  const hasToken = !!getStorage<string>('blog-admin-token')
  if (to.meta.requiresAuth && !hasToken) {
    return { name: 'admin-login' }
  }
  if (to.meta.guest && hasToken) {
    return { name: 'admin-dashboard' }
  }
  return true
})

export default router
```

- [ ] **步骤 2：暂停——路由指向的视图/布局需全量存在**

复用本任务步骤 3 全量创建占位视图（每条路由对应文件）。为保持步幅可独立测试，这里先全部创建，测试放步骤 5 的编译冒烟。

- [ ] **步骤 3：创建全部占位视图 + 布局**

每个占位视图如 `frontend/src/views/Home.vue`：

```vue
<template>
  <div class="page-placeholder">
    <h1>首页（占位）</h1>
    <p>蓝粉淡色主题骨架预览：正式内容随后端接入替换。</p>
  </div>
</template>
```

新建（内容均为此结构，标题文案对应页面名）：
`Home.vue`、`ArticleDetail.vue`、`Category.vue`、`Tag.vue`、`Archive.vue`、`Search.vue`、`Friends.vue`、`About.vue`、`NotFound.vue`、`admin/Login.vue`、`admin/Dashboard.vue`、`layouts/AdminLayout.vue`、`layouts/AppLayout.vue`（AppLayout 本任务先建，供后续任务用）。

- [ ] **步骤 4：main.ts 注册 router 与 pinia**

```ts
import { createPinia } from 'pinia'
import router from './router'

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.mount('#app')
```

- [ ] **步骤 5：编译冒烟验证（TS 检查所有懒加载路径存在）**

```bash
npm run build
```

预期：vue-tsc 不报 `Cannot find module 'xxx.vue'`；dist 生成成功。

- [ ] **步骤 6：Commit**

```bash
cd D:/projects/myblog && git add frontend/ && git commit -m "feat: 路由骨架与占位视图

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### 任务 6：导航栏（毛玻璃）+ 页脚 + 前台布局

**文件：**
- 创建：`frontend/src/components/layout/AppNavbar.vue`
- 创建：`frontend/src/components/layout/AppFooter.vue`
- 创建：`frontend/src/layouts/AppLayout.vue`（上任务已建占位，此任务改为真实布局）

- [ ] **步骤 1：AppNavbar.vue**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useThemeStore } from '../../store/theme'

const theme = useThemeStore()
const menus = [
  { path: '/', label: '首页' },
  { path: '/archive', label: '归档' },
  { path: '/friends', label: '友链' },
  { path: '/about', label: '关于' },
]
const drawerOpen = ref(false)
</script>

<template>
  <header class="navbar">
    <router-link to="/" class="navbar-logo">
      <!-- 图片占位：/images/site-logo.png（用户自行提供，见清单） -->
      <img src="/images/site-logo.png" alt="logo" class="navbar-logo-img img-placeholder" />
      <span class="navbar-logo-text">我的博客</span>
    </router-link>

    <nav class="navbar-menu">
      <router-link v-for="m in menus" :key="m.path" :to="m.path" class="navbar-link">{{ m.label }}</router-link>
      <router-link to="/search" class="navbar-link">搜索</router-link>
    </nav>

    <div class="navbar-actions">
      <el-button circle :icon="theme.isDark ? 'Sunny' : 'Moon'" @click="theme.toggle()"
        :title="theme.isDark ? '切换到亮色' : '切换到暗色'" />
      <el-button text type="primary" @click="drawerOpen = true">管理</el-button>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  position: sticky; top: 0; z-index: 100;
  display: flex; align-items: center; gap: 24px;
  padding: 0 24px; height: 64px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-bottom: 1px solid var(--glass-border);
}
.navbar-logo { display: flex; align-items: center; gap: 10px; text-decoration: none; color: var(--text-primary); }
.navbar-logo-img { width: 36px; height: 36px; border-radius: 10px; }
.navbar-logo-text { font-weight: 600; font-size: 18px; }
.navbar-menu { display: flex; gap: 8px; margin-left: auto; }
.navbar-link {
  padding: 8px 14px; border-radius: var(--radius-btn); color: var(--text-secondary);
  text-decoration: none; font-size: 14px; transition: background var(--duration-base) var(--spring-curve), color var(--duration-base) var(--spring-curve);
}
.navbar-link:hover { background: var(--brand-accent-soft); color: var(--brand-primary); }
.router-link-active { background: var(--brand-primary); color: #fff; }
.navbar-actions { display: flex; align-items: center; }
</style>
```

- [ ] **步骤 2：AppFooter.vue**

```vue
<template>
  <footer class="app-footer">
    <p>© 2026 我的博客 · 由 Vue3 + Spring Boot + RustFS 驱动</p>
    <p class="app-footer-muted">备案号占位：待配置</p>
  </footer>
</template>
<style scoped>
.app-footer { text-align: center; padding: 32px 16px; color: var(--text-muted); font-size: 13px; }
.app-footer-muted { margin-top: 4px; }
</style>
```

- [ ] **步骤 3：AppLayout.vue 真实化**

```vue
<script setup lang="ts">
import AppNavbar from '../components/layout/AppNavbar.vue'
import AppFooter from '../components/layout/AppFooter.vue'
</script>

<template>
  <div class="app-layout">
    <AppNavbar />
    <main class="app-main"><router-view /></main>
    <AppFooter />
  </div>
</template>

<style scoped>
.app-layout { min-height: 100vh; display: flex; flex-direction: column; }
.app-main { flex: 1; width: 100%; max-width: 1080px; margin: 0 auto; padding: 24px 16px; }
</style>
```

- [ ] **步骤 4：让首页/前台路由套用 AppLayout**

改 `frontend/src/router/index.ts`：顶部:

```ts
{ path: '/', component: () => import('../layouts/AppLayout.vue'), children: [ /* 前台全部路由移入 children */ ] }
```

（将 Home/Article/Archive/... 全部移入其 children，`/admin` 保持不变。守卫逻辑不变。）

- [ ] **步骤 5：构建 + 主题冒烟**

```bash
npm run build
cd D:/projects/myblog/frontend && npm run dev -- --port 17532 &
sleep 8 && curl -s http://localhost:17532 | grep -c "我的博客"
```

预期：build 成功；dev 页面包含"我的博客"文字。杀掉后台 dev 进程。

- [ ] **步骤 6：Commit**

```bash
cd D:/projects/myblog && git add frontend/ && git commit -m "feat: 毛玻璃导航 + 页脚 + 前台布局

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### 任务 7：首页 Hero（图片占位 + 卡片骨架 + 微动效）

**文件：**
- 修改：`frontend/src/views/Home.vue`

- [ ] **步骤 1：创建图片占位说明文件**

在 `frontend/public/images/` 下创建 `README.md`，内容即设计规格 §14 的图片清单（site-logo / site-avatar / hero-bg / favicon）并注明"用户自行提供，未提供时前端显示占位"。

- [ ] **步骤 2：实现 Home.vue**（Hero + 文章骨架卡片，点明图片占位用法）

```vue
<script setup lang="ts">
// 骨架数据占位：真实数据来自 GET /api/home/recommend 与 /api/article/list（后端接入后替换）
const recommendSkeletons = [1, 2, 3]
const articleSkeletons = [1, 2, 3, 4, 5, 6]
</script>

<template>
  <div class="home">
    <section class="hero img-placeholder">
      <div class="hero-inner">
        <h1 class="hero-title">我的博客</h1>
        <p class="hero-subtitle">记录生活的倒影与诗意的代码</p>
      </div>
    </section>

    <section class="recommend">
      <h2 class="section-title">推荐</h2>
      <div class="recommend-grid">
        <div v-for="n in recommendSkeletons" :key="n" class="rec-card img-placeholder" style="height: 160px">
          推荐位 {{ n }}（图片占位）
        </div>
      </div>
    </section>

    <section class="article-list">
      <h2 class="section-title">最新</h2>
      <article v-for="n in articleSkeletons" :key="n" class="article-card">
        <div class="article-cover img-placeholder">封面占位</div>
        <div class="article-body">
          <h3>文章标题占位 {{ n }}</h3>
          <p class="article-summary">摘要占位：接入后端后展示真实摘要。</p>
          <div class="article-meta">
            <span class="tag-chip">标签</span>
            <span>浏览 0 · 2026-09-17</span>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.hero { min-height: 280px; border-radius: var(--radius-card)*2 /* 强调大圆角 */; position: relative; margin-bottom: 32px;
  /* hero-bg 用户图替换占位背景 */
  background-image: linear-gradient(135deg, var(--brand-primary-soft), var(--brand-secondary-soft)); }
.hero-inner { position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; color: #fff; text-shadow: 0 2px 8px rgba(0,0,0,.15); }
.hero-title { font-size: 38px; font-weight: 700; margin: 0; }
.hero-subtitle { font-size: 16px; opacity: .92; }
.section-title { font-size: 22px; font-weight: 600; margin: 24px 0 16px; }
.recommend-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 32px; }
.article-list { display: grid; gap: 16px; }
.article-card {
  display: flex; gap: 16px; padding: 16px; background: var(--glass-bg);
  border-radius: var(--radius-card); box-shadow: var(--shadow-soft);
  transition: transform var(--duration-base) var(--spring-curve), box-shadow var(--duration-base) var(--spring-curve);
}
.article-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); }
.article-cover { width: 200px; min-height: 120px; flex-shrink: 0; }
.article-body { flex: 1; }
.article-summary { color: var(--text-secondary); }
.article-meta { display: flex; gap: 12px; color: var(--text-muted); font-size: 13px; align-items: center; }
.tag-chip { background: var(--brand-accent-soft); color: var(--brand-primary); border-radius: 999px; padding: 2px 10px; font-size: 12px; }
</style>
```

注意：`var(--radius-card)*2` 是非法的 CSS——用 `calc(var(--radius-card) * 2)`。

- [ ] **步骤 3：构建验证**

```bash
npm run build
```

预期：编译通过（无未定义变量/语法错）。

- [ ] **步骤 4：Commit**

```bash
cd D:/projects/myblog && git add frontend/ && git commit -m "feat: 首页 Hero 与卡片骨架（图片占位）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### 任务 8：picture 占位清单输出（无代码）

- [ ] **步骤 1：汇总图片素材清单并交付给用户**

在任务完成后，向用户输出以下的图片占位清单（与规格 §14 对齐），请用户按格式准备图片：`site-logo`、`site-avatar`、`hero-bg`、`favicon`、（文章封面 `cover-*`、友链 `friend-*` 在对应功能落地时再出）。格式：PNG（logo/avatar/favicon）、JPG 或 WebP（hero/封面色），尺寸按规格。

- [ ] **步骤 2：更新 README 的项目图片说明**

在 `frontend/public/images/README.md` 中已含清单，确认与规格 §14 一致后 Commit。

```bash
cd D:/projects/myblog && git add frontend/ && git commit -m "docs: 图片占位清单

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### 任务 9：前端骨架收尾验证

**文件：** 无新文件

- [ ] **步骤 1：全量测试 + 生产构建**

```bash
cd D:/projects/myblog/frontend
npm test
npm run build
```

预期：Vitest 全部通过；vue-tsc 无错；dist 产出。

- [ ] **步骤 2：Compose 外的本地联调冒烟（无后端时）**

```bash
npm run dev -- --port 17532 &
curl -s http://localhost:17532 | grep -c "我的博客"
```

预期：首页文案可见（前端骨架完成标准：**路由全部可达、主题可切、页面占位渲染**）。

- [ ] **步骤 3：最终 Commit（如仍有零散改动）**

```bash
cd D:/projects/myblog && git add -A && git commit -m "chore: 前端骨架收尾

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 自检

**1. 规格覆盖度：**
- 前端路由骨架 ✅（任务 5，含前台/admin/404）
- 蓝粉淡色 + iPhone 准则主题约束 ✅（任务 2 tokens，用例断言）
- 毛玻璃导航/卡片/spring 动效 ✅（任务 2 global、任务 6、任务 7）
- 图片名称占位策略 ✅（任务 7 创建 public/images/README + 任务 8 清单）
- 统一响应与 401 拦截 ✅（任务 4 http.ts）
- 暗黑模式 ✅（任务 3 theme store + `/admin` 与布局联动）
- 品牌/导航/页脚 ✅（任务 6）

**2. 占位符扫描：** 无 TODO；所有 css token、组件路径、测试文件在任务内定义；图片一律占位引用非虚构资源。

**3. 类型一致性：** `useThemeStore` 导出自 `store/theme.ts` 并仅被 Navbar 使用；`unwrapResult` 仅本文件定义；`src/api/*.ts` 业务 API 文件不在骨架范围内（留给页面任务）；占位视图均按本计划创建的文件名。

**4. 环境边界：** 前端仅依赖 Node 22（✅ 已装）；不涉及 JDK/Docker；端口 17532 为前端开发专用（避开 18088）。