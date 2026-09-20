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
├── docker/           # docker-compose 编排及中间件配置
│   ├── mysql/
│   ├── redis/
│   └── rustfs/
├── docs/superpowers/
│   ├── specs/        # 设计规格（冻结的需求来源）
│   └── plans/        # 实现计划
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
npm run test        # vitest（jsdom），4 个文件 / 15 个用例
npx vue-tsc -b      # 类型检查
```

数据库连接等真实凭据放在 `backend/config/application-local.yml`（**已 gitignore，不入库**），由 Spring 自行加载。

建库建表脚本是 `backend/src/main/resources/db/schema.sql`（含 `CREATE DATABASE IF NOT EXISTS happyblog` 与全部 7 张表），**需手工执行**：

```bash
mysql -u<user> -p < backend/src/main/resources/db/schema.sql
```

表结构说明见 `docs/superpowers/specs/2026-09-20-backend-skeleton-design.md` §7。

`backend/smoke/category-smoke.sh` 是 category 竖切链路的端到端冒烟脚本（自带 `admin` 凭据），需后端与 Redis 已启动。

## 状态

> **已完成：** 前端骨架（主题/路由/布局/首页 v2）与后端骨架（Spring Boot 3 + 公共基建 + JWT 鉴权 + category 竖切链路）。
>
> **前端已接上首条真实数据链路（2026-09-20）：** 首页侧栏「分类」卡片读 `GET /api/category/list`，浏览器实测能渲染真库数据（空库显示「暂无分类」），点击进入 `/category/:id`。
>
> **验证边界：** 编译、Mockito 单测、MockMvc 切片由开发侧负责；category 竖切链路另经 `backend/smoke/category-smoke.sh` 真实 HTTP 跑通（22/22，含真库写入与缓存失效断言）。**建库建表（DDL）与脚本末尾的落库核对由使用者手工执行。**
>
> **下一步：** `GET /api/article/list?categoryId=`（`/category/:id` 目前仍是占位页），其余业务表的 CRUD、前台接口与浏览量统计、管理端页面、RustFS 上传、Docker Compose 编排。
>
> **已知未修（用户 2026-09-20 裁定暂不动）：** 暗色主题刷新后不生效 —— `frontend/src/store/theme.ts` 的 `init()` 没有调用点，localStorage 里的暗色偏好在刷新后不会打到 `:root` 上（表现为图标显示「暗色」而页面是亮的），点一次切换按钮才生效。
