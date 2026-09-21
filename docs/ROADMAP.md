# HappyBlog 后续工作路线图

> **快照：** 2026-09-21，对应 `main@2b22a31`（运行环境实况经使用者逐条确认后修订）。
> **这是一份活文档**，不是冻结稿——随交付更新，别把它当需求来源。
> 需求真相在 [`docs/superpowers/specs/2026-09-17-myblog-design.md`](superpowers/specs/2026-09-17-myblog-design.md)（下称**主规格**）。
> 本文只回答三件事：**还差什么、先做什么、每件事多大**。
> 单个切片的延后项与**撤回项**不在这里，在 [`docs/superpowers/followups/`](superpowers/followups/)。

---

## 0. 三条轨道

剩下的事分三类，不要混在一起排：

| 轨道 | 是什么 | 在哪 |
|---|---|---|
| **功能切片** | 一次 subagent-driven 交付：规格 → 计划 → 实现 → 审查 | §2 §3 |
| **横切欠账** | 不专属任何切片，攒着一起做 | §4 |
| **验证债** | 「代码写了但从没真跑过」的欠账 | §5 |

---

## 1. 现状快照

### 已交付三个切片

| 切片 | 日期 | 交付内容 |
|---|---|---|
| 前端骨架 | 2026-09-17 | Vue3 + TS + Vite、主题令牌与暗色切换、路由、布局、首页 v2（轮播／展位／悬浮通知的**静态版**） |
| 后端骨架 | 2026-09-20 | Spring Boot 3.4.3 + MyBatis-Plus + Redis + JWT 鉴权 + 统一返回／全局异常分层 + OpenAPI 就绪 + category 一条完整 CRUD 竖切 |
| article 列表切片 | 2026-09-21 | `GET /api/article/list`（五个筛选 + 分页）、`/category/:id` 接真数据、`ArticleCard` 抽取 |

### 接口覆盖率

后端一共只有 4 个 Controller：`AdminAuthController`（登录／登出）、`AdminCategoryController`（增／改／删）、`ArticleController`（只有 `/list`）、`CategoryController`（只有 `/list`）。

- **前台公共端点：主规格 §8 列了 10 个，实现了 2 个（20%）**
- **管理端端点：主规格 §8 列了 25 个，实现了 5 个（20%）**

### 前端实况

11 条路由（8 前台 + 2 管理端 + 404），其中 **8 个视图是 5 行的占位页**：`article/:articleId`、`tag/:id`、`archive`、`search`、`friends`、`about`、`admin/login`、`admin/dashboard`。

前台只有 `/category/:id` 是**完全接真数据**的。首页是「真的外壳 + 假的内容」——`Home.vue:12-17` 的 `articleSkeletons` 是 6 条硬编码假数据。

### 运行环境实况

**这一节是 2026-09-21 向使用者逐条确认 + 磁盘痕迹核对的结果**（我上一版曾据「`docker/` 空 + 无 CI」错误推断「应用从未启动过」，那是把「我没见过」当成了「没发生过」，已改正）：

| 项 | 状态 | 出处 |
|---|---|---|
| MySQL 8 / Redis | 🟢 本机已装好、**正在运行**；库表已按 `schema.sql` 建好，**库里已有数据** | 使用者 2026-09-21 确认 |
| 后端启动 | 🟢 **成功过**，在 IDE 里直接跑 `MyBlogApplication` | 使用者确认；`.tmp/startup.log` 另留有一次 2026-09-20 14:44 的记录 |
| 前端 dev server | 🟢 跑过 `npm run dev`；也构建过生产包（`frontend/dist`，2026-09-17） | 使用者确认 + `frontend/dist` |
| 前端页面浏览器实测 | 🟡 只有**首页侧栏分类卡片**（2026-09-20，真库数据渲染） | README「## 状态」 |

> ⚠️ 关于 `.tmp/startup.log` 那次记录，别当成「完整可用的启动」：那是后端骨架**开发中途**的状态，
> 日志里有 `No MyBatis mapper was found in '[com.blog.mapper]'`（当时 `@MapperScan` 还没就位），
> 且**没有任何 Hikari 连池记录**（Hikari 懒初始化，启动成功不等于连上了库）。
> 真正证明「应用 + 库 + Redis 全链路可用」的是 `category-smoke.sh` 那次 22/22。

**结论：能跑的环境已经具备。** 「启动应用」不需要等 `docker compose` —— 这直接改变了下面 §6 的排序理由。

### 最要紧的一句话

> **系统里没有任何创建文章的路径。**
>
> 这是 article 列表切片被迫手工 INSERT 样例数据的根因（见 `backend/smoke/article-list-smoke.sh` 文末的样例数据 SQL），
> 也是「前台永远渲染空列表」的根因。**所有前台页面的接线都卡在这上面** —— 没有内容可展示。

---

## 2. 大方向：剩余 9 个切片

| # | 切片 | 主规格依据 | 规模 | 依赖 | 解锁 |
|---|---|---|---|---|---|
| **A** | Docker 基础设施 | §12 §15.1 | 中 | 无 | 真实 E2E、RustFS、后面所有人 |
| **B** | 管理端文章 CRUD + 状态机 | §8 §7.4 | **大** | 无 | **造内容**、前台一切、管理端 |
| **C** | 文章详情 + 浏览量 | §8 §9 | 中 | B（得有内容） | `/article/:articleId` |
| **D** | 标签／友链／通知／站点配置 四条 CRUD | §7.2 §7.3 §8 | 中大 | 无（照抄 category） | `/tag/:id`、`/friends`、通知、导航栏 |
| **E** | 首页接线 | §5 §8 §9 | 小中 | D（通知/配置）、B（内容） | 首页真数据 |
| **F** | 归档 + 搜索 | §8 §13 | 小中 | B | `/archive`、`/search` |
| **G** | Vditor + RustFS 图床 | §7.1 | **大**（新技术栈） | A（RustFS 得先起得来） | 写作体验 |
| **H** | 管理端前端页面 + 仪表盘 | §5 §8 | 中大 | B、D | 可用的管理端 |
| **I** | 暗色主题切片 | README「已知未修」 | 小 | 无 | 暗色下 EP 控件、刷新保持 |

---

## 3. 小方向：逐切片细项

> 下面的字母（A–I）是**标签，不是执行顺序** —— 顺序见 §6。

### A. Docker 基础设施

`docker/` 目录**完全是空的**——主规格 §12 要的 5 个服务一个都没有。仓库里也没有任何 `Dockerfile`、`compose*.yml`、`.env*`。

**要产出：**

- `docker/docker-compose.yml` —— `mysql8`／`redis7`／`rustfs`／`backend`／`frontend-nginx`
- `docker/mysql/` 初始化挂载（`schema.sql` 已在 `backend/src/main/resources/db/`，要决定是复制还是挂载引用）
- `docker/redis/redis.conf`、`docker/rustfs/`（RustFS 配置）
- `backend/Dockerfile` —— JDK21 + maven 多段构建
- `frontend/Dockerfile` + `docker/nginx/default.conf` —— node 构建 + nginx 托管静态 + 反代 `/api → backend:18088`
- `.env.example`

**硬约束（主规格 §12）：**

- 外部端口避开通用端口：MySQL `33066`、Redis `16379`
- 服务密钥／连接串走环境变量，**不硬编码**
- 各服务 healthcheck；数据卷持久化
- **纯 HTTP 内网访问，不做 HTTPS／外网暴露**
- `docker compose up -d` / `down` 两个命令搞定

> ⚠️ **一个必须先补的坑：** 当前 `.gitignore` **没有 `.env` 规则**（它只忽略了 `backend/config/` 和 `backend/target/`）。
> 照主规格要求把密钥搬进环境变量时，`.env` 会被 `git add` 直接吃进去。**做本切片时第一件事就是把 `.env` 加进 `.gitignore`。**

### B. 管理端文章 CRUD + 状态机 ← 杠杆最高

后端缺 `AdminArticleController`，端点全缺：

```
POST   /api/admin/article              创建
PUT    /api/admin/article              更新
GET    /api/admin/article/list         管理列表（含草稿/私密/已删筛选）
GET    /api/admin/article/{id}         管理详情
PUT    /api/admin/article/{id}/status  状态流转
DELETE /api/admin/article/{id}         逻辑删除
```

**服务层**要在 `ArticleService` 上加写路径：

- 状态三件套：`status`（0 草稿／1 公开／2 私密）、`isTop`、`isRecommended`
- **标签关联**：`articleTag` 中间表要随文章保存重建
- **缓存失效**：`blog:home:recommend`（改推荐即删）与 `blog:article:{id}`（更新/删除即删）。
  后者还没实现（见 C）；`blog:article:list:{hash}` 主规格 §9 已裁定**暂缓**，本切片不用管
- 逻辑删除：`@TableLogic` 已在实体上，删即是 UPDATE

**规格里明确写了、容易漏的语义：**

- 私密／草稿：**前台隐藏、不可被搜索到**；管理员可预览
- 置顶：列表优先
- 推荐：进首页 Swiper
- 已删：管理列表要能筛得出来

> **本切片顺带消解一整类历史问题。** 有了写入口，`backend/smoke/article-list-smoke.sh` 就能自己造数据、自己清，
> 不必再依赖「手工灌样例数据」和「库中除样例数据外无他物」这两个前提——
> `followups/2026-09-21-article-list-slice-followups.md` §五 记的那批**「断言依赖库内容」的假红风险随之消失**。

### C. 文章详情 + 浏览量

**端点：** `GET /api/article/{articleId}`

- 返回正文 + 元信息（分类名、标签列表）
- 不存在／已删／私密 → `code:40400`，前台跳 404（主规格 §10）
- 上一篇／下一篇
- 缓存 `blog:article:{id}`，TTL 10 分钟，更新删除即删

**浏览量（主规格 §8 已确认 A 方案）——本切片要新建一整条链路：**

- 详情接口返回 Redis `blog:view:{id}` 的 INCR 即时值
- `@Scheduled` 每 5 分钟把增量批量写回 MySQL `viewCount` 并清零重计
- ⚠️ **当前仓库里没有任何 `@Scheduled`，也没开 `@EnableScheduling`** —— 从零开始
- 测试这条定时任务时，**别用真实等待**：把落库逻辑抽成可手动触发的方法，单测直接调

**前端：** `ArticleDetail.vue`（现 5 行占位）——元信息 + Markdown 渲染 + 标签 + 上下篇。
Markdown 渲染库主规格没锁；如果 G 的 Vditor 已落地，可复用它自带的渲染器，省一个依赖。

### D. 标签 / 友链 / 通知 / 站点配置 四条 CRUD

**这四条代码形态几乎一样，而 category 那条已经跑通、并被 22 条冒烟断言钉过——拿 `CategoryServiceImpl` / `AdminCategoryController` 当模板，别重新发明。**

| 模块 | 管理端端点 | 前台端点 | 前端落地页 |
|---|---|---|---|
| **标签** | `POST /api/admin/tag`、`DELETE /api/admin/tag/{id}` | `GET /api/tag/list`（含文章数）、`GET /api/tag/cloud` | `/tag/:id`（现 5 行占位） |
| **友链** | `POST`／`PUT`／`DELETE /api/admin/friend` | `GET /api/friend/list` | `/friends`（现 5 行占位） |
| **通知** | `POST`／`PUT`／`DELETE /api/admin/notice`、`GET /api/admin/notice/list` | `GET /api/notice/today` | 首页 `NoticeFloat` |
| **站点配置** | `PUT /api/admin/site/config` | `GET /api/site/config` | 导航栏／页脚／关于页 |

**缓存：**

- `blog:siteConfig` —— 后台保存即删
- 当天通知短缓存 —— 主规格 §5 只说「短缓存、发布即失效」，**没指定键名**，实现时自定
- 键名与失效写法直接照抄 `CategoryServiceImpl:25` 的 `CACHE_KEY = "blog:category:list"` 模式

**已有的资产（不用重建）：** 7 张表的 **entity + mapper 全部就位**（`Tag`／`FriendLink`／`Notice`／`SiteConfig`／`ArticleTag` …），缺的只有 service + controller + dto。

**前端当前是桩的地方（都要替换）：**

- `Tag.vue`／`Friends.vue`／`About.vue` 各 5 行占位
- `NoticeFloat.vue:27` 的 `fetchTodayNotice()` **直接 `return null`** —— 桩已经画好了，接上即可
- `AppNavbar.vue`／`AppFooter.vue` 的站点信息是写死的

### E. 首页接线

`Home.vue:12-17` 的 `articleSkeletons` 是 6 条硬编码假数据，封面与摘要都是「占位」字样。要接四路：

| 位置 | 接口 | 后端状态 |
|---|---|---|
| 主区瀑布流 | `GET /api/article/list` | ✅ 已就绪，直接复用 `fetchArticleList` |
| 左栏推荐 `SidebarRecommended.vue` | `GET /api/home/recommend` | ❌ **要新建**；缓存 `blog:home:recommend` TTL 5min，后台改推荐即删 |
| 顶部轮播 `CarouselHero.vue` | 推荐文章（`isRecommended`） | 同上 |
| 右上悬浮通知 | `GET /api/notice/today` | ❌ 见 D |

做完可一次清掉 `Home.vue` 里 8 处「占位」注释。

### F. 归档 + 搜索

- `GET /api/article/archive` → 年／月时间线；前端 `/archive`（现 5 行占位）
- `/search`（现 5 行占位）：**主规格 §13 明确只做 LIKE，不做全文搜索**——别引入 ES／Lucene
- 搜索结果同样要排除私密与草稿（与 B 的状态语义一致）

### G. Vditor + RustFS 图床

**当前代码里零痕迹：** `pom.xml` 没有任何 S3／AWS SDK，`backend/src` 搜不到 rustfs／presign／s3，前端没有 Vditor。全部从零。

**要产出：**

- 后端加 S3 兼容 SDK 依赖 → presigned URL 签发
- `GET /api/admin/upload/presign`、`GET /api/admin/images`（ListObjectsV2 + 分页 + 缓存，供图库）、`DELETE /api/admin/images`
- 前端 Vditor：编辑／所见即所得／分屏三模式
- 拖拽／粘贴／工具栏三种触发 → 取 presign → **前端直传 RustFS**（不经过后端）→ URL 插正文
- 图库管理页：图片分页列表，供引用／删除
- 失败码：超大小／类型不符／RustFS 不可用 → `code:50300`（主规格 §10）

**依赖 A**：RustFS 服务得先能起来。

### H. 管理端前端页面 + 仪表盘

路由表里只有 `admin/login` 与 `admin/dashboard`（且都是 5 行占位），要补 6 条 —— `router/index.ts:63` 已经留了注释位：

```
/admin/articles    /admin/categories   /admin/tags
/admin/friends     /admin/notices      /admin/settings
```

- `Login.vue` —— 后端 `AdminAuthController` **已就绪**，接线即可（token 存 `blog-admin-token`，路由守卫已在 `router/index.ts:74-84`）
- `Dashboard.vue` —— 依赖 `GET /api/admin/dashboard/stats`，**后端还没有**
- `/admin/articles` —— 表格 + Vditor 编辑 + 图床，依赖 G

### I. 暗色主题切片

使用者 2026-09-21 裁定：两条并成**一个**切片，一次验证。

1. **暗色偏好刷新后失效** —— `frontend/src/store/theme.ts` 的 `init()` 没有调用点，localStorage 里的偏好刷新后打不到 `:root` 上
2. **EP 控件在暗色下仍是亮色** —— `frontend/src/main.ts` 只 import 了 `element-plus/dist/index.css`，缺 `element-plus/theme-chalk/dark/css-vars.css`

> **成因别记错：** 第 2 条**只有「少 import 一个文件」这一个原因**，不是选择器不匹配——
> `:root.dark` 与 EP 的 `html.dark` **是同一个选择器**（`theme.ts` 给 `document.documentElement` 加类就等于 `<html class="dark">`）。

修法各 1 行，但第 2 条会改动**全站所有 EP 组件**在暗色下的观感，所以单独做。
**适合插队：小、独立、不阻塞任何人。**

---

## 4. 横切欠账

1. **图片清单要补到「精确到每个使用点」** —— 主规格 §14 承诺「实现阶段会随组件落地给出最终清单」。
   现状 `frontend/public/images/README.md` 是**粗清单**（4 张基础图 + 2 类功能图），欠：`hero-bg`、`site-avatar`、`friend-{name}`、每篇的 `cover-{slug}` 的**具体路径与尺寸**。
   **约束（`image-placeholder-policy`）：一律名称占位，我只列清单、不生成图片，由你提供后同名替换。**

2. **`backend/` 没有 README** —— `frontend/README.md` 有（开发命令／目录结构／两个契约／已接接口／主题），后端缺一份对称的。

3. **主规格 §9 缓存表缺 `blog:category:list` 一行** —— 该键已实现（`CategoryServiceImpl:25`）、§15 第 4 项也提到了，但 §9 的表里没有它。表格与实现已经漂了，顺手补上。

4. **主规格 §9 注记要回头看** —— `blog:article:list:{hash}` 已裁定暂缓，理由是「等管理端 article CRUD 落地、有了真实写入压力后重新评估」。
   **B 切片做完就要回到这一条。**

5. **文档同步的三个固定写入点** —— README「## 状态」+ 主规格 §15 + `frontend/README.md`。
   （计划文档是冻结稿，**不回写**。）

6. **`docs/superpowers/followups/`** —— 已交付切片的延后项与撤回项。**下个切片开工前先读**，别重新发现、重新争论。

---

## 5. 验证债

**这一节比功能清单重要。**

| 项目 | 状态 |
|---|---|
| `mvn test` / `vitest` / `vue-tsc` | 🟢 绿（开发侧每次都跑：后端 96 项 / 前端 32 用例） |
| 应用启动 | 🟢 **成功过**（IDE 里跑 `MyBlogApplication`）+ 前端 `npm run dev` 跑过 |
| MySQL / Redis / 库表 | 🟢 本机已装好且运行中，库里已有数据 |
| 首页侧栏分类卡片 | 🟢 浏览器实测渲染出真库数据（2026-09-20） |
| `backend/smoke/category-smoke.sh` | 🟢 使用者 2026-09-20 手工跑通 **22/22**（真 HTTP、真库写入、缓存失效、自排除谓词） |
| `backend/smoke/article-list-smoke.sh` | 🔴 **从未执行过**（第一层与第二层都没有） |
| `/category/:id` 浏览器实测 | 🔴 **从未加载过**——分页、URL 页码、三态、暗色观感全未看 |
| Docker / 部署形态 | 🔴 `docker/` 是空的（主规格 §12 要的 5 个服务都没有） |
| CI | 🔴 无（`.github/`、`.gitlab-ci.yml` 都不存在） |

> **最尖锐的事实已经换了。** 不再是「应用起不来」——环境齐备，起得来。
> 而是：**article 列表切片的端到端从没跑过，`/category/:id` 从没在浏览器里打开过。**
> 这两件是上一轮交付里**唯一还没兑现的部分**，而且**不需要写任何新代码就能还掉**。

**怎么还（不需要写代码，环境已经具备）：**

1. 起后端（IDE 跑 `MyBlogApplication`）+ 起前端（`npm run dev`）
2. 跑 `bash backend/smoke/article-list-smoke.sh` —— 第一层空库即可跑；再按脚本文末 SQL 灌样例数据跑第二层，跑完执行清理 SQL
3. 浏览器打开 `/category/:id`，看分页、页码进 URL、空态、暗色观感
4. 此后**每个切片自带端到端**

**关于 category 那条绿的有效性（我核对过）：** 它**对当前代码仍然有效** —— 此后 `CategoryServiceImpl`、`CategoryController`、`schema.sql` 一行未动，
`application.yml` 在 `6a1e155` 的改动是**纯注释**。但它是**使用者手工跑的**，开发侧从未复现。

> **硬约束（`verification-boundary`）：凭据我不碰，端到端永不声称已验证，只由你手工执行。**
> 上面第 2 步我能代跑脚本，但**前提是你明确授权**（它会打真实 HTTP 到本机 18088）——不授权就留给你。

---

## 6. 建议顺序与理由

```
零  还掉当前验证债          ← 不是切片，不需要写代码，见 §5
B   管理端文章 CRUD + 状态机 ← 第一实现切片
C   文章详情 + 浏览量
D   标签 / 友链 / 通知 / 站点配置
E   首页接线
F   归档 + 搜索
A   Docker 基础设施
G   Vditor + RustFS 图床
H   管理端前端页面 + 仪表盘
I   暗色主题切片            ← 可随时插队
收口：联调 + compose 端到端
```

**理由：**

- **「零」排最前，且不是切片** —— 环境齐备，欠的只是「跑一遍 article 冒烟 + 浏览器看一眼 `/category/:id`」。
  这件事半小时能完，却是上一轮交付唯一还没兑现的部分。**先把它还掉，再动新代码。**
- **B 第一** —— 杠杆最高：它是唯一能「造内容」的路径。没有它，E／F／H 全都在渲染空列表，D 建出来的分类和标签也没有文章可挂。
  它顺带消解 followups §五 那批「断言依赖库内容」的风险。
- **C 紧跟 B** —— 读路径闭环，`/article/:articleId` 才有意义。
- **D 放在 C 之后** —— 体量最大（4 个模块 × 三条链），但技术风险最低（照抄 category），适合在核心链路通了之后批量清。
- **A（Docker）降到第 7 位** —— 这是本次修订的**主要变化**。原先把 A 排第一，理由是「验证债在复利，Docker 是让应用能跑起来的唯一杠杆」；
  但环境实况（§1）显示**应用本来就能在本地跑**，Docker 并不解除任何阻塞。它现在的理由只剩两条，都不紧急：
  (1) 主规格 §12 要求的**部署形态**；(2) G（RustFS 容器）的前置。
  把已经证明可用的东西再容器化，比先容器化再往里塞未验证的代码要稳。
- **G 排在 A 之后** —— 它依赖 RustFS；且它是唯一引入新技术栈的（S3 SDK + Vditor），风险集中，不该在还有结构性空洞时做。
- **H 在 G 之后** —— `/admin/articles` 要 Vditor 编辑器和图床。
- **I 可随时插队** —— 小、独立、不阻塞任何人。

**如果你只想做一件事**：做「零」——它不用写代码，且能立刻把上一轮交付的验证边界从「待手工执行」变成「已跑通」。

---

## 7. 明确不做（复述主规格 §13，省得重新讨论）

多用户／权限分级、评论、消息通知、站内信、定时发布、RSS、访客头像、**全文搜索（仅 LIKE）**、HTTPS／外网暴露。

---

## 附：本文档的事实核对方式

本文所有「现状」结论都来自对 `main@48607c6` 的直接查证，不是从规格推想的：

- 端点覆盖率：`grep '@(Get|Post|Put|Delete)Mapping'` 扫 `backend/src/main/java/com/blog/controller/`，与主规格 §8 的清单对表
- 前端占位页：`frontend/src/views/` 逐文件行数与占位标记扫描；路由表读的是 `frontend/src/router/index.ts` 全文
- 缓存键：`grep 'blog:' backend/src/main/java/`
- 定时任务：`grep '@Scheduled|@EnableScheduling'` —— 零命中
- RustFS：`grep rustfs|s3|presign` 扫 `backend/src` 与 `pom.xml` —— 零命中
- Docker／CI：`find` 扫 `Dockerfile*`／`compose*`／`.env*`／`.github/` —— 零命中
- `category-smoke.sh` 的 22 项：14 处走 `expect_code` 计数器 + 8 处手工 `[通过]`，合计 22，与记载相符
- 运行环境实况（§1）：向使用者逐条确认（2026-09-21），并用磁盘痕迹互相印证 ——
  `.tmp/startup.log`、`backend/target/myblog-backend-0.0.1-SNAPSHOT.jar`、
  `frontend/dist/`、`backend/config/application-local.yml`

### 一条自我更正（留档，别重犯）

本文第一版（`2b22a31`）在 §5 写过「**应用从未被启动过**」，那是**错的**。推导过程是：
「`docker/` 是空的 + 没有 CI + 冒烟脚本没跑 ⇒ 应用没起来过」——把「我没见过」当成了「没发生过」。

更糟的是，同一份文档的上一段就引用了「`category-smoke.sh` 跑通 22/22（真 HTTP、真库写入、缓存失效）」，
而**那条记录本身就要求应用在运行**。文档内部自相矛盾，我却没有察觉。

改正依据：磁盘上一直躺着 `.tmp/startup.log`（2026-09-20 14:44 `Started MyBlogApplication`）与
`.tmp/startup-trace.log`，是使用者为排查配置加载问题留下的；再加上使用者 2026-09-21 的直接确认。

教训与本文 §5 的立场一致：**验证状态只能来自「谁真的做了什么」，不能从「仓库里缺什么」反推。**
