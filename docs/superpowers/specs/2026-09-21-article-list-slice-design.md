# 文章列表接口 + 分类页接线（article list slice）设计规格

> 日期：2026-09-21
> 状态：设计已确认，待实现
> 上游规格：[2026-09-17-myblog-design.md](./2026-09-17-myblog-design.md)（下文简称「主规格」）

## 1. 本次目标与范围

### 目标

落地 **`GET /api/article/list`**，并让前端 `/category/:id` 从占位页变成真实可用页面。这是继 `blog:category:list`（首页侧栏）之后**前端第二条真实数据链路**，也是主规格 §15 第 4 步（前台核心接口）的第一个文章类接口。

### 范围内（本次交付）

1. `GET /api/article/list`：**主规格 §8 定义的完整筛选面**（`categoryId` / `tagId` / `keyword` / `recommended` / `top`）+ 分页
2. 后端 5 个新类（§2），**零修改既有类**
3. 前端：`api/article.ts`（新增）、`components/ArticleCard.vue`（抽取）、`views/Category.vue`（重写）、`views/Home.vue`（改用 `ArticleCard`）
4. 测试：后端 Mockito 单测 + MockMvc 切片；前端 Vitest
5. 冒烟脚本 `backend/smoke/article-list-smoke.sh`（两层，见 §7）

### 明确出界（本次不做）

- `GET /api/article/{articleId}` 文章详情（含浏览量 Redis INCR 与定时落库）
- 管理端 article CRUD。**因此本次没有任何文章写入口，真库 `article` 表的数据需手工灌入**（影响 §7 的验证边界）
- `GET /api/article/archive`、`GET /api/home/recommend`
- 列表缓存（见 §9 偏离说明）
- `/tag/:id`、`/archive`、`/search`、`/friends`、`/about`、`/admin/**` 各页接线
- RustFS 上传与图库、Docker Compose 编排

### 与主规格 §15 序列的对应

本次 = 第 4 步（前台核心接口 + 缓存/浏览量）中的**文章列表部分**（不含缓存、不含浏览量）+ 第 6 步（前台页面）中的 **`/category/:id` 一页**。

## 2. 组件边界

### 后端（新增 5 个类，零修改既有类）

| 文件 | 职责 |
|---|---|
| `controller/ArticleController.java` | 参数接收与转发，无业务逻辑 |
| `service/ArticleService.java` | `PageResult<ArticleListVO> list(ArticleQueryDTO q)` |
| `service/impl/ArticleServiceImpl.java` | 参数钳制、拼 wrapper、调 `selectPage`、转 VO |
| `dto/ArticleQueryDTO.java` | 5 个筛选参数 + `page` / `pageSize` |
| `dto/ArticleListVO.java` | `articleId` / `title` / `summary` / `coverImage` / `createdAt` / `viewCount` |

**`ArticleMapper` 零改动**：继承自 `BaseMapper` 的 `selectPage` 配合 `PaginationInnerInterceptor`（已在 `MybatisPlusConfig` 注册）即可。本次没有需要手写 SQL 的场景——`tagId` 用 wrapper 的 `exists` 子查询表达（§4）。

### 前端

| 文件 | 变更 |
|---|---|
| `src/api/article.ts` | 新增。`fetchArticleList()` + `ArticleListItem` / `ArticleQuery` / `PageResult` 类型 |
| `src/components/ArticleCard.vue` | 新增（从 `Home.vue` 抽取） |
| `src/views/Category.vue` | 重写（原为 6 行占位） |
| `src/views/Home.vue` | 改用 `<ArticleCard>`，骨架数据与其余布局不动 |

**关于抽取 `ArticleCard`**：卡片是最容易在两处复制后各自漂移的东西，而首页即将接真数据。抽取范围**刻意压到最小**：只提取卡片本身，不动 `Home.vue` 的轮播、负 margin 展开、侧栏。

### 时间字段格式（已确认：不改）

**沿用 Spring Boot 默认的 ISO-8601**（`2026-09-21T14:30:00`），**不注册全局 Jackson 定制，也不加 `@JsonFormat`**。

理由有二：

1. **ISO-8601 是 JS 唯一可靠的线上格式。** `new Date("2026-09-21T14:30:00")` 各引擎均可正确解析；而 `new Date("2026-09-21 14:30:00")` 是**实现相关的**，Safari/JSC 返回 `Invalid Date`。管理端将来要发时间回后端时，这个差别会真的咬人。
2. **全局 Jackson 定制会波及缓存存储格式。** `RedisConfig` 注入的是容器里的 `ObjectMapper`，而 `Jackson2ObjectMapperBuilderCustomizer` 定制的正是同一个 bean——一个「改 HTTP 响应格式」的动作会连带改掉 Redis 里的值形态，把两件无关的事焊在一起。这与上一份规格 §2 定下的「显式优先」取向相抵触。

前端卡片只显示日期，`createdAt.slice(0, 10)` 即可。

**连带改动**：`application.yml` 中 `spring.jackson` 那段注释目前写着「将来 article 接口涉及时间字段时，用 `@JsonFormat` 或注册 Customizer」——本次结论与该提示相反，注释须改写为「已按 ISO-8601 保留默认，理由见本规格 §2」，避免留下会诱导后人做错选择的过时文字。

## 3. 接口契约

```
GET /api/article/list
```

无需 JWT（不属于 `/api/admin/**`）。

### 参数（全部可选，query string，多条件之间 AND）

| 参数 | 类型 | 默认 / 边界 | 语义 |
|------|------|------------|------|
| `categoryId` | Long | — | 分类筛选 |
| `tagId` | Long | — | 标签筛选 |
| `keyword` | String | 先 `trim()`，trim 后为空则不参与筛选 | 标题 **或** 摘要模糊匹配 |
| `recommended` | Boolean | — | **仅 `true` 生效**，只返回 `isRecommended = 1` |
| `top` | Boolean | — | **仅 `true` 生效**，只返回 `isTop = 1` |
| `page` | int | 默认 1，最小 1，越界钳制 | 页码 |
| `pageSize` | int | 默认 10，最小 1，**最大 50**，越界钳制 | 每页条数 |

**`recommended` / `top` 的开关语义**：两个布尔都是「**加上这个约束**」的开关。`false` 与 `null` 等价，都表示**不筛选**；`false` **不是**「筛选出非推荐的」。这一点不写清楚，实现时最容易想当然地写成 `eq(recommended != null, ...)`。

**`keyword` 的 trim 语义**：无论是否空白，一律先 `trim()`。trim 后为空 → **不参与筛选**（等价于没传）；trim 后非空 → **以 trim 后的值**做模糊匹配。故搜索 ` abc ` 与搜索 `abc` 结果完全一致，不存在把首尾空格也拿去 LIKE 的中间状态。

### 恒定条件

`status = 1`。草稿（0）与私密（2）文章永远不进前台列表（主规格 §7）。

### 排序

```
ORDER BY isTop DESC, createdAt DESC, articleId DESC
```

末位的 `articleId` 是**分页稳定性**的关键：`createdAt` 会重复（批量导入尤其常见），没有唯一键兜底时，同一条记录可能在翻页时出现两次或被整页跳过。

### 响应

`Result<PageResult<ArticleListVO>>`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 42,
    "page": 1,
    "pageSize": 10,
    "list": [
      {
        "articleId": 7,
        "title": "示例文章",
        "summary": "摘要文本",
        "coverImage": "/images/cover-example.jpg",
        "createdAt": "2026-09-21T14:30:00",
        "viewCount": 128
      }
    ]
  }
}
```

`PageResult` 为既有类型（`com.blog.common.PageResult`），字段顺序 `total` / `page` / `pageSize` / `list`。

**响应里的 `page` / `pageSize` 是钳制生效后的值**：请求 `pageSize=999` 时，响应中 `pageSize` 为 `50`，而不是把请求值原样回显。回显原值会让前端的页码控件按 999 去排页，与实际返回的条数对不上。

### 错误与边界

- 无匹配 → `total: 0`、`list: []`，HTTP 200 + `code: 0`，**不是** 404
- `categoryId` 指向不存在或已逻辑删除的分类 → 空列表，**不报 404**。理由：省一次存在性查询，且前端此时页头标题退化为「分类」、正文显示「暂无文章」，比跳 404 更友好
- `categoryId=abc`（类型不匹配）→ 由既有 `GlobalExceptionHandler` 映射为 `40001`，**不新增错误码**
- **本次不新增任何 `ResultCode`**

> **该待验证点已实测结清（2026-09-21，任务 3）**：`@ModelAttribute` 绑定失败时，本项目当前
> 的 Spring Boot 版本抛的是 `MethodArgumentNotValidException` —— 既有
> `GlobalExceptionHandler.handleValidation` 已映射它。故 **`categoryId` 类型失配实测返回
> `40001`**，未新增任何 handler，`GlobalExceptionHandler.java` 未被改动。
>
> 口径边界（勿写成更强的结论）：以上只是 `categoryId` 一处的实测结果。`page=abc` 等其它参数的
> 类型失配**未单独实测**；若要把结论推广成「所有参数类型失配均为 40001」，那是基于
> 「同 POJO、同绑定器」的外推，须显式标注为外推，或先补一条 `page=abc` 的用例把它变成实测。

## 4. 查询实现

### 分页

`new Page<>(page, pageSize)` + `articleMapper.selectPage(page, wrapper)`。`PaginationInnerInterceptor` 会生成 `LIMIT` 并**自动跑一条 COUNT**，故 `total` 由插件给出，不需要手写第二条 SQL。

### 条件拼装（`ArticleServiceImpl`）

```java
LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
w.eq(Article::getStatus, STATUS_PUBLIC);                                  // 恒定
w.eq(q.getCategoryId() != null, Article::getCategoryId, q.getCategoryId());
w.eq(Boolean.TRUE.equals(q.getRecommended()), Article::getIsRecommended, 1);
w.eq(Boolean.TRUE.equals(q.getTop()),         Article::getIsTop,         1);
w.and(kw != null, x -> x.like(Article::getTitle, kw)
                        .or().like(Article::getSummary, kw));
w.exists(q.getTagId() != null,
         "SELECT 1 FROM articleTag t WHERE t.articleId = article.articleId"
       + " AND t.tagId = {0} AND t.deleted = 0", q.getTagId());
w.orderByDesc(Article::getIsTop)
 .orderByDesc(Article::getCreatedAt)
 .orderByDesc(Article::getArticleId);
```

三个必须讲明白的点：

**1. `tagId` 用 `EXISTS` 子查询，不是 JOIN。** `articleTag` 是多对多：同一条文章挂多个标签时，JOIN 会让它在结果里出现多行，得再加 `DISTINCT`，而 `DISTINCT` 与分页 COUNT 一起用更容易出错。`EXISTS` 天然去重。另一处关键：**`t.deleted = 0` 必须手写**——`@TableLogic` 只保护 MyBatis-Plus 生成的 SQL，手写片段它管不着。这与 `CategoryMapper.selectCategoryWithArticleCount` 注释里已经记下的坑是同一个。参数用 `{0}` 占位符走预编译，**不是字符串拼接**。

**2. `keyword` 必须转义 LIKE 通配符。** 技术博客里搜 `user_name`、`100%`、`C++` 是家常便饭。不转义时 `_` 匹配任意单字符、`%` 匹配任意串——搜一个下划线**几乎命中全站文章**。转义顺序为 `\` → `\\`、`%` → `\%`、`_` → `\_`（必须先转 `\`，否则会把它刚写进去的反斜杠再转一次）。依赖 MySQL 默认转义符 `\` 且 `NO_BACKSLASH_ESCAPES` 未开启，此依赖写进代码注释。

**3. 参数钳制放在 Service，不放在 Controller。** 这样无论谁调用 `ArticleService.list()`（将来的管理端列表、定时任务）都受同一层保护，不会因为绕过 Controller 就漏掉上限。`page < 1 → 1`；`pageSize` 落在 `[1, 50]`，越界钳制；两者为 `null` 时取 `1` / `10`。**`Page<>(...)` 用钳制后的值构造，返回的 `PageResult` 也用钳制后的值**，理由见 §3。

**一处需由冒烟兜住的依赖**：`exists` 子查询里用 `article.articleId` 引用外层表，这依赖 MyBatis-Plus 生成不含别名的 `FROM article`。若将来升级 MP 后它改为生成别名（如 `FROM article AS t1`），该片段会失效——**而这是单测发现不了的**（单测断言的是 wrapper 里的字符串，不是数据库真跑的结果）。由 §7 第二层的 `tagId` 筛选冒烟兜住；升级 MP 后须重跑该层。

### VO 转换

`page.getRecords()` 逐条映射为 `ArticleListVO`。`viewCount` 为 `null` 时按 `0` 处理（DDL 默认 0，但建表前导入的历史数据可能为 NULL）。

**不用 `BeanUtils.copyProperties` 从 `Article` 直接拷**：`Article` 带着 `content`（LONGTEXT）与 `deleted`，拷贝「碰巧对了」但不可读。显式 `new ArticleListVO()` 逐字段赋值，将来谁改了字段一眼看得见。

## 5. 前端页面

### `Category.vue` 三态

沿用 `SidebarCategories.vue` 已确立的模式：**加载中** / **失败（带重试按钮）** / **空态**。不发明新写法。

### 两个必须盯住的坑

1. **必须 `watch` 路由参数，不能只在 `onMounted` 取数。** `/category/1` → `/category/2` 命中的是同一个组件实例，Vue 会复用而不重建，`onMounted` 不会再跑第二次。只在 `onMounted` 里加载的话，点另一个分类页面不会变。写法：`watch([categoryId, page], load, { immediate: true })`。
2. **切分类时页码要归 1。** 从「分类 A 第 3 页」跳到「分类 B」时不重置 `page`，会去请求 B 的第 3 页，很可能直接空列表。

### URL 是页码的唯一真相

点分页器 → `router.push({ query: { page: n } })` → `watch` 触发加载。**刻意不在组件里另存 `currentPage` state**——两份状态迟早不一致。回到第 1 页时把 `page` 从 query 里**删掉**（得到 `/category/3` 而非 `/category/3?page=1`）。`scrollBehavior` 已在路由表里配了 `top: 0`，翻页自动回顶。

### 分类名

复用 `fetchCategoryList()`，按 `categoryId` 找到名字做页头标题。

**此处与文章列表的错误处理刻意不同**：分类名拉取失败时**静默退化**为「分类」二字，不弹错误、不阻塞文章列表渲染。理由：分类名只是标题装饰，为它把整页拖垮不划算。

### `ArticleCard.vue`

props 即 `ArticleListVO` 的六个字段。**整卡包在 `<router-link :to="\`/article/${articleId}\`">` 里**（该页目前仍是占位页，点进去会看到「文章详情（占位）」，属已知且已确认的临时状态）。

- 封面 `coverImage` 为空时套用既有的 `.img-placeholder` 类（主规格 §14 占位约定，不报错）
- 时间显示日期部分：`createdAt.slice(0, 10)`
- 显示 `viewCount`

### 分页控件

`el-pagination`（Element Plus 已在 `main.ts` 全局注册），`layout="prev, pager, next"`，`:total` 取后端 `total`，`:current-page` 绑 URL 里的值，`@current-change` 改 URL。`total` 为 0 时整块隐藏。

**前端固定 `pageSize = 10`**，不提供「每页条数」选择器（与后端默认值一致，省掉一套要同步进 URL 的状态）。

**URL 里的 `page` 要防御性解析**：`?page=` 是用户可手改的。取值规则为「解析为整数，`NaN` 或 `< 1` 一律当 `1`」——即 `?page=abc`、`?page=0`、`?page=-3` 都退化为第 1 页，而不是把非法值发给后端（虽然后端也会钳制，但前端不该依赖后端兜底来保证自己的页码控件不炸）。

## 6. 测试策略

### 后端 · `ArticleServiceImplTest`（Mockito，mock `ArticleMapper`）

主力测试。Service 的全部逻辑就是「拼 wrapper + 转 VO」，故断言对象就是**拼出来的 wrapper 长什么样**：捕获传给 `selectPage` 的 wrapper，读其条件 SQL 与参数映射（MyBatis-Plus 的 `getTargetSql()` / `getParamNameValuePairs()`），逐条核对：

- `status = 1` 恒定存在
- `recommended` / `top`：`true` 加条件、`false` **不加**、`null` 不加
- `tagId`：条件里出现 `EXISTS`，且**含 `t.deleted = 0`**；参数是预编译绑定值而非拼进 SQL 的字符串
- `keyword`：传 `user_name` 时 pattern 里**没有裸 `_`**；传 `100%` 同理；空白串 / 纯空格 → **不加**该条件
- 排序：`isTop DESC, createdAt DESC, articleId DESC` 三段都在且顺序如此
- 分页钳制：`page` 为 `0` / `-1` / `null` → `1`；`pageSize` 为 `0` → `1`、`999` → `50`、`null` → `10`
- VO 转换：`viewCount` 为 `null` → `0`；`content` 与 `deleted` **不出现在 VO 上**

### 后端 · `ArticleControllerTest`（`@WebMvcTest` 切片）

- 响应结构：`code` / `message` / `data`，且 `data` 含 `total` / `page` / `pageSize` / `list`
- 无参调用的默认值（`page=1`、`pageSize=10`）
- `categoryId=abc` → `40001`
- **不带 JWT 也能访问**（`/api/article/**` 不属于 `/api/admin/**`，别被 `AdminAuthInterceptor` 误伤）

### 前端 · `src/__tests__/category-page.test.ts`

挂载 `Category.vue`，用 `createMemoryHistory` 起一个**真 router**（页码住在 URL 里，桩掉 router 等于没测），mock `../api/article` 与 `../api/category`：

- 渲染后端返回的 N 张卡片
- 空结果（`total: 0`）显示空态而非错误态
- 取数失败显示错误态 + 后端 message，点重试可恢复
- 翻页：点第 2 页 → `router.currentRoute.value.query.page === '2'`
- **路由参数变化触发重新取数**（`/category/1` → `/category/2`）——专门守住 §5 那个「只在 `onMounted` 取数」的坑
- 切分类时 `page` 归 1
- 非法 `page`（`?page=abc` / `?page=0`）退化为第 1 页取数（§5 的防御性解析）

### 前端 · `src/__tests__/article-card.test.ts`

- `coverImage` 为空时走占位渲染
- 整卡链到 `/article/:articleId`

### 测试盲区（必须说清楚）

`ArticleServiceImplTest` 是 Mockito 单测，它**证明不了这条 SQL 能在真 MySQL 上跑**：列名、`EXISTS` 语法、`{0}` 占位符是否真的预编译，全都测不到。这正是 `ColumnNamingConventionTest` 注释里那句「单测全绿、连真库才炸」的领域。**故 §7 的冒烟脚本不是可选项。**

## 7. 冒烟脚本与验证边界

### 脚本：`backend/smoke/article-list-smoke.sh`

沿用 `category-smoke.sh` 的体例（`jq` 硬依赖前置检查、逐步骤打印、`set -u`、头部注明前置条件与启动命令）。

**脚本必须分两层，且第二层的结论不能由脚本独断**——因为本次没有任何文章写入口，真库 `article` 表是空的：

**第一层（不需要业务数据，可直接跑）**

- 接口能通、`code = 0`
- 空库时 `total = 0` 且 `list` 是空数组
- `PageResult` 四个字段齐全
- `categoryId=abc` → `40001`
- `pageSize=999` 被钳到 50

这一层真实证明了「**应用起来了 + Mapper 装配完整**」——与 `category-smoke.sh` 头部【证据一】同一个道理：若 `@MapperScan` 被挪回启动类，单测与切片全绿，而真实应用启动时注入 `ArticleMapper` 会直接抛 `NoSuchBeanDefinitionException`。

**第二层（需先手工灌数据）**

`categoryId` / `tagId` / `keyword` / `recommended` / `top` 五个筛选各自命中；排序中置顶在前；翻第 2 页不重复、不遗漏；草稿（`status=0`）与私密（`status=2`）**不出现**。

脚本内附一段带注释的样例 `INSERT`（分类 + 文章 + 标签 + `articleTag` 关联，含一条置顶、一条草稿、一条私密），由用户手工执行后再跑第二层。

### 验证边界（硬约束）

- **实现者负责**：编译、Mockito 单测、MockMvc 切片（均不依赖真实 DB/Redis）、前端 vitest 与 `vue-tsc -b` 类型检查
- **用户负责**：执行 `INSERT` 样例数据、运行冒烟脚本并反馈输出
- **绝不由实现者声称端到端已验证**。冒烟脚本由用户手工执行；在其输出被反馈之前，本次交付的表述只能是「冒烟脚本已就绪，待手工执行」

## 8. 文档更新点

| 文件 | 写什么 |
|------|--------|
| 本文件 | 新增规格 |
| `docs/superpowers/plans/2026-09-21-article-list-slice.md` | 实现计划（由 writing-plans 产出；计划文档是冻结稿，后续不参与回写） |
| `README.md`「## 状态」 | 补「前端第二条真实数据链路」与本次的验证边界 |
| 主规格 §15 | 第 4 / 6 步状态推进；并记一条：§9 的 `blog:article:list:{hash}` 暂缓 |
| `frontend/README.md` | 前端现在有两个后端契约要记，补 `/api/article/list` 与 `PageResult` 结构 |
| `backend/src/main/resources/application.yml` | 改写 `spring.jackson` 注释（§2 连带改动） |

## 9. 对主规格的偏离

**偏离项：主规格 §9 缓存表中的 `blog:article:list:{hash}`（TTL 5min）本次不实现。**

理由：

1. **失效复杂度不划算。** 该键是「参数组合」——`{categoryId}:{page}:{pageSize}` 还要乘上 `tagId` / `keyword` / `recommended` / `top`，组合爆炸。将来文章 CRUD 上线时，每次增删改都要失效所有变体，得靠 `SCAN MATCH` 或额外维护键索引。而 `category` 的 `blog:category:list` 是**单一键**，删一下就完事——两者不是一个量级。
2. **收益低。** 个人博客数据量小，按 `categoryId` 走索引查库本就够快。
3. **会引入可见的不一致。** 列表里的 `viewCount` 来自 MySQL（浏览量定时落库），5 分钟缓存只会让它更旧。

**后续接续**：等管理端 article CRUD 落地、有了真实写入压力与失效时机，再回头评估。届时主规格 §9 仍留有该键的定义，本次不做不等于取消。
