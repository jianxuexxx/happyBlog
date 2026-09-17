# 我的博客系统（myblog）设计规格

> 日期：2026-09-17
> 状态：需求讨论完成，待实现

## 1. 项目概述

参考 [POETIZE - 最美博客](https://poetize.cn/) 的布局与信息结构，搭建一套**纯个人单用户博客**系统。视觉遵循用户指定的**蓝粉淡色系 + iPhone 设计准则**约束（替代参考站原有红橙配色）。

- **定位**：纯个人单用户，单一管理员
- **部署**：Docker Compose 全容器化，**纯 HTTP 内网展示**（不做 HTTPS / 外网暴露）
- **语言**：中文界面

## 2. 技术栈

| 层次 | 技术 | 备注 |
|------|------|------|
| 前端 | Vue 3 + Vite + Element Plus | 单应用含前台 + `/admin` 管理端（路由懒加载） |
| 前端状态 | Pinia | |
| Markdown | Vditor | 编辑与渲染（与参考站同源） |
| 后端 | Spring Boot 3 + MyBatis-Plus | RESTful API，端口 **18088** |
| 数据库 | MySQL 8 | |
| 缓存 | Redis 7 | 缓存 / 计数 / 会话 |
| 对象存储 | RustFS | S3 兼容，存图片，前端 presigned URL 直传 |
| 部署 | Docker Compose | nginx + backend + mysql + redis + rustfs，一键启停 |
| 网关 | Nginx | 托管前端静态 + 反代 `/api → backend:18088` |

**命名约束**：全项目使用驼峰命名法（Java/TS 变量、方法、实体字段、组件、Pinia store）。

## 3. 架构与运行时拓扑

```
                        ┌──────────────┐
   浏览器  ──HTTP──▶    │  Nginx 网关   │
   (前台首页/文章 +      │ 静态+反代 /api │
    /admin 管理端)       └──────┬───────┘
                               │ /api/*
                        ┌──────▼───────┐
                        │ Spring Boot  │──▶ Redis（缓存/计数/会话）
                        │   (18088)    │──▶ MySQL 8（持久化）
                        └──────┬───────┘
                               │ presigned URL 签发
                        ┌──────▼───────┐
                        │   RustFS     │（图片资源）
                        └──────────────┘
```

代码目录结构：

```
myblog/
├── backend/
│   ├── src/main/java/com/blog/
│   │   ├── controller/     # REST API（前台 + admin）
│   │   ├── service/        # 业务逻辑
│   │   ├── mapper/         # MyBatis-Plus Mapper
│   │   ├── entity/         # 实体
│   │   ├── config/         # Redis/MyBatis/Security/全局异常
│   │   └── util/ + dto/
│   ├── docker/Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── views/          # 前台页面
│   │   ├── admin/          # 管理端页面
│   │   ├── api/ components/ router/ store/ styles/
│   └── Dockerfile + nginx.conf
└── docker/
    ├── docker-compose.yml  # mysql + redis + rustfs + backend + frontend-nginx
    └── mysql/ redis/ rustfs/  # 初始化脚本与配置
```

## 4. 数据模型（MySQL）

**公共审计字段**（所有业务表）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `createdAt` | DATETIME | 创建时间，插入自动填充 |
| `updatedAt` | DATETIME | 更新时间，插入+更新自动填充 |
| `deleted` | TINYINT | 逻辑删除：0未删/1已删，默认 0 |

MyBatis-Plus 配置：`@TableField(fill=INSERT/INSERT_UPDATE)` 自动填充时间；`@TableLogic` + 全局 `logic-delete-value: 1 / logic-not-delete-value: 0`。

**删除策略（全局约束）**：所有删除一律**逻辑删除**，包括 `articleTag` 关联表；绝不物理删除。被删后可同名重建（唯一键 + `deleted` 组合）。

### 表结构

**article 文章表**
| 字段 | 类型 | 说明 |
|------|------|------|
| `articleId` | BIGINT PK 自增 | 主键 |
| `title` | VARCHAR(120) | 标题 |
| `summary` | VARCHAR(300) | 摘要 |
| `content` | LONGTEXT | Markdown 正文 |
| `coverImage` | VARCHAR(255) | 封面图 URL（RustFS） |
| `categoryId` | BIGINT | 分类外键，可空（删分类后置空） |
| `status` | TINYINT | 0草稿 / 1公开 / 2私密 |
| `isTop` | TINYINT | 置顶开关 |
| `isRecommended` | TINYINT | 首页推荐位 |
| `viewCount` | INT | 总浏览量（Redis → 定时落库） |
| 公共字段 | | createdAt / updatedAt / deleted |

**category 分类表**：`categoryId`, `categoryName`, `sortOrder`, 公共字段
**tag 标签表**：`tagId`, `tagName`, 公共字段（`tagName` 唯一键与 deleted 组合）
**articleTag 关联表**：`id`, `articleId`, `tagId`, 公共字段（多对多，逻辑删除）
**friendLink 友链表**：`friendLinkId`, `name`, `url`, `avatar`, `description`, `sortOrder`, 公共字段
**siteConfig 站点配置表**：`configKey`(PK), `configValue`, 公共字段
**notice 通知表**（v2 新增）：`noticeId`, `title`, `content`, `startsAt`（生效时间起）, `endsAt`（生效时间止，可空）, 公共字段

关系：文章-分类 多对一；文章-标签 多对多；删除文章/标签时对应 `articleTag` 逻辑删；删除分类时文章 `categoryId` 置空。

## 5. 前端页面与路由

```
/                      首页：导航 + 顶部大轮播图（拖动展开）+ 左侧推荐文章展位
                       + 主区文章瀑布流 + 右侧悬浮通知
/article/:articleId    文章详情：元信息 + Markdown 渲染 + 标签 + 上一篇/下一篇
/category/:id          分类文章列表
/tag/:id               标签文章列表
/archive               时间归档（年/月时间线）
/search                搜索
/friends               友链墙
/about                 关于页
/admin/login           管理端登录
/admin/dashboard       仪表盘（文章数/浏览量/分类/标签统计）
/admin/articles        文章管理（表格 + Vditor 编辑 + 图床）
/admin/categories      分类管理
/admin/tags            标签管理
/admin/friends         友链管理
/admin/notices         通知管理（v2 新增）
/admin/settings        站点配置
```

> 管理端为同一 Vue 应用内 `/admin` 路由懒加载。

**首页布局 v2（2026-09-17 用户细化，前端骨架已完成实现）：**
- **顶部大轮播图**：Swiper 轮播（Autoplay + Pagination 圆点，loop），高度 `clamp(420px, 54vh, 640px)`（按正常图片比率 16:9 观感设计）；z 轴最底层
- **站点信息透明叠加**：标题/副标题叠在轮播图上方、背景透明不遮挡图片，仅顶部留柔和深色渐变保证文字可读
- **展开交互（A 方案，按钮式）**：下方正文板块顶部用负 margin（-180px）遮盖轮播下部，初始只露出轮播上部；正文顶部居中一个**毛玻璃透明圆钮（下箭头图标，展开时旋转 180° 朝上）**——点击按钮正文整体下移复位、图片全部展开；用户向下滚动正文时内容自然上滑再次遮盖
- **左侧栏展位**：sticky 侧栏，当前先展示**推荐文章**（封面缩略 + 标题）
- **右侧悬浮通知**：**右上角**（fixed top 84px / right 24px）毛玻璃浮动卡片；当天有新通知（notice 表）时弹出，当天只弹一次（前端本地记 `blog:notice-seen:{date}`），可手动关闭
- 主区为文章瀑布流卡片；窄屏（<900px）收起左侧栏

**通知模块（v2 新增）：**
- 数据：`notice` 表（见 §4），后台 `/admin/notices` 管理，逻辑删除
- 前台：`GET /api/notice/today` 返回当天最新通知；当天有则弹出悬浮框
- 缓存：当天通知短缓存，发布即失效

## 6. 视觉规范（强制约束）

**【约束】偏蓝粉淡色系 + iPhone 设计准则**（用户明确要求写为强制约束）：

| 维度 | 规范 |
|------|------|
| 主题色系 | 蓝 × 粉 为主，整体淡色系；替代参考站红橙渐变 |
| 主渐变 | 蓝→粉浅渐变（例 `#5b9cf5 → #f48fb1`） |
| 点缀色 | 淡青 `#b2d8f0`、浅粉 `#ffd6e8`、柔和蓝紫 |
| 背景 | 淡色为主：雾蓝/淡粉极浅渐变（例 `#f4f6ff → #fff → #fef2f7`）；暗色模式保留但偏暖柔 |
| 圆角 | 大圆角、连续曲率（iPhone 曲率）：卡片 16–20px，按钮/输入框 10–14px，弹窗 24px+ |
| 字体 | 中文：苹方 PingFang SC；数字/英文：SF Pro / SF Mono；用字重+字号做层级 |
| 间距 | 8pt 栅格（8/16/24/32/40） |
| 阴影 | 柔和、低饱和投影，hover 轻微上浮 |
| 动效 | spring 曲线：200–350ms，`cubic-bezier(0.32,0.72,0,1)`；滚动淡入、Swiper 轮播 |
| 毛玻璃 | 导航栏/浮层 `backdrop-filter: blur` |
| 布局 | 参考 poetize.cn 信息结构 + 卡片式布局 |
| 响应式 | 移动优先，间距/字号按 iOS 断点微调 |
| 组件库 | Element Plus |

**参考站还原要点**（poetize.cn，仅布局参考）：亮色背景 `linear-gradient(180deg,#fff5f7,#fff 50%,#f0f4ff)`；`poetry-font` 诗意字体变体；卡片 `shadow-box` 阴影体系；WOW 滚动淡入 + Swiper 轮播。

## 7. 扩展模块

1. **Markdown 写作 + 图床**
   - 管理端 Vditor（编辑/所见即所得/分屏三模式）
   - 图片上传：拖拽/粘贴/工具栏 → 后端签 presigned URL → 前端直传 RustFS → URL 插入正文
   - 图库管理：图片分页列表（RustFS ListObjectsV2 + 缓存），供引用/删除
2. **站点配置**：键值存 `siteConfig`；后台表单化编辑（站点名/副标题/简介/头像/导航菜单 JSON/SEO/备案号/公告）；Redis 缓存 `blog:siteConfig`，保存即删
3. **友情链接**：后台 CRUD（逻辑删除）+ 前台 `/friends` 友链墙
4. **文章状态三件套**：置顶 `isTop`（列表优先）；私密 `status=2`/草稿 `status=0`（前台隐藏，管理员可预览，私密/草稿不可搜索到）；推荐 `isRecommended`（首页 Swiper，Redis 缓存）

## 8. REST API 设计

统一约定：返回 `{ code, message, data }`（code=0 成功）；`/api/admin/**` 需 JWT；分页 `page`/`pageSize`；驼峰命名。

**前台公共：**
```
GET  /api/site/config                站点配置
GET  /api/home/recommend             首页推荐
GET  /api/article/list               公开文章分页（?categoryId&tagId&keyword&recommended&top）
GET  /api/article/{articleId}        文章详情（浏览量 INCR）
GET  /api/article/archive            时间归档
GET  /api/category/list              分类（含文章数）
GET  /api/tag/list                   标签（含文章数）
GET  /api/tag/cloud                  标签云
GET  /api/friend/list                友链
GET  /api/notice/today               当天通知（v2）
```

**管理端（`/api/admin/**`）：**
```
POST /api/admin/login                登录 → JWT
POST /api/admin/logout
GET  /api/admin/dashboard/stats      仪表盘统计
POST /api/admin/article              创建    PUT /api/admin/article 更新
GET  /api/admin/article/list         管理列表（含草稿/私密/已删筛选）
GET  /api/admin/article/{id}         管理详情
PUT  /api/admin/article/{id}/status  状态流转（草稿→公开→私密/置顶）
DELETE /api/admin/article/{id}       逻辑删除
POST /api/admin/category             新增     PUT /api/admin/category 更新
DELETE /api/admin/category/{id}      逻辑删除
POST /api/admin/tag                  新增     DELETE /api/admin/tag/{id}
POST /api/admin/friend               新增     PUT /api/admin/friend 更新   DELETE /api/admin/friend/{id}
POST /api/admin/notice               新增通知  PUT /api/admin/notice 更新  DELETE /api/admin/notice/{id}
GET  /api/admin/notice/list          通知列表（v2）
GET  /api/admin/upload/presign       RustFS presigned URL 签发
GET  /api/admin/images               图库列表     DELETE /api/admin/images 删除
PUT  /api/admin/site/config          保存站点配置
```

**浏览量方案（已确认 A）**：详情接口返回 Redis `blog:view:{id}` INCR 即时值；Spring Schedule 每 5 分钟将 Redis 计数增量批量写回 MySQL `viewCount` 并清零重计。个人博客接受刷新重复计数。

## 9. 缓存策略

| 键 | 内容 | 失效 |
|----|------|------|
| `blog:siteConfig` | 站点配置 | 后台保存即删 |
| `blog:home:recommend` | 首页推荐 | TTL 5min / 后台改推荐即删 |
| `blog:article:list:{hash}` | 文章分页列表 | TTL 5min |
| `blog:article:{id}` | 文章详情 | TTL 10min / 更新删除即删 |
| `blog:view:{id}` | 浏览量计数 | 定时写回后清零 |
| `blog:admin:token` | 管理员会话 | 登出/过期 |

## 10. 错误处理

- 全局 `@RestControllerAdvice`：`BizException` → 业务码+消息；通用异常 → code:50000 + 友好消息（不泄漏堆栈）
- 参数校验 JSR-380 → code:400xx
- 404（文章不存在/已删/私密）→ code:40400，前台跳 404
- 登录失败 code:40101；Token 无效/过期 code:40100，前端 401 拦截跳登录
- 上传失败（超大小/类型不符/RustFS 不可用）→ code:50300

## 11. 测试策略

- 后端：Service 层单测（JUnit5 + Mockito）重点覆盖——浏览量计数/定时落库、缓存失效、逻辑删除、文章状态机；Controller MockMvc 测接口契约与鉴权
- 前端：Vitest 测关键工具函数（presign 直传流程），组件轻量
- 聚焦核心逻辑，不追覆盖率数字

## 12. 部署（Docker Compose，纯 HTTP 内网）

- 5 服务：`mysql8`/`redis7`/`rustfs`/`backend`/`frontend-nginx`，数据卷持久化
- 外部端口避开通用端口（如 MySQL 33066、Redis 16379）
- `backend`：JDK21 + maven 多段构建；`frontend-nginx`：node 构建 + nginx 托管静态 + 反代 `/api → backend:18088`
- 密钥/连接串走环境变量，不硬编码
- healthcheck 各服务；`docker compose up -d` / `down`
- 网站对外纯 HTTP 内网访问（不做 HTTPS/外网）

## 13. 范围裁剪（YAGNI）

- **不做**：多用户/权限分级、评论、消息通知、站内信、定时发布、RSS、访客头像、全文搜索（仅 LIKE）
- **保留**：全部核心 + 已确认扩展模块

## 14. 图片资产约定（占位）

> **约束**：本项目所有图片资源**一律使用名称占位**，不生成/不准备真实图片。实现中使用统一占位命名引用，由用户按此清单自行提供真实图片后替换。

**统一规则**：
- 占位引用格式：`/images/{占位名}.{ext}`，存在 `frontend/public/images/` 下
- 提供图片后无需改代码，同名替换即可
- 未提供的图片由前端展示为占位图（淡色底 + 名称文字），不报错

**图片清单（后续实现前提供详细清单）**：

| 占位名 | 用途 | 建议格式 | 建议尺寸/大小 |
|--------|------|----------|---------------|
| `site-logo` | 站点 Logo（导航栏） | PNG（透明底） | 192×192，≤100KB |
| `site-avatar` | 站点头像（Hero/关于页） | JPG/PNG | 512×512，≤200KB |
| `hero-bg` | 首页 Hero 背景图 | JPG | 1920×1080，≤500KB |
| `cover-{占位}` | 文章封面图（每篇可不同） | JPG/WebP | 1200×630 或 16:9，≤300KB |
| `friend-{name}` | 友链头像 | PNG/JPG | 96×96，≤50KB |
| `favicon` | 浏览器标签图标 | PNG (ICO) | 32×32 / 192×192 |

> 实现阶段会随组件落地给出**精确到每个使用点的最终清单**（含路径、尺寸、格式）。

## 15. 待实现序列建议

1. Docker 基础设施与初始化（MySQL/Redis/RustFS）
2. 后端骨架（Spring Boot + MyBatis-Plus + 公共字段/逻辑删除/统一返回/全局异常）
3. 数据模型与基础 CRUD（分类/标签/文章）
4. 前台核心接口 + 缓存/浏览量
5. 前端骨架（Vue3 + 主题系统 + 路由）✅（2026-09-17 完成）
6. 前台页面（首页/列表/详情/归档/搜索/友链/关于）
7. 管理端（登录/文章/Vditor/图床/分类标签/友链/配置/仪表盘）
8. 联调 + Docker Compose 编排与测试