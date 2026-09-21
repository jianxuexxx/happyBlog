# HappyBlog - 我的博客系统

参考 [POETIZE - 最美博客](https://poetize.cn/) 的视觉风格，搭建一套个人博客系统。

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus |
| 后端 | Spring Boot 3 + MyBatis-Plus |
| 数据库 | MySQL 8 |
| 缓存 | Redis |
| 对象存储 | RustFS（S3 兼容） |
| 部署 | Docker Compose |

## 目录结构

```
myblog/
├── backend/          # Spring Boot 后端服务
├── frontend/         # Vue3 前端（前台 + /admin 管理端）
├── docker/           # docker-compose 编排及中间件配置（**尚未创建**，见 ROADMAP §3-A）
├── docs/
│   ├── ROADMAP.md    # 后续工作路线图（活文档，随交付更新）
│   └── superpowers/
│       ├── specs/    # 设计规格（冻结的需求来源）
│       ├── plans/    # 实现计划
│       └── followups/ # 已交付切片的延后项与撤回项（下个切片先读）
└── .tmp/             # 参考素材（poetize.cn 还原用，不入库）
```

## 本地开发

后端（18088）。本机 `JAVA_HOME` 指向 JDK 1.8，而项目要 Java 21，所以构建命令必须**内联覆盖** JDK 与 Maven：

```bash
cd backend
JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH \
  /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn spring-boot:run
```

前端（17532）。`/api` 已在 `vite.config.ts` 里反向代理到 18088，故开发期不需要后端配 CORS：

```bash
cd frontend
npm install
npm run dev
npm run test        # vitest（jsdom），6 个文件 / 32 个用例
npx vue-tsc -b      # 类型检查
```

数据库连接等真实凭据放在 `backend/config/application-local.yml`（**已 gitignore，不入库**），由 Spring 自行加载。

建库建表脚本是 `backend/src/main/resources/db/schema.sql`（含 `CREATE DATABASE IF NOT EXISTS happyblog` 与全部 7 张表），**需手工执行**：

```bash
mysql -u<user> -p < backend/src/main/resources/db/schema.sql
```

表结构说明见 `docs/superpowers/specs/2026-09-20-backend-skeleton-design.md` §7。

`backend/smoke/category-smoke.sh` 是 category 竖切链路的端到端冒烟脚本（自带 `admin` 凭据），需后端与 Redis 已启动。

`backend/smoke/article-list-smoke.sh` 是 `GET /api/article/list` 的冒烟脚本（无需凭据，纯公开接口）。它分两层：第一层空库即可跑；第二层需先手工执行脚本末尾的样例数据 INSERT，跑完再执行末尾的清理 SQL。

## 状态

> **已完成：** 前端骨架（主题/路由/布局/首页 v2）与后端骨架（Spring Boot 3 + 公共基建 + JWT 鉴权 + category 竖切链路）。
>
> **前端已接上两条真实数据链路：** 首页侧栏「分类」卡片读 `GET /api/category/list`（2026-09-20，
> 浏览器实测能渲染真库数据，空库显示「暂无分类」）；`/category/:id` 分类页读
> `GET /api/article/list`（2026-09-21，含分页与 URL 页码，空态显示「暂无文章」）。
> **该页面尚未在浏览器中实测** —— 接口与接线已完成，真实渲染效果待人工验证后再写进本节。
>
> **验证边界：** 编译、Mockito 单测、MockMvc 切片、前端 vitest 与 `vue-tsc` 类型检查由开发侧负责；
> category 竖切链路另经 `backend/smoke/category-smoke.sh` 真实 HTTP 跑通。
> **`GET /api/article/list` 的端到端验证尚未完成**：冒烟脚本 `backend/smoke/article-list-smoke.sh`
> 已就绪但**待手工执行**，且因本切片没有文章写入口，其第二层需要先手工灌入样例数据（脚本末尾附 INSERT）。
> **建库建表（DDL）与脚本末尾的落库核对由使用者手工执行。**
> **运行环境已具备：** 后端在 IDE 里跑起来过（`MyBlogApplication`），前端 `npm run dev` 跑过，
> 本机 MySQL 8 与 Redis 已装好且运行中、库表已建、库里已有数据。
> 尚未兑现的只有两件：**`article-list-smoke.sh` 从未执行**、**`/category/:id` 从未在浏览器中打开**。
> 另：`docker/` 仍是空的（部署形态见 [`docs/ROADMAP.md`](docs/ROADMAP.md) §3-A），仓库里**没有 CI**。
> 详见 [`docs/ROADMAP.md`](docs/ROADMAP.md) §5。
>
> **文章列表切片的延后项：** 最终审查判「可合并」后遗留的改进项、以及**已判定不必再提的撤回项**，
> 统一记在 `docs/superpowers/followups/2026-09-21-article-list-slice-followups.md`。下个切片开工前先读那份，
> 免得重新发现、重新争论同一批事。
>
> **下一步：** 完整清单、每项规模与建议顺序见 **[`docs/ROADMAP.md`](docs/ROADMAP.md)**
> （9 个剩余切片 + 横切欠账 + 验证债）。一句话：`/tag/:id`、`/article/:articleId`、`/archive`、`/search`、
> `/friends`、`/about` 仍为占位页，且 —— **系统里还没有任何创建文章的路径**，这是所有前台页面接线的卡点。
>
> **已知未修 —— 两条都并入同一个「暗色主题」切片（用户 2026-09-21 裁定）：**
>
> 1. **暗色偏好刷新后失效**（2026-09-20 裁定暂不动）：`frontend/src/store/theme.ts` 的 `init()` 没有调用点，
>    localStorage 里的暗色偏好在刷新后不会打到 `:root` 上（表现为图标显示「暗色」而页面是亮的），
>    点一次切换按钮才生效。
> 2. **Element Plus 控件在暗色下仍是亮色**：`frontend/src/main.ts` 只 import 了 `element-plus/dist/index.css`，
>    没有 import `element-plus/theme-chalk/dark/css-vars.css`，因此 `el-pagination` 等 EP 组件不随主题走
>    （2026-09-21 由文章列表切片暴露 —— 它是前台第一个把大块 EP 组件放上暗色页的地方）。
>    **原因只有「少 import 一个文件」这一条**，别记成选择器不匹配：`:root.dark` 与 EP 的 `html.dark` 是同一个选择器。
>    修法是 1 行，但会改动全站所有 EP 组件在暗色下的观感，所以与第 1 条合起来单独做、一次验证。
