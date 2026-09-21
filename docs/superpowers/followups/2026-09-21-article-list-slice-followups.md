# article 列表切片 · 延后项与撤回项

**来源：** 文章列表竖切（`GET /api/article/list` + `/category/:id` 接线）的最终审查分诊，交付区间 `f0ee861..74db3d6`（13 提交，2026-09-21）。
**用途：** 本切片**明确没做**的事、以及**被判定为误报不必再做**的事。目的是让下一个切片不必重新发现它们，也不必重新争论。

最终审查的判定是「可以合并，0 Critical」，分诊结果是 **判「必须合并前修」= 0 条**。本文件记录的是「可合并后修」与「撤回」两级。

---

## 一、撤回项（已判定为误报或不必修 —— 请不要重新提出）

这些条目在执行期被提出过，最终审查逐条核对后判定为**不值得修**或**判断本身有误**。列在这里是为了防止下一个切片重新发现同一件事、重新争论一遍。

| 曾提出的问题 | 撤回理由 |
|---|---|
| 计划步骤 1 的代码块与交付文件不符 | 计划是冻结稿，其自身条款即「以仓库代码为准」；该块已被消费，无人再读 |
| 断言摸了 MyBatis-Plus 内部结构（`getExpression().getNormal()`） | **该写法正是为绕开恒真陷阱而选的**，不是缺陷（见下方「恒真断言的教训」） |
| `ArticleServiceImpl` 的 EXISTS 注释承诺「由冒烟脚本第二层兜住」 | 承诺**就绪未兑现**，各处表述无过度；等手工执行第二层后才算成立 |
| `@WebMvcTest` 切片是否真注册 `AdminAuthInterceptor` 无法从 diff 核实 | **已在静态层结清**：`WebMvcConfig.java:20-22` 只 `addPathPatterns("/api/admin/**")`，`/api/article/list` 在路径层面不可能被命中 |
| 规格 §3 的 40001 回写口径 | 已逐字满足（只写「`categoryId` 类型失配实测返回 40001」，推广写法显式标注为外推） |
| `SidebarCategories.vue:125` 注释指向已删除的 `.tag-chip` | 已在 `c215b37` 改好 |
| `ArticleCard.vue:5` 前向引用 `api/article.ts` 的 `ArticleListItem` | 已成立：`article.ts` 确实导出该类型名 |
| `createdAt` 为 null 时 `.slice` 抛错 | schema 是 `DATETIME NOT NULL`，后端不可能返回 null/空串 |
| `/category/:id` 卸载时可能发 `?categoryId=NaN` 废请求 | 未实证，且即使发生也只是一次被请求序号守卫丢弃的请求 |
| 错误态「重试」按钮不重试分类名接口 | 规格 §5 规定分类名拉不到即静默降级，这是**规定行为** |
| `el-pagination` 在越界页码时是否自行纠正并回写 URL | 用例不依赖该行为；无论是否自纠正，死胡同都已打通 |
| 序号守卫是「丢弃过期响应」而非「取消请求」 | 省流量需 AbortController，超出裁定范围；行为本身正确 |
| 计划文档与若干报告里的行数/计数笔误 | 过程产物；`.superpowers/` 不被 git 跟踪，不入交付 |
| 样例数据的中文经用户终端落库不受脚本编码契约保护 | 已在脚本头部说明，且断言按 id 限域不受影响 |

## 二、下一个切片建议顺手带上（一行到几行级）

按「同一处改动」分组，每组成本都很低。

**后端测试卫生**（`backend/src/test/java/com/blog/service/impl/ArticleServiceImplTest.java`）

- `:22` 未使用的 `import java.util.Map;`
- 缺 `@AfterAll` 清理：`@BeforeAll` 把 `Article` 的 `TableInfo` 注册进 JVM 全局静态缓存后不移除，与 `ColumnNamingConventionTest.java:94-97` 的既有清理惯例不一致（当前实测无污染）
- `normalizePage` 的「原样透传」档无测试：现有断言喂的全是 `null/0/-3`（都期望 1），把 `return page` 改成 `return DEFAULT_PAGE` 后 **18 条仍全绿**。补一条 `query(5, 10) → page == 5` 即可
- 断言强度：`lastWrapper()` 的 `verify(articleMapper)` 被从 `times(1)` 放宽为 `atLeastOnce()`，逃逸了「`list()` 一次请求调两次 `selectPage`」。**注意损失比看上去窄** —— 该不变量仍被未改动的 `passesClampedValuesToMapper`（`:272-281`，用默认 `times(1)`）钉住。精确修法：`captureWrapperFor` 里加 `clearInvocations(articleMapper)` 并还原 `times(1)`
- 覆盖缺口：`whenFalse`/`whenNull` 两档只有负向断言、`keyword == null` 无显式用例、无「`content` 不在 keyword 匹配范围」用例、无 `tagId` + `keyword` 组合用例
- `ArticleControllerTest`：绑定失败用例未断言 Service 未被调用（补 `verify(articleService, never()).list(any())`）

**前端测试与行为**（`frontend/`）

- `ArticleCard` 无用例钉住「整卡可点击」（补 `expect(wrapper.element.tagName).toBe('A')`）
- `Category.vue` 的 `finally` loading 守卫**仓库内无用例覆盖**（复审者用临时探针证实过行为正确，探针未入库）→ 这正是请求序号守卫的核心机制之一，补一条「陈旧请求先返回时仍显示加载中」很有价值
- **`Category.vue:92` 的 `watch(categoryId)` 没有路由名守卫**，而它读的是全局当前路由的 `route.query.page`。**下一个切片就是 `/tag/:id`，很可能同样用 `?page=`** —— 届时这是真实的串扰面（从分类页跳到那里会在卸载竞态里把对方的 `page` 参数抹掉）。修法：加 `route.name === 'category'` 一行
- `Category.vue` 用 `router.push` 而非 `replace` 清/写 query：从带 `page` 的 URL 切分类会多压一条历史记录，用户按一次「后退」看起来没反应。**（本切片裁定 I-2 时明确选了不重排 watcher，故该项随之延后）**
- `Category.vue:14` 与 `:21` 不对称：`page` 做了防御性解析而 `categoryId` 是裸 `Number(route.params.id)`，手输 `/category/abc` 会发 `categoryId=NaN` → 显示「加载失败…」而非空态（UI 内不可达，地址栏可达）
- `Category.vue` 每次翻页都连带重发一次 `fetchCategoryList`（分类名只当标题装饰，与页码无关）—— 白跑一次被 Redis 缓存的请求

**冒烟脚本便利性**（`backend/smoke/article-list-smoke.sh`）

- 无「后端是否可达」预检：后端没起时十几条断言全红、每条打印空的「原始响应：」，是首次运行最可能的挫败点。加一条 `curl -s -o /dev/null -w '%{http_code}'` 即可一眼定位
- 第二层跑了但库里同时有用户真实文章时，仍会有若干条**按设计**变红（头部前置条件 5 已写明是环境非缺陷）；这是固有性质，若想彻底消除需给那几条断言加 `categoryId` 限域

**措辞与观感（低优先）**

- `article-list-smoke.sh:192` 的 `.data.list | length` 只证「长度 0」，对 `list: null` 不敏感（`jq 'length'` 对 `null` 返回 0）；`null` 场景由 `:168` 的 `type == "array"` 独立把关，实际不可利用
- 切片设计文档 `:89` 参数表仍写 `page | 默认 1，最小 1，越界钳制`，与同文档 `:138` 新写的「上界不钳」略有字面张力（可读作只指下界，日后顺手收紧）
- `Category.vue` 越界页上「共 N 篇」与「暂无文章」同屏时，措辞严格说应作「本页暂无文章」
- `.category-retry` 亮色下是 `#5b9cf5` 字压白底（12px 约 2.8:1，低于 WCAG AA）；它与既有的 `.sidebar-retry` **逐字同款**，属仓库既有风格而非本切片回归（暗色下约 6:1，合规）。建议与 `.sidebar-retry` 一起改色
- `article-list-smoke.sh` 的 `SECOND_LAYER_TOTAL=14` 是硬编码，会随第二层断言增删漂移（只影响「跳过」时报告的数字，不影响任何断言判断）

## 三、需要独立决策的事（不在「顺手」范畴）

- **Element Plus 的暗色适配**：`frontend/src/main.ts` 只 import 了 `element-plus/dist/index.css`，没有 import `element-plus/theme-chalk/dark/css-vars.css`，所以暗色下 `el-pagination` 是**亮色控件**（本切片是前台第一个把大块 EP 组件放上暗色页的地方）。
  **原因只有这一条** —— 注意不要记成「`:root.dark` 不命中 EP 的 `html.dark`」：两者**是同一个选择器**（`theme.ts` 给 `document.documentElement` 加类就等于 `<html class="dark">`）。
  **使用者 2026-09-21 裁定：并入一个专门的暗色主题切片**，与下面那条已知缺陷一起做、一次验证（修法 1 行，但会改动全站所有 EP 组件在暗色下的观感）。

## 四、已在本切片内修掉的（不必再提）

最终审查挖出的 7 条「假绿」已在 `74db3d6` 一次修完并通过定向复审（FR-1..FR-7 全部 ADDRESSED）：

1. 第二层因样例数据未灌而整体跳过时脚本仍 `exit 0`、汇总还打「失败 0 项」→ 现在跳过显形、退出码三档（失败→1、仅跳过→2、全过→0）
2. 「不得落到 50000」在空响应体时假通过（`jq -r '.code'` 得空串，`"" != "50000"` 恒真）→ 已删，检出能力由「返回 40001」那条等价承担
3. 两条转义断言零区分度（删掉整个 `escapeLike` 仍全绿）+ 一句与样例数据不符的注释 → 改用裸下划线 `keyword=%5F&categoryId=$SMOKE_CID`（转义→1 篇、未转义→3 篇），并删掉错误注释
4. 缺规格 §7 第一层明列的「`list` 是空数组」→ 已补（挂在 `categoryId=999999` 上，因为灌入样例数据后无参调用必然非空，挂那里会造成与库内容有关的假红）
5. 文档口径不一致（`frontend/README.md` 与切片设计 §3 仍写「响应里的 `page`/`pageSize` 是钳制生效后的值」）→ 已限定为 `pageSize`
6. 样例 INSERT 非幂等、显式 id 顶高 AUTO_INCREMENT 未提醒 → 已写进头部前置条件 6 与文末 SQL 块
7. 汇总行被 60 行 SQL 块淹没 → 失败与跳过各补结论行

## 五、恒真断言的教训（给下一个切片的方法论）

本切片执行期一共撞见**三例**「断言不到东西」的测试/脚本断言，其中前两例在起飞前排查中被发现：

| 形态 | 位置 | 病症 |
|---|---|---|
| **永远为假** | 冒烟脚本 `.data.list[0].isTop` | 断言了一个 `ArticleListVO` 根本没有的字段，永远红 |
| **永远为真** | 计划里 `contains("(")` | MP 无条件给条件列表加括号，恒真；后改为「取 OR 所在的括号分组并断言其中不含 `status`」，才真正能抓住「漏 `and()` 导致 `status=1` 被 OR 绕过」这个泄漏 |
| **零区分度** | 冒烟脚本两条转义断言 | 样例数据里没有「转义与不转义结果不同」的行，删掉被断言的功能仍全绿 |

**共同点：靠读断言的名字和文字判断不出它是否有效。** 前两例是我用一次专门的起飞前冲突扫描抓到的，第三例漏网了——直到最终审查才发现。有效手段是**变异验证**（把被断言的那段逻辑破坏掉，看它是否真的变红），本切片有三次由此拦下假绿：任务 5 的请求序号守卫、分页器空态分支、以及 FR-3 的转义断言。

**另一个同源教训：断言必须与库内容解耦，或者把前提写在最显眼处。** 冒烟脚本第二层有若干断言（`recommended=true`→1、`top=true`→1、`keyword=user_name`→1、`keyword=100%25`→1）只在「库中 `article` 表除样例数据外没有别的文章」时成立；使用者将来写了真实文章再跑，它们会变红。现在脚本头部第 5 条前置条件点名了这批断言与失败解读，但**根本解法是加 `categoryId` 限域**（当时因断言数值被规格钉死而未做）。

---

## 本切片的验证边界（明确没做什么）

- **冒烟脚本从未被执行过。** `backend/smoke/article-list-smoke.sh` 的所有断言（含本文件提到的那些）**只有使用者手工灌入样例数据并真正运行第二层之后才算成立**。第一层同样未跑过。
- **`/category/:id` 未在浏览器中实测。** 分页、URL 页码、三态、暗色观感均待人工验证。
- **开发侧**已验证的：后端编译与 Mockito 单测（96 项）、MockMvc 切片、前端 vitest（6 文件 32 用例）、`vue-tsc` 类型检查、脚本 `bash -n` 语法检查。
- **运行环境本身是通的**（2026-09-21 确认）：后端在 IDE 里跑起来过（`MyBlogApplication`）、前端 `npm run dev` 跑过、本机 MySQL 8 与 Redis 在运行且库表已建、库里有数据。
  所以「启动应用 / 连库 / 真实缓存读写」这三项**不是**欠账 —— 它们早被同一条 category 竖切链路证明过（`blog:category:list` 的失效断言就是真实的缓存读写）。
  > 本文件第一版曾把这三项列为「未验证」，那是错的：把「仓库里缺什么」当成了「没人做过什么」。2026-09-21 更正。
- **于是本切片真正欠的只有上面前两件**（冒烟未跑、分类页未在浏览器打开）。完整清单与还债步骤见 [`docs/ROADMAP.md`](../../ROADMAP.md) §5。
