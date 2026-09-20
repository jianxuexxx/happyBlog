# 后端骨架 + 竖切链路（category）设计规格

> 日期：2026-09-20
> 状态：设计已确认，待实现
> 上游规格：[2026-09-17-myblog-design.md](./2026-09-17-myblog-design.md)（下文简称「主规格」）

## 1. 本次目标与范围

### 目标

在 `backend/` 下搭建可运行的 Spring Boot 3 后端，**竖切一条完整链路**：把「HTTP → Controller → Service → MyBatis-Plus → MySQL → Redis」整条链走通并被验证，之后每张业务表照抄同一套模式落地。

### 范围内（本次交付）

1. 工程搭建（`pom.xml` + 配置 + 启动类）
2. 公共基建：统一返回、错误码、全局异常、公共字段自动填充、逻辑删除、分页插件、Redis 配置、JWT 鉴权、OpenAPI 导出
3. **全部 7 张表**的 DDL（`schema.sql`）、实体、Mapper
4. **仅 `category` 一条**完整 CRUD 端到端（公开读 + JWT 保护的写）
5. 极简 JWT 基建（登录 / 登出 / 拦截器）
6. 测试：Service 层 Mockito 单测、Controller 层 MockMvc 契约与鉴权测试

### 明确出界（本次不做）

- `article` / `tag` / `friendLink` / `notice` / `siteConfig` 的 Service 与 Controller（**表、实体、Mapper 建好，业务逻辑不写**）
- `articleTag` 关联关系的维护逻辑（随 article CRUD 一起做）
- 浏览量 Redis 计数与定时落库（主规格 §8）
- RustFS presign 上传与图库
- `blog:article:*` / `blog:home:recommend` / `blog:siteConfig` 缓存（主规格 §9 中除 `blog:category:list` 与 `blog:admin:token` 外的全部键）
- 前端对接（前端骨架已完成，本次不改前端）
- Docker Compose 编排（主规格 §15 第 1 / 8 步）

### 与主规格 §15 序列的对应

本次 = 主规格 §15 的**第 2 步（后端骨架）** + **第 3 步中 `category` 这一条竖切**。第 3 步的其余表、第 4 步（前台接口 / 缓存 / 浏览量），留待后续。

## 2. 实现风格：显式优先（已确认）

在三个方案中选定**方案 A「显式优先」**：

| 维度 | 取法 | 理由 |
|------|------|------|
| 返回体 | 显式 `Result<T>` 类型，Controller 每方法显式 `Result.ok(data)` | 类型明确、可断言、可 grep；不用 `ResponseBodyAdvice` 自动包装（其 String/byte[] 返回值有已知坑，且隐式结构难追踪） |
| 缓存 | `CacheUtil` 封装 `RedisTemplate`，键名逐字对齐主规格 §9，TTL 与失效点显式写在 Service | 主规格把键名钉死了，手写才逐字可控 |
| 异常 | 显式抛 `BizException(code, message)`，`@RestControllerAdvice` 统一转码 | 错误码来自主规格 §10，必须逐字对应 |
| DDL | 手写 `schema.sql`，由用户手动执行 | 用户要求掌控建库建表 |

**被否决的方案：** `ResponseBodyAdvice` 自动包装 + Spring Cache 注解 + 自动填充全自动（代码少，但在键名与响应结构已冻结的前提下，隐式化是负债）；混合方案（仍要踩 `ResponseBodyAdvice` 的坑）。

## 3. 环境约束（重要）

本机环境经实测确认如下，**实现与验证都必须按此执行**：

| 项 | 值 | 说明 |
|----|-----|------|
| JDK | `E:\works\jdk21`（Temurin 21.0.12.1 LTS） | **会话内使用，不改本机 `JAVA_HOME`**（本机 `JAVA_HOME=E:\works\java8\jdk`，JDK 8 保留不动） |
| Maven | `E:\works\apache-maven-3.8.6-bin\apache-maven-3.8.6` | 未加入 PATH，用绝对路径调用 |
| 本地仓库 | `C:\Users\jianx\.m2\repository` | 由用户级 `~/.m2/settings.xml` 指定 |
| 镜像 | `alimaven` → `http://maven.aliyun.com/nexus/content/groups/public/` | **实测可用**：`mybatis-plus-spring-boot3-starter:3.5.9` 已通过该镜像成功下载。Maven 3.8.6 的 http 拦截未生效（用户 settings 中 `alimaven` 的 `mirrorOf=central` 优先匹配），无需额外配置 |
| MySQL | 本机 `MySQL 8.0.32`，监听 `3306` | 用户手动建库建表 |
| Redis | 本机 `E:\works\Redis`（Windows 版），**当前未启动** | 用户手动启动 |
| Docker | CLI 29.8.0 + Compose v5.5.1 已装，**引擎未运行** | 本次不使用 |

**统一命令前缀**（所有 Maven 命令）：

```bash
export JAVA_HOME=/e/works/jdk21
export PATH="$JAVA_HOME/bin:$PATH"
MVN=/e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn
```

**验证归属（已确认）：** 实现者无法访问 MySQL/Redis 凭证，因此

- **实现者负责验证**：编译、Mockito 单测、MockMvc 契约测试（均不依赖真实 DB/Redis）
- **用户负责验证**：执行 `schema.sql`、填写本地连接串、运行冒烟脚本并反馈输出

## 4. 依赖与版本

`backend/pom.xml`，parent `spring-boot-starter-parent:3.4.3`（本地 `.m2` 已缓存），`java.version=21`，`maven.compiler.release=21`，编码 UTF-8。

| 依赖 | 版本 | 用途 |
|------|------|------|
| `spring-boot-starter-web` | BOM | REST |
| `spring-boot-starter-validation` | BOM | JSR-380 参数校验 |
| `spring-boot-starter-data-redis` | BOM | Redis |
| `mybatis-plus-spring-boot3-starter` | **3.5.9** | ORM。**必须用 boot3 starter**，普通 `mybatis-plus-boot-starter` 是 Spring Boot 2 / javax 体系 |
| `mysql-connector-j` | BOM 管理 | 驱动，scope runtime |
| `java-jwt` | **4.4.0** | JWT 签发/校验（HS256）。本地已缓存 |
| `springdoc-openapi-starter-webmvc-ui` | **2.7.0** | OpenAPI 导出（对应 Boot 3.4） |
| `lombok` | **1.18.36** | 实体/DTO 样板，scope provided |
| `spring-boot-starter-test` | BOM | JUnit5 + Mockito + MockMvc，scope test |

**不引入 Maven Wrapper**：直接用已装的 Maven 3.8.6。少一个「首次运行需下载 Maven 发行包」的失败点；后续 Dockerfile 内自带 Maven，不受影响。

## 5. 包结构

根包 `com.blog`：

```
com.blog
├── MyBlogApplication.java                     启动类
├── common/
│   ├── Result.java                            统一返回 {code, message, data}
│   ├── ResultCode.java                        错误码枚举
│   ├── PageResult.java                        分页返回 {total, page, pageSize, list}
│   ├── BizException.java                      业务异常（携带 code）
│   └── GlobalExceptionHandler.java            @RestControllerAdvice
├── config/
│   ├── MybatisPlusConfig.java                 分页插件 + 注册 MetaObjectHandler
│   ├── AuditMetaObjectHandler.java            createdAt/updatedAt 自动填充
│   ├── RedisConfig.java                       RedisTemplate<String,Object> + Jackson 序列化
│   ├── WebMvcConfig.java                      JWT 拦截器注册
│   └── OpenApiConfig.java                     springdoc 配置
├── security/
│   ├── JwtUtil.java                           签发 / 校验 HS256
│   ├── AdminTokenStore.java                   Redis 中的管理员会话（blog:admin:token）
│   └── AdminAuthInterceptor.java              保护 /api/admin/**
├── entity/                                    Article · Category · Tag · ArticleTag · FriendLink · SiteConfig · Notice
├── mapper/                                    7 个 Mapper 接口
├── service/
│   ├── CategoryService.java
│   ├── AdminAuthService.java
│   └── impl/                                  CategoryServiceImpl · AdminAuthServiceImpl
├── controller/
│   ├── CategoryController.java                公开读
│   ├── AdminCategoryController.java           写
│   └── AdminAuthController.java               登录 / 登出
├── dto/
│   ├── CategorySaveDTO.java · CategoryVO.java
│   └── LoginDTO.java · LoginVO.java
└── util/
    └── CacheUtil.java                         Redis 读写 / 失效封装
```

**命名约束（主规格 §2）：全项目驼峰命名**——Java 变量/方法/实体字段、JSON 字段一律驼峰。

## 6. 接口设计与前端契约

### 路径策略

**控制器显式映射 `/api/**`**，不使用 `server.servlet.context-path`（避免 swagger / actuator 跟着挪到 `/api` 下）。

理由：前端 `vite.config.ts` 中 `'/api'` 代理**无 rewrite**，原样转发到 `localhost:18088`，因此后端必须真实服务 `/api/...`；同时与主规格 §8 的字面路径一致，后续 nginx 反代 `location /api/` 也直接对齐。

### 统一返回（严格对齐 `frontend/src/api/http.ts`）

```json
{ "code": 0, "message": "success", "data": {} }
```

- `code = 0` 表示成功，前端 `unwrapResult()` 取 `data`，否则抛 `Error(message)`
- 失败时 `data` 为 `null`
- 前端响应拦截器对 **`code === 40100`** 有特殊处理（清 `localStorage` 的 `blog-admin-token`），因此 token 失效**必须**返回 40100

### 错误码（`ResultCode` 枚举）

| 码 | 含义 | 来源 |
|----|------|------|
| `0` | 成功 | 主规格 §8 |
| `40001` | 参数校验失败 | 主规格 §10「400xx」细化 |
| `40002` | 分类名已存在 | 本切片新增（主规格未定，此处定死） |
| `40100` | token 无效 / 过期 | 主规格 §10 + 前端拦截器依赖 |
| `40101` | 登录失败 | 主规格 §10 |
| `40400` | 资源不存在 / 已删除 | 主规格 §10 |
| `50000` | 通用异常 | 主规格 §10 |
| `50300` | 上传失败 | 主规格 §10（本次不实现，仅占位枚举，**不出现在任何代码路径**） |

> 40100 与 40101 的区别：**40101** 是「登录接口凭账号密码换取 token 时失败」；**40100** 是「已有 token 无效或过期」，前端据此跳登录。

### 本切片接口

| 方法 | 路径 | 鉴权 | 请求 | 成功返回 `data` |
|------|------|------|------|----------------|
| GET | `/api/category/list` | 公开 | — | `CategoryVO[]` |
| POST | `/api/admin/login` | 公开 | `LoginDTO` | `LoginVO` |
| POST | `/api/admin/logout` | JWT | — | `null` |
| POST | `/api/admin/category` | JWT | `CategorySaveDTO` | 新建的 `categoryId`（`Long`） |
| PUT | `/api/admin/category` | JWT | `CategorySaveDTO`（含 `categoryId`） | `null` |
| DELETE | `/api/admin/category/{categoryId}` | JWT | — | `null` |

### DTO 定义

**`LoginDTO`**：`username`（`@NotBlank`）、`password`（`@NotBlank`）
**`LoginVO`**：`token`（String）
**`CategorySaveDTO`**：`categoryId`（Long，仅更新时必填，用 `@Null(groups=Create)` / `@NotNull(groups=Update)` 校验分组）、`categoryName`（String，`@NotBlank`，`@Size(max=50)`）、`sortOrder`（Integer，可空，默认 0）
**`CategoryVO`**：`categoryId`、`categoryName`、`sortOrder`、`articleCount`

### 鉴权头格式

```
Authorization: Bearer <token>
```

## 7. 数据模型与 DDL

`backend/src/main/resources/db/schema.sql` —— 建库 + 7 张表，全部 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`。

**字段名使用主规格 §4 定义的驼峰名**（MySQL 允许，DDL 中以反引号包裹）。MyBatis-Plus 实体字段与之同名，免去映射配置。

### 公共审计字段（所有 7 张表）

```sql
`createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updatedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`deleted`   TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删/1已删',
```

### 表清单与要点

| 表 | 主键 | 关键字段 | 索引 / 唯一键 |
|----|------|---------|--------------|
| `article` | `articleId` BIGINT AUTO_INCREMENT | `title` VARCHAR(120)、`summary` VARCHAR(300)、`content` LONGTEXT、`coverImage` VARCHAR(255)、`categoryId` BIGINT NULL、`status` TINYINT、`isTop` TINYINT、`isRecommended` TINYINT、`viewCount` INT DEFAULT 0 | `idx_article_status(status, isTop, createdAt)`、`idx_article_categoryId(categoryId)` |
| `category` | `categoryId` BIGINT AUTO_INCREMENT | `categoryName` VARCHAR(50)、`sortOrder` INT DEFAULT 0 | `uk_category_categoryName(categoryName, deleted)` |
| `tag` | `tagId` BIGINT AUTO_INCREMENT | `tagName` VARCHAR(50) | `uk_tag_tagName(tagName, deleted)` |
| `articleTag` | `id` BIGINT AUTO_INCREMENT | `articleId`、`tagId` | `idx_articleTag_articleId`、`idx_articleTag_tagId`、`uk_articleTag(articleId, tagId, deleted)` |
| `friendLink` | `friendLinkId` BIGINT AUTO_INCREMENT | `name` VARCHAR(50)、`url` VARCHAR(255)、`avatar` VARCHAR(255)、`description` VARCHAR(200)、`sortOrder` INT | — |
| `siteConfig` | `configKey` VARCHAR(64) PK（非自增） | `configValue` TEXT | — |
| `notice` | `noticeId` BIGINT AUTO_INCREMENT | `title` VARCHAR(120)、`content` TEXT、`startsAt` DATETIME、`endsAt` DATETIME NULL | `idx_notice_startsAt_endsAt(startsAt, endsAt)` |

### 约束决策

- **不建外键约束**：主规格 §4 定义的是逻辑删除语义下的关系（删分类置空文章 `categoryId`、删标签逻辑删 `articleTag`），引用的完整性由 Service 层保证。物理外键在逻辑删除模型下会阻碍「同名重建」并增加维护负担
- **唯一键与 `deleted` 组合**：满足主规格 §4「被删后可同名重建」
- `siteConfig` 主键为业务键 `configKey`，无自增列

## 8. 公共基建行为

### 公共字段自动填充

`AuditMetaObjectHandler implements MetaObjectHandler`：

- `insertFill`：填 `createdAt` + `updatedAt`（`LocalDateTime.now()`）
- `updateFill`：填 `updatedAt`
- 实体对应字段标注 `@TableField(fill = FieldFill.INSERT)` / `@TableField(fill = FieldFill.INSERT_UPDATE)`

> 注：DDL 里也给这两个列加了 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` 作为兜底（例如用户手动 SQL 插入的场景），两者不冲突。

### 逻辑删除

- 实体 `deleted` 字段标注 `@TableLogic`
- `application.yml` 全局配置 `mybatis-plus.global-config.db-config.logic-delete-value: 1` / `logic-not-delete-value: 0`
- 所有删除一律逻辑删除，含 `articleTag` 关联表（主规格 §4 全局约束）

### 分页

`MybatisPlusConfig` 注册 `MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.MYSQL)`。本切片的 `category/list` **不分页**（分类量级小），但插件本次就位，供后续 article 列表直接用。

`PageResult<T>`：`{ total, page, pageSize, list }`。

### 全局异常处理

`GlobalExceptionHandler`（`@RestControllerAdvice`）：

| 捕获 | → code | message |
|------|--------|---------|
| `BizException` | 异常自带 code | 异常自带 message |
| `MethodArgumentNotValidException` | 40001 | 拼接首个字段错误：`字段名: 校验消息` |
| `Exception` | 50000 | 固定友好文案「服务器开小差了」 |

**规则：日志打印完整堆栈（`log.error`），响应体一律不含堆栈或 SQL 片段**（主规格 §10）。

### JWT 鉴权

- **算法** HS256，secret 来自 `blog.jwt.secret`（环境变量 `BLOG_JWT_SECRET` 可覆盖）
- **有效期** 7 天，配置项 `blog.jwt.expire-days`
- **载荷**：`sub`（管理员用户名）、`jti`（随机 UUID）、`iat`、`exp`
- **会话存储**：登录成功把 token 写入 Redis `blog:admin:token`，TTL = 7 天
- **拦截器 `AdminAuthInterceptor`**：拦截 `/api/admin/**`，**排除** `/api/admin/login`；校验「签名与过期时间有效」**且**「Redis 中 `blog:admin:token` 的值与该 token 一致」——两者都过才放行，否则返回 40100
- **登出**：删除 Redis `blog:admin:token`，token 立即失效

> 单管理员模型下 `blog:admin:token` 为单值键（非多端会话），符合主规格 §9 的键定义与 §13 的 YAGNI 裁剪。

**管理员凭证**：配置项 `blog.admin.username` / `blog.admin.password`，**不建 admin 表**（主规格 §13 单用户）。`application.yml` 中给开发默认值并在注释中标注生产必须用环境变量覆盖，本地真实值由用户写入 gitignored 的 `application-local.yml`。

### 缓存

`CacheUtil` 封装 `RedisTemplate<String, Object>`，提供 `get` / `set(key, value, ttl)` / `delete(key)`。`RedisConfig` 配置 Jackson 序列化器（`GenericJackson2JsonRedisSerializer`）并设置 `StringRedisSerializer` 为 key 序列化器。

本切片使用的键：

| 键 | 内容 | TTL | 失效时机 | 来源 |
|----|------|-----|---------|------|
| `blog:category:list` | 分类列表（含文章数） | 5 分钟 | 分类新增 / 更新 / 删除后立即删除 | **本设计新增**（主规格 §9 未列此键；按 §9 既有 `blog:模块:用途` 命名约定补充） |
| `blog:admin:token` | 管理员会话 token | 7 天 | 登出时删除 | 主规格 §9 |

> 主规格 §9 已列但本切片**不使用**的键：`blog:siteConfig`、`blog:home:recommend`、`blog:article:list:{hash}`、`blog:article:{id}`、`blog:view:{id}`。

### OpenAPI 导出

`springdoc-openapi-starter-webmvc-ui` 自动扫描，`OpenApiConfig` 补充标题/版本/描述。UI 路径 `/swagger-ui.html`，JSON 路径 `/v3/api-docs`。

> 依据：既有决策「后端统一导出 OpenAPI」（Apifox MCP 暂不配置，后端统一导出供导入）。

## 9. 竖切功能：category

### `GET /api/category/list`（公开，走缓存）

1. 读 `blog:category:list`；命中即返回
2. 未命中 → 查库 → 写缓存（TTL 5 分钟）→ 返回
3. 排序：`sortOrder ASC, categoryId ASC`

**`articleCount` 的取法**：单条 `LEFT JOIN article ... GROUP BY` 的自定义 Mapper 方法，**逻辑删除条件显式写进 join 条件**：

```sql
SELECT c.categoryId, c.categoryName, c.sortOrder,
       COUNT(a.articleId) AS articleCount
FROM category c
LEFT JOIN article a
       ON a.categoryId = c.categoryId
      AND a.deleted = 0
WHERE c.deleted = 0
GROUP BY c.categoryId, c.categoryName, c.sortOrder
ORDER BY c.sortOrder ASC, c.categoryId ASC
```

实现方式：在 `CategoryMapper` 上用 **`@Select` 注解**承载该 SQL（单条查询，注解比 XML 文件更贴「显式且就近可读」，不引入 XML 映射文件）。

不做内存聚合（两次查询往返）、不加冗余计数字段（一致性维护成本，个人博客不值当）。

### `POST /api/admin/category`（新增）

- 校验 `categoryName` 非空且 ≤50
- 重名校验：查 `categoryName` 相同且 `deleted = 0` 的记录，存在则抛 `BizException(40002, "分类名已存在")`
- 插入；`sortOrder` 为空时取 0
- 删除缓存 `blog:category:list`
- 返回新建的 `categoryId`

### `PUT /api/admin/category`（更新）

- 校验 `categoryId` 非空、`categoryName` 非空
- 存在性校验：按 `categoryId` 查（`@TableLogic` 自动过滤已删），不存在 → `BizException(40400, "分类不存在")`
- 重名校验：同名的**其他**分类存在（`categoryId != 当前` 且 `deleted = 0`）→ 40002
- 更新 `categoryName` / `sortOrder`
- 删除缓存 `blog:category:list`

### `DELETE /api/admin/category/{categoryId}`（逻辑删除）

`@Transactional(rollbackFor = Exception.class)`，事务内：

1. 存在性校验，不存在 → 40400
2. 逻辑删除该分类（`@TableLogic` → `UPDATE category SET deleted = 1 WHERE categoryId = ?`）
3. **该分类下所有未删文章的 `categoryId` 置空**（主规格 §4 关系约束）——`UPDATE article SET categoryId = NULL WHERE categoryId = ? AND deleted = 0`
4. 删除缓存 `blog:category:list`

### `POST /api/admin/login` / `POST /api/admin/logout`

**登录**：比对配置中的用户名密码（`MessageDigest.isEqual` 做常量时间比较，避免时序侧信道）；不匹配 → `BizException(40101, "用户名或密码错误")`。匹配 → 签发 JWT，写 Redis `blog:admin:token`（TTL 7 天），返回 `LoginVO{token}`。

**登出**：删除 Redis `blog:admin:token`，返回成功。

## 10. 配置

| 文件 | 入库 | 内容 |
|------|------|------|
| `backend/src/main/resources/application.yml` | ✅ | 公共配置：`server.port=18088`、数据源与 Redis 用 `${DB_URL:...}` 等环境变量占位 + 开发注释、MyBatis-Plus（逻辑删除、驼峰映射、SQL 日志）、springdoc、`blog.jwt.*`、`blog.admin.*` |
| `backend/src/main/resources/application-test.yml` | ✅ | 测试 profile（单测实际不加载数据源，见 §11） |
| `backend/config/application-local.yml` | ❌ **gitignored** | 用户真实连接串。Spring Boot 通过 `spring.config.additional-location=optional:file:./config/` 加载 |

**`.gitignore` 需新增**：`backend/config/`、`backend/target/`、`*.log`。

> 实现者**不读取** `application-local.yml`，仅由 Spring 加载。

## 11. 测试策略

实现者可自行运行（**全程不依赖真实 MySQL / Redis**）：

| 测试类 | 手段 | 覆盖 |
|--------|------|------|
| `CategoryServiceImplTest` | Mockito（mock Mapper + CacheUtil） | 新增重名 → 40002；更新不存在 → 40400；更新重名 → 40002；删除级联清空 `categoryId` 且清缓存；列表缓存命中时不查库、未命中时查库并写缓存；列表排序按 `sortOrder` |
| `AdminAuthServiceImplTest` | Mockito（mock CacheUtil） | 凭证正确 → 返回非空 token 且写入 Redis；密码错 → 40101 |
| `JwtUtilTest` | 纯单元 | 签发后可校验通过；篡改后校验失败；过期 token 校验失败 |
| `GlobalExceptionHandlerTest` | 直接调用 handler 方法 | `BizException` → 对应码；`RuntimeException` → 50000 且 `message` 不含堆栈信息 |
| `ResultCodeTest` | 纯单元 | 断言各错误码取值与 §6 表格一致（防回归） |
| `CategoryControllerTest` | `@WebMvcTest` + `@MockitoBean` service | `GET /api/category/list` → `$.code=0` 且 `$.data[0].articleCount` 存在；`POST /api/admin/category` 无 token → `$.code=40100` |
| `AdminAuthControllerTest` | `@WebMvcTest` + `@MockitoBean` service | 登录契约 `$.code=0` 且 `$.data.token` 存在 |

**注意事项：**

- Spring Boot 3.4 中 `@MockBean` 已废弃，**使用 `@MockitoBean`**
- `@WebMvcTest` 不加载数据源与 Redis，且不扫描 `@Component` / `@Service`。`AdminAuthInterceptor` 依赖的两者——`JwtUtil` 与 `AdminTokenStore`——**均需以 `@MockitoBean` 注入**（`WebMvcConfig` 作为 `WebMvcConfigurer` 会被 `@WebMvcTest` 加载，故拦截器本身可用）
- 测试聚焦核心逻辑，不追覆盖率数字（主规格 §11）

## 12. 交付给用户执行的部分

实现者无法访问 MySQL / Redis，以下由用户执行：

### 12.1 建库建表

交付 `backend/src/main/resources/db/schema.sql`（含 `CREATE DATABASE IF NOT EXISTS myblog` 与全部 7 张表），用户手动执行。

### 12.2 本地连接串

交付 `backend/config/application-local.yml` **模板**（含 `url` / `username` / `password` / `redis host` 占位与注释），用户填写。

### 12.3 启动命令

```bash
export JAVA_HOME=/e/works/jdk21
export PATH="$JAVA_HOME/bin:$PATH"
cd D:/projects/myblog/backend
/e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn spring-boot:run \
  -Dspring-boot.run.profiles=local
```

### 12.4 冒烟脚本 `backend/smoke/category-smoke.sh`

curl 实现，依次执行并在注释中写明**每一步的预期输出**：

1. `POST /api/admin/login` → 取 `data.token`
2. 不带 token `POST /api/admin/category` → 预期 `code=40100`
3. 带 token 新增分类「冒烟测试分类」→ 预期 `code=0` 且返回 `categoryId`
4. `GET /api/category/list` → 预期列表含该分类且 `articleCount=0`
5. `PUT /api/admin/category` 改名 + 改 `sortOrder` → 预期 `code=0`
6. 再次 `GET /api/category/list` → 预期名称与排序已更新（验证缓存失效已生效）
7. 重复第 3 步新增同名分类 → 预期 `code=40002`
8. `DELETE /api/admin/category/{id}` → 预期 `code=0`
9. `GET /api/category/list` → 预期该分类消失
10. `POST /api/admin/logout` → 预期 `code=0`
11. 用登出前的 token 再访问 `POST /api/admin/category` → 预期 `code=40100`（验证登出真实失效）
12. 脚本末尾打印提示，让用户执行两条 SQL 核对落库结果：
    - `SELECT categoryId, categoryName, deleted FROM category WHERE categoryName='冒烟测试分类';` → 预期 `deleted=1`
    - `SELECT articleId, categoryId FROM article WHERE categoryId=<该分类id>;` → 预期无结果（若用户此前给该分类挂过文章，则 `categoryId` 为 `NULL`）

## 13. 完成标准

本次实现完成，需同时满足：

1. `mvn -q clean package` 通过（编译 + 全部测试）
2. 上述 §11 全部测试类通过
3. `schema.sql`、`application-local.yml` 模板、启动命令、`category-smoke.sh` 四项交付物齐备
4. 冒烟脚本已交付给用户，**用户执行结果待反馈**——在用户反馈前，本次不宣称「端到端已跑通」，只宣称「编译与单测通过 + 冒烟脚本已交付」
