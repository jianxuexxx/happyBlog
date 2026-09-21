# HappyBlog 前端

Vue 3 + TypeScript + Vite，含前台与管理端（`/admin`）。视觉遵循设计规格 §6「蓝粉淡色系 + iPhone 设计准则」。

## 开发命令

```bash
npm install
npm run dev        # 开发服务器，端口 17532
npm run test       # vitest（jsdom），单跑一次
npm run test:watch # vitest 监听模式
npm run build      # vue-tsc -b 类型检查 + vite build
```

`vite.config.ts` 里把 `/api` 反向代理到 `http://localhost:18088`（后端端口），因此开发期**不需要后端配 CORS**——后端目前也确实没有任何 CORS 配置，纯 HTTP 内网部署。

## 目录结构

```
src/
├── api/            # 接口层。http.ts 是 axios 实例与统一响应解包，其余按后端模块分文件
├── components/
│   ├── home/       # 首页区块（轮播、公告、侧栏分类、侧栏推荐）
│   └── layout/     # 导航栏、页脚
├── layouts/        # AppLayout（前台）/ AdminLayout（管理端）
├── router/         # 路由表
├── store/          # Pinia store（目前只有 theme）
├── styles/         # tokens.css（设计令牌）+ global.css
├── utils/          # storage 等工具
├── views/          # 页面；views/admin/ 为管理端
└── __tests__/      # 单测
```

## 与后端的两个契约

**1. 所有响应都是 HTTP 200，错误只体现在 body 的 `code` 字段。**

所以不要用 axios 的 `catch` 判业务错误——那是网络层错误。业务错误走 `api/http.ts` 的 `unwrapResult()`：`code !== 0` 时抛 `Error(message)`，调用方 `try/catch` 拿到后端的中文提示。

`code` 取值：`0` 成功 / `40001` 参数校验失败 / `40002` 分类名已存在 / `40100` token 失效 / `40101` 登录失败 / `40400` 不存在 / `50000` 服务端错误 / `50300` 上传失败。

**2. `40100`（token 失效）在 axios 的*成功*回调里处理**（见 `api/http.ts` 的响应拦截器）：清掉 localStorage 里的 `blog-admin-token`，然后照常把响应交回调用方。这是契约 1 的直接后果。

## 已接入的后端接口

| 前端调用 | 后端接口 | 说明 |
|---|---|---|
| `fetchCategoryList()` | `GET /api/category/list` | 首页侧栏分类卡片、分类页的页头标题 |
| `fetchArticleList(query)` | `GET /api/article/list` | 分类页文章列表 |

`fetchArticleList` 的返回是 `PageResult<T>`：`{ total, page, pageSize, list }`，字段与后端
`com.blog.common.PageResult` 一一对应。**响应里的 `page` / `pageSize` 是后端钳制生效后的值**
（请求 `pageSize=999` 会得到 `50`），不是请求值的回显。

`createdAt` 是 ISO-8601（`2026-09-21T14:30:00`）。后端刻意不定制时间格式（理由见
`docs/superpowers/specs/2026-09-21-article-list-slice-design.md` §2），因为它同时是
`new Date()` 在各浏览器上都能正确解析的格式。只显示日期时用 `createdAt.slice(0, 10)`。

## 主题

设计令牌集中在 `styles/tokens.css`，暗色是 `:root.dark` 覆盖同名变量，由 `store/theme.ts` 切换 `document.documentElement` 上的 `dark` 类，偏好存 localStorage 的 `blog-theme`。

⚠️ **改令牌时要看它在 `:root.dark` 里有没有被覆盖。** `--brand-*` 系列**没有**暗色覆盖，而 `--text-primary` / `--text-secondary` **有**（会翻成浅色）。所以彩色小徽标这类「浅底 + 深字」的组合，底色和字色必须**都取 `--brand-*`**；一旦字色用了会翻转的文本令牌，暗色下会变成浅字压浅底，对比度掉到 1.1 左右，等于看不见。
