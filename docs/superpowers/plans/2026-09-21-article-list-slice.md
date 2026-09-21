# 文章列表接口 + 分类页接线（article list slice）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 落地 `GET /api/article/list`（主规格 §8 的完整筛选面 + 分页），并让前端 `/category/:id` 从占位页变成真实可用页面——这是前端第二条真实数据链路。

**架构：** 后端新增 5 个类（Controller / Service / ServiceImpl / ArticleQueryDTO / ArticleListVO），**零修改既有类**，不加手写 SQL——分页由既已注册的 `PaginationInnerInterceptor` 承担，`tagId` 用 wrapper 的 `EXISTS` 子查询表达。前端新增 `api/article.ts`，从 `Home.vue` 抽出共用的 `ArticleCard.vue`，重写 `Category.vue`，页码住在 URL 里。

**技术栈：** Spring Boot 3.4.3、Java 21、MyBatis-Plus 3.5.9、JUnit5 + Mockito + MockMvc；Vue 3.5 + TypeScript + Element Plus + Vitest（jsdom）+ @vue/test-utils。

**设计依据：** [`docs/superpowers/specs/2026-09-21-article-list-slice-design.md`](../specs/2026-09-21-article-list-slice-design.md)（下文简称「规格」）

---

## ⚠️ 实现前必读：环境约束与验证边界

本机 `JAVA_HOME` 指向 **JDK 1.8**（`E:\works\java8\jdk`），Spring Boot 3 需要 17+。**绝对不要修改本机 `JAVA_HOME` 或系统环境变量**，只在每条命令内联覆盖。

**本计划中所有 Maven 命令都必须使用如下自包含前缀**（单行内联，不依赖 shell 状态，因为每次 Bash 调用环境不保留）：

```bash
JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn <goal>
```

前端在 `frontend/` 下执行 `npm run test`（vitest，单跑一次）与 `npx vue-tsc -b`。

### 验证边界（硬约束，不得越界）

实现者**无法访问 MySQL / Redis 凭证**：

- **能验证**：编译、Mockito 单测、MockMvc 切片测试、前端 vitest、`vue-tsc -b`（都不需要真实 DB / Redis）
- **不能验证**：启动应用、连库、真实缓存读写
- **不要尝试**启动 `spring-boot:run`，不要写任何连真实数据库的测试。做了必失败
- **绝不要声称端到端已验证。** 冒烟脚本（任务 6）由用户手工执行；在用户反馈其输出之前，本次交付的表述只能是「冒烟脚本已就绪，待手工执行」

**重要事实：本次没有任何文章写入口**（管理端 article CRUD 不在范围内），所以真库 `article` 表是空的。任务 6 的冒烟脚本因此分两层，第二层需要用户先手工灌入样例数据。

### 本计划的冻结性质

本计划是**执行前的冻结稿**。执行期若发生人类裁定推翻了下面某个代码块，**以仓库里的实际代码为准**，不要照本计划的代码块去「修正」生产代码。

---

## 文件结构

### 后端（新增，均在 `backend/src/main/java/com/blog/`）

| 文件 | 职责 |
|---|---|
| `dto/ArticleQueryDTO.java` | 7 个查询参数的载体。**不含钳制逻辑**（钳制在 Service，见规格 §4） |
| `dto/ArticleListVO.java` | 卡片字段：`articleId` / `title` / `summary` / `coverImage` / `createdAt` / `viewCount`。**刻意不含 `content` 与 `deleted`** |
| `service/ArticleService.java` | 接口：`PageResult<ArticleListVO> list(ArticleQueryDTO)` |
| `service/impl/ArticleServiceImpl.java` | 参数钳制、拼 wrapper、调 `selectPage`、转 VO。**本切片唯一的业务逻辑所在** |
| `controller/ArticleController.java` | 参数接收与转发，无业务逻辑 |

`ArticleMapper` **零改动**——`BaseMapper.selectPage` 已够用。

### 后端测试（`backend/src/test/java/com/blog/`）

| 文件 | 测什么 |
|---|---|
| `service/impl/ArticleServiceImplTest.java` | 拼出来的 wrapper 长什么样 + 钳制 + VO 转换 |
| `controller/ArticleControllerTest.java` | 接口契约、参数绑定、错误码、无需 JWT |

### 前端（`frontend/src/`）

| 文件 | 变更 | 职责 |
|---|---|---|
| `api/article.ts` | 新增 | `fetchArticleList()` + `ArticleListItem` / `ArticleQuery` / `PageResult` 类型 |
| `components/ArticleCard.vue` | 新增 | 文章卡片，首页与分类页共用 |
| `views/Category.vue` | 重写 | 分类页（原为 6 行占位） |
| `views/Home.vue` | 修改 | 改用 `<ArticleCard>`，骨架数据与其余布局不动 |

### 前端测试（`frontend/src/__tests__/`）

| 文件 | 测什么 |
|---|---|
| `article-card.test.ts` | 链接、字段渲染、封面占位 |
| `category-page.test.ts` | 三态、列表渲染、翻页改 URL、路由参数变化重新取数 |

### 其它

| 文件 | 变更 |
|---|---|
| `backend/smoke/article-list-smoke.sh` | 新增，两层结构 |
| `README.md` | 「## 状态」更新 |
| `docs/superpowers/specs/2026-09-17-myblog-design.md` | §15 状态推进 + §9 偏离注记 |
| `frontend/README.md` | 补 `/api/article/list` 契约与 `PageResult` 结构 |
| `backend/src/main/resources/application.yml` | 改写 `spring.jackson` 注释 |

---

## 任务 1：Service 基础——恒定条件、排序、分页钳制、VO 转换

**文件：**
- 创建：`backend/src/main/java/com/blog/dto/ArticleQueryDTO.java`
- 创建：`backend/src/main/java/com/blog/dto/ArticleListVO.java`
- 创建：`backend/src/main/java/com/blog/service/ArticleService.java`
- 创建：`backend/src/main/java/com/blog/service/impl/ArticleServiceImpl.java`
- 测试：`backend/src/test/java/com/blog/service/impl/ArticleServiceImplTest.java`

本任务只做「恒定 `status=1` + 三段排序 + 分页钳制 + VO 转换」，五个筛选参数留到任务 2。

- [ ] **步骤 1：编写失败的测试**

创建 `backend/src/test/java/com/blog/service/impl/ArticleServiceImplTest.java`：

```java
package com.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.PageResult;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.entity.Article;
import com.blog.mapper.ArticleMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTest {

    @Mock
    private ArticleMapper articleMapper;

    private ArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ArticleServiceImpl(articleMapper);
    }

    /** 空查询：所有筛选参数为 null，只验证本任务负责的部分。 */
    private ArticleQueryDTO emptyQuery() {
        return new ArticleQueryDTO();
    }

    private ArticleQueryDTO query(Integer page, Integer pageSize) {
        ArticleQueryDTO q = new ArticleQueryDTO();
        q.setPage(page);
        q.setPageSize(pageSize);
        return q;
    }

    private Article article(long id, String title, Integer viewCount) {
        Article a = new Article();
        a.setArticleId(id);
        a.setTitle(title);
        a.setSummary("摘要 " + id);
        a.setCoverImage("/images/cover-" + id + ".jpg");
        a.setCreatedAt(LocalDateTime.of(2026, 9, 21, 14, 30));
        a.setViewCount(viewCount);
        return a;
    }

    /** 打桩 selectPage，并返回捕获到的 wrapper。 */
    @SuppressWarnings("unchecked")
    private AbstractWrapper<Article, ?, ?> captureWrapper(List<Article> records, long total) {
        given(articleMapper.selectPage(any(), any())).willAnswer(invocation -> {
            Page<Article> p = invocation.getArgument(0);
            p.setRecords(records);
            p.setTotal(total);
            return p;
        });

        service.list(emptyQuery());

        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleMapper).selectPage(any(), captor.capture());
        return (AbstractWrapper<Article, ?, ?>) captor.getValue();
    }

    // ---------- 恒定条件与排序 ----------

    @Test
    @DisplayName("列表：恒定只查 status=1（草稿与私密永不进前台列表）")
    void listAlwaysFiltersPublicStatus() {
        AbstractWrapper<Article, ?, ?> wrapper = captureWrapper(List.of(), 0);

        String sql = wrapper.getTargetSql();
        assertThat(sql).as("实际条件: %s", sql).contains("status");
        assertThat(wrapper.getParamNameValuePairs().values())
                .as("参数: %s", wrapper.getParamNameValuePairs())
                .contains(1);
    }

    @Test
    @DisplayName("列表：排序为 isTop DESC, createdAt DESC, articleId DESC")
    void listOrdersByTopThenCreatedAtThenId() {
        AbstractWrapper<Article, ?, ?> wrapper = captureWrapper(List.of(), 0);

        // 末位的 articleId 是分页稳定性关键：createdAt 会重复，没有唯一键兜底时
        // 同一条记录可能在翻页时出现两次或被整页跳过（规格 §3）。
        String sql = wrapper.getTargetSql();
        assertThat(sql).as("实际 SQL: %s", sql)
                .contains("isTop")
                .contains("createdAt")
                .contains("articleId");

        int top = sql.indexOf("isTop");
        int created = sql.indexOf("createdAt");
        int id = sql.indexOf("articleId");
        assertThat(top).as("isTop 必须排在 createdAt 之前: %s", sql).isLessThan(created);
        assertThat(created).as("createdAt 必须排在 articleId 之前: %s", sql).isLessThan(id);
        assertThat(sql.toUpperCase()).as("三段都必须是 DESC: %s", sql).doesNotContain(" ASC");
    }

    // ---------- 分页钳制 ----------

    @Test
    @DisplayName("分页钳制：page 为 null / 0 / 负数一律当 1")
    void normalizesPage() {
        for (Integer raw : new Integer[] {null, 0, -3}) {
            given(articleMapper.selectPage(any(), any())).willAnswer(invocation -> {
                Page<Article> p = invocation.getArgument(0);
                p.setRecords(List.of());
                p.setTotal(0);
                return p;
            });

            PageResult<ArticleListVO> result = service.list(query(raw, 10));

            assertThat(result.getPage()).as("page=%s 应钳制为 1", raw).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("分页钳制：pageSize 为 null 取 10，为 0/负数取 1，超过 50 取 50")
    void normalizesPageSize() {
        Object[][] cases = {
                {null, 10},
                {0, 1},
                {-5, 1},
                {999, 50},
                {10, 10},
        };
        for (Object[] c : cases) {
            Integer raw = (Integer) c[0];
            long expected = (Integer) c[1];

            given(articleMapper.selectPage(any(), any())).willAnswer(invocation -> {
                Page<Article> p = invocation.getArgument(0);
                p.setRecords(List.of());
                p.setTotal(0);
                return p;
            });

            PageResult<ArticleListVO> result = service.list(query(1, raw));

            assertThat(result.getPageSize()).as("pageSize=%s 应钳制为 %s", raw, expected).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("分页钳制：传给 selectPage 的是钳制后的值（不是请求值）")
    void passesClampedValuesToMapper() {
        given(articleMapper.selectPage(any(), any())).willAnswer(invocation -> {
            Page<Article> p = invocation.getArgument(0);
            p.setRecords(List.of());
            p.setTotal(0);
            return p;
        });

        service.list(query(0, 999));

        ArgumentCaptor<IPage<Article>> captor = ArgumentCaptor.forClass(IPage.class);
        verify(articleMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("分页钳制：回显的是钳制后的值，不是请求值")
    void echoesClampedValues() {
        // 规格 §3：回显原值会让前端页码控件按请求值排页，与实际返回条数对不上
        given(articleMapper.selectPage(any(), any())).willAnswer(invocation -> {
            Page<Article> p = invocation.getArgument(0);
            p.setRecords(List.of());
            p.setTotal(0);
            return p;
        });

        PageResult<ArticleListVO> result = service.list(query(0, 999));

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(50);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
    }

    // ---------- VO 转换 ----------

    @Test
    @DisplayName("VO 转换：viewCount 为 null 时按 0 处理")
    void mapsNullViewCountToZero() {
        Article a = article(7L, "空浏览量", null);
        given(articleMapper.selectPage(any(), any())).willAnswer(invocation -> {
            Page<Article> p = invocation.getArgument(0);
            p.setRecords(List.of(a));
            p.setTotal(1);
            return p;
        });

        PageResult<ArticleListVO> result = service.list(query(1, 10));

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getViewCount()).isZero();
    }
}
```

> 步骤 1 只求「文件能编译、测试能跑起来并失败」。`mapsArticleToVoExplicitly`（逐字段映射）**故意留到步骤 3 再写**——因为它的断言需要 `ArticleListVO` 真实存在，放在步骤 1 只能先写个假实现，而假实现会编译通过并**假绿**，比编译失败更危险。

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=ArticleServiceImplTest
```

预期：**编译失败**，报 `ArticleServiceImpl` / `ArticleQueryDTO` / `ArticleListVO` 找不到符号。

- [ ] **步骤 3：编写最少实现代码**

创建 `backend/src/main/java/com/blog/dto/ArticleQueryDTO.java`：

```java
package com.blog.dto;

import lombok.Data;

/**
 * 前台文章列表查询参数，全部可选。
 * 分页钳制刻意不在这里做 —— 放在 Service 层，这样绕过 Controller 的调用方（将来的
 * 管理端列表、定时任务）也受同一层保护（设计规格 §4）。
 */
@Data
public class ArticleQueryDTO {

    private Long categoryId;
    private Long tagId;
    private String keyword;
    private Boolean recommended;
    private Boolean top;
    private Integer page;
    private Integer pageSize;
}
```

创建 `backend/src/main/java/com/blog/dto/ArticleListVO.java`：

```java
package com.blog.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文章列表项（卡片字段）。
 * 刻意不含 content（LONGTEXT）与 deleted —— 列表用不着，带上只是徒增载荷与泄漏面。
 * createdAt 由 Jackson 按 Spring Boot 默认的 ISO-8601 序列化（如 2026-09-21T14:30:00）：
 * 这是 JS 各引擎都能正确解析的格式，而 "yyyy-MM-dd HH:mm:ss" 在 Safari 上会得到
 * Invalid Date（设计规格 §2 有完整理由，别改成 @JsonFormat）。
 */
@Data
public class ArticleListVO {

    private Long articleId;
    private String title;
    private String summary;
    private String coverImage;
    private LocalDateTime createdAt;
    private Integer viewCount;
}
```

创建 `backend/src/main/java/com/blog/service/ArticleService.java`：

```java
package com.blog.service;

import com.blog.common.PageResult;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;

public interface ArticleService {

    /**
     * 前台公开文章分页列表。
     * 契约见 docs/superpowers/specs/2026-09-21-article-list-slice-design.md §3。
     */
    PageResult<ArticleListVO> list(ArticleQueryDTO query);
}
```

创建 `backend/src/main/java/com/blog/service/impl/ArticleServiceImpl.java`：

```java
package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.PageResult;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.entity.Article;
import com.blog.mapper.ArticleMapper;
import com.blog.service.ArticleService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ArticleServiceImpl implements ArticleService {

    /** 公开状态。前台列表恒定只出这一种（设计规格 §3）。 */
    static final int STATUS_PUBLIC = 1;

    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 10;
    /** 每页条数上限，防止 pageSize=999999 拖垮库（设计规格 §3）。 */
    static final int MAX_PAGE_SIZE = 50;

    private final ArticleMapper articleMapper;

    public ArticleServiceImpl(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @Override
    public PageResult<ArticleListVO> list(ArticleQueryDTO query) {
        int pageNum = normalizePage(query.getPage());
        int pageSize = normalizePageSize(query.getPageSize());

        LambdaQueryWrapper<Article> wrapper = Wrappers.lambdaQuery();
        // 恒定条件：草稿（0）与私密（2）永不进前台列表（主规格 §7）
        wrapper.eq(Article::getStatus, STATUS_PUBLIC);
        buildFilters(wrapper, query);
        // 末位的 articleId 是分页稳定性关键：createdAt 会重复（批量导入尤其常见），
        // 没有唯一键兜底时，同一条记录可能在翻页时出现两次或被整页跳过（设计规格 §3）。
        wrapper.orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getCreatedAt)
                .orderByDesc(Article::getArticleId);

        IPage<Article> result = articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 回显钳制后的值，不是请求值：回显原值会让前端页码控件按请求值排页，
        // 与实际返回的条数对不上（设计规格 §3）。
        return PageResult.of(result.getTotal(), pageNum, pageSize, toVoList(result.getRecords()));
    }

    /** 五个筛选参数。任务 2 实现。 */
    private void buildFilters(LambdaQueryWrapper<Article> wrapper, ArticleQueryDTO query) {
        // 任务 2 填充
    }

    private List<ArticleListVO> toVoList(List<Article> records) {
        return records.stream().map(this::toVo).toList();
    }

    /**
     * 显式逐字段赋值，不用 BeanUtils.copyProperties —— Article 带着 content(LONGTEXT)
     * 与 deleted，直接拷是「碰巧对了」但不可读；显式赋值后谁改了字段一眼看得见（设计规格 §4）。
     */
    private ArticleListVO toVo(Article article) {
        ArticleListVO vo = new ArticleListVO();
        vo.setArticleId(article.getArticleId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setCoverImage(article.getCoverImage());
        vo.setCreatedAt(article.getCreatedAt());
        // 建表前导入的历史数据可能为 NULL，不能直接拆箱
        vo.setViewCount(article.getViewCount() == null ? 0 : article.getViewCount());
        return vo;
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    /** null → 10；其余一律钳进 [1, 50]。注意 0 得到的是 1 而不是默认值 10（设计规格 §3）。 */
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }
}
```

现在在 `ArticleServiceImplTest.java` 的 `// ---------- VO 转换 ----------` 标记之前**追加**逐字段映射用例：

```java
    @Test
    @DisplayName("VO 转换：逐字段映射，且不带上 content 与 deleted")
    void mapsArticleToVoExplicitly() {
        Article a = article(7L, "示例文章", 128);
        a.setContent("正文很长很长");   // 不该出现在 VO 上
        a.setDeleted(0);                // 同上
        given(articleMapper.selectPage(any(), any())).willAnswer(invocation -> {
            Page<Article> p = invocation.getArgument(0);
            p.setRecords(List.of(a));
            p.setTotal(1);
            return p;
        });

        PageResult<ArticleListVO> result = service.list(query(1, 10));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        ArticleListVO vo = result.getList().get(0);
        assertThat(vo.getArticleId()).isEqualTo(7L);
        assertThat(vo.getTitle()).isEqualTo("示例文章");
        assertThat(vo.getSummary()).isEqualTo("摘要 7");
        assertThat(vo.getCoverImage()).isEqualTo("/images/cover-7.jpg");
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 14, 30));
        assertThat(vo.getViewCount()).isEqualTo(128);
    }
```

> `ArticleListVO` 上**没有** `content` / `deleted` 字段，所以「不出现在 VO 上」由类型系统保证——上面把 `content` 与 `deleted` 设进 `Article` 后仍能通过编译与断言，就是这个保证的体现。**不要**给 `ArticleListVO` 加这两个字段。

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
cd backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=ArticleServiceImplTest
```

预期：**PASS**，全部用例绿。

若 `排序` 用例红在 `doesNotContain(" ASC")`：说明 `getTargetSql()` 里出现了大写 `ASC`。MyBatis-Plus 的 `orderByDesc` 生成 `ORDER BY ... DESC`，正常不会有 `ASC`；若真有，先打印实际 SQL 再判断，别直接删断言。

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/blog/dto/ArticleQueryDTO.java \
        backend/src/main/java/com/blog/dto/ArticleListVO.java \
        backend/src/main/java/com/blog/service/ArticleService.java \
        backend/src/main/java/com/blog/service/impl/ArticleServiceImpl.java \
        backend/src/test/java/com/blog/service/impl/ArticleServiceImplTest.java
git commit -m "feat(backend): article 列表 Service 骨架——恒定 status=1、三段排序、分页钳制"
```

---

## 任务 2：五个筛选参数

**文件：**
- 修改：`backend/src/main/java/com/blog/service/impl/ArticleServiceImpl.java`（`buildFilters` 方法）
- 测试：`backend/src/test/java/com/blog/service/impl/ArticleServiceImplTest.java`（追加用例）

- [ ] **步骤 1：编写失败的测试**

在 `ArticleServiceImplTest.java` 的 `// ---------- VO 转换 ----------` 之前插入以下内容：

```java
    // ---------- 五个筛选参数 ----------

    private ArticleQueryDTO filterQuery(java.util.function.Consumer<ArticleQueryDTO> customizer) {
        ArticleQueryDTO q = new ArticleQueryDTO();
        customizer.accept(q);
        return q;
    }

    private AbstractWrapper<Article, ?, ?> captureWrapperFor(ArticleQueryDTO q) {
        given(articleMapper.selectPage(any(), any())).willAnswer(invocation -> {
            Page<Article> p = invocation.getArgument(0);
            p.setRecords(List.of());
            p.setTotal(0);
            return p;
        });

        service.list(q);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleMapper).selectPage(any(), captor.capture());
        return (AbstractWrapper<Article, ?, ?>) captor.getValue();
    }

    @Test
    @DisplayName("筛选：categoryId 传入时加入条件，参数值正确")
    void filtersByCategoryId() {
        AbstractWrapper<Article, ?, ?> wrapper =
                captureWrapperFor(filterQuery(q -> q.setCategoryId(3L)));

        assertThat(wrapper.getTargetSql()).contains("categoryId");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(3L);
    }

    @Test
    @DisplayName("筛选：categoryId 为 null 时不加该条件")
    void skipsCategoryIdWhenNull() {
        AbstractWrapper<Article, ?, ?> wrapper = captureWrapperFor(emptyQuery());

        assertThat(wrapper.getTargetSql()).doesNotContain("categoryId");
    }

    @Test
    @DisplayName("筛选：recommended/top 只在 true 时加条件——false 与 null 都是不筛选")
    void recommendsAndTopsOnlyWhenTrue() {
        // 这两个布尔是「加上这个约束」的开关，false 不是「筛选出非推荐的」。
        // 写成 eq(recommended != null, ...) 会把 false 当成一个真实条件（规格 §3）。
        AbstractWrapper<Article, ?, ?> onlyTrue = captureWrapperFor(filterQuery(q -> {
            q.setRecommended(true);
            q.setTop(true);
        }));
        assertThat(onlyTrue.getTargetSql()).contains("isRecommended").contains("isTop");

        AbstractWrapper<Article, ?, ?> whenFalse = captureWrapperFor(filterQuery(q -> {
            q.setRecommended(false);
            q.setTop(false);
        }));
        assertThat(whenFalse.getTargetSql())
                .as("false 必须等同于不筛选，实际 SQL: %s", whenFalse.getTargetSql())
                .doesNotContain("isRecommended")
                .doesNotContain("isTop");

        AbstractWrapper<Article, ?, ?> whenNull = captureWrapperFor(emptyQuery());
        assertThat(whenNull.getTargetSql())
                .doesNotContain("isRecommended")
                .doesNotContain("isTop");
    }

    @Test
    @DisplayName("筛选：keyword 用括号包住 title OR summary，避免 OR 逃逸污染其他条件")
    void keywordIsNestedInParentheses() {
        AbstractWrapper<Article, ?, ?> wrapper =
                captureWrapperFor(filterQuery(q -> q.setKeyword("spring")));

        String sql = wrapper.getTargetSql();
        assertThat(sql).contains("title").contains("summary").contains("OR");
        // 没有括号时，仅靠 OR 会把 status=1 这个恒定条件一并绕过，草稿与私密文章会泄漏进列表
        assertThat(sql).as("keyword 的 OR 必须被括号包住，实际 SQL: %s", sql).contains("(");
        assertThat(wrapper.getParamNameValuePairs().values())
                .as("参数: %s", wrapper.getParamNameValuePairs())
                .contains("%spring%");
    }

    @Test
    @DisplayName("筛选：keyword 的空白串与纯空格都视为不传")
    void skipsBlankKeyword() {
        assertThat(captureWrapperFor(filterQuery(q -> q.setKeyword(""))).getTargetSql())
                .doesNotContain("title");
        assertThat(captureWrapperFor(filterQuery(q -> q.setKeyword("   "))).getTargetSql())
                .doesNotContain("title");
    }

    @Test
    @DisplayName("筛选：keyword 前后空白被 trim 掉，不参与 LIKE")
    void trimsKeyword() {
        AbstractWrapper<Article, ?, ?> wrapper =
                captureWrapperFor(filterQuery(q -> q.setKeyword("  spring  ")));

        assertThat(wrapper.getParamNameValuePairs().values())
                .as("参数: %s", wrapper.getParamNameValuePairs())
                .contains("%spring%")
                .doesNotContain("%  spring  %");
    }

    @Test
    @DisplayName("筛选：keyword 里的 LIKE 通配符被转义（搜下划线不能命中全站）")
    void escapesLikeWildcardsInKeyword() {
        // 技术博客里搜 user_name / 100% / C++ 是家常便饭。不转义时 _ 匹配任意单字符、
        // % 匹配任意串，搜一个下划线几乎命中全站文章（规格 §4）。
        AbstractWrapper<Article, ?, ?> underscore =
                captureWrapperFor(filterQuery(q -> q.setKeyword("user_name")));
        assertThat(underscore.getParamNameValuePairs().values())
                .as("参数: %s", underscore.getParamNameValuePairs())
                .contains("%user\\_name%");

        AbstractWrapper<Article, ?, ?> percent =
                captureWrapperFor(filterQuery(q -> q.setKeyword("100%")));
        assertThat(percent.getParamNameValuePairs().values())
                .as("参数: %s", percent.getParamNameValuePairs())
                .contains("%100\\%%");
    }

    @Test
    @DisplayName("筛选：keyword 里的反斜杠先于通配符被转义（不能把自己刚写的转义符再转一次）")
    void escapesBackslashBeforeWildcards() {
        assertThat(ArticleServiceImpl.escapeLike("a\\b")).isEqualTo("a\\\\b");
        assertThat(ArticleServiceImpl.escapeLike("_")).isEqualTo("\\_");
        assertThat(ArticleServiceImpl.escapeLike("%")).isEqualTo("\\%");
        // 顺序错的典型症状：先把 % 转成 \%，再把 \ 转成 \\，结果变成 \\%，转义失效
        assertThat(ArticleServiceImpl.escapeLike("a%b")).isEqualTo("a\\%b");
    }

    @Test
    @DisplayName("筛选：tagId 用 EXISTS 子查询，且必须显式带 t.deleted = 0")
    void filtersByTagIdViaExistsWithDeletedGuard() {
        AbstractWrapper<Article, ?, ?> wrapper =
                captureWrapperFor(filterQuery(q -> q.setTagId(9L)));

        String sql = wrapper.getTargetSql();
        assertThat(sql).as("实际 SQL: %s", sql).contains("EXISTS");
        assertThat(sql).contains("articleTag");
        // @TableLogic 只保护 MyBatis-Plus 生成的 SQL，手写片段它管不着 ——
        // 与 CategoryMapper.selectCategoryWithArticleCount 是同一个坑。
        // 少了这个条件，「正文标签被删掉但文章还在」的关联会让文章错误命中。
        assertThat(sql).as("EXISTS 里必须显式写 t.deleted = 0，实际 SQL: %s", sql)
                .contains("deleted");
        // 参数必须是预编译绑定值，不是拼进 SQL 的字符串
        assertThat(wrapper.getParamNameValuePairs().values()).contains(9L);
    }

    @Test
    @DisplayName("筛选：tagId 为 null 时不加 EXISTS")
    void skipsTagIdWhenNull() {
        assertThat(captureWrapperFor(emptyQuery()).getTargetSql()).doesNotContain("EXISTS");
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=ArticleServiceImplTest
```

预期：**FAIL**。`filtersByCategoryId` 等筛选用例红（条件还没拼），且 `escapesBackslashBeforeWildcards` **编译失败**（`escapeLike` 还不存在）。

- [ ] **步骤 3：编写最少实现代码**

修改 `ArticleServiceImpl.java`：把 `buildFilters` 方法体替换成下面的实现，并在类末尾（`normalizePage` 之前）加入 `normalizeKeyword` 与 `escapeLike`：

```java
    private void buildFilters(LambdaQueryWrapper<Article> wrapper, ArticleQueryDTO query) {
        wrapper.eq(query.getCategoryId() != null, Article::getCategoryId, query.getCategoryId());

        // recommended / top 是「加上这个约束」的开关：false 与 null 等价，都表示不筛选，
        // false 不是「筛选出非推荐的」。写成 != null 会把 false 当成真实条件（设计规格 §3）。
        wrapper.eq(Boolean.TRUE.equals(query.getRecommended()), Article::getIsRecommended, 1);
        wrapper.eq(Boolean.TRUE.equals(query.getTop()), Article::getIsTop, 1);

        String keyword = normalizeKeyword(query.getKeyword());
        // 必须用 and(...) 把 OR 包进括号。否则拼出来是
        //   WHERE status = 1 AND title LIKE ? OR summary LIKE ?
        // AND 优先级高于 OR，等同于 (status=1 AND title LIKE ?) OR summary LIKE ? ——
        // 摘要命中的草稿与私密文章会直接泄漏进前台列表（设计规格 §3 恒定条件）。
        wrapper.and(keyword != null, x -> x.like(Article::getTitle, keyword)
                .or().like(Article::getSummary, keyword));

        // EXISTS 而非 JOIN：articleTag 是多对多，JOIN 会让挂多个标签的文章出现多行，
        // 得再加 DISTINCT，而 DISTINCT 与分页 COUNT 一起用更容易出错；EXISTS 天然去重。
        // t.deleted = 0 必须手写：@TableLogic 只保护 MyBatis-Plus 生成的 SQL，手写片段
        // 它管不着 —— 与 CategoryMapper.selectCategoryWithArticleCount 是同一个坑。
        // {0} 是预编译占位符，不是字符串拼接。
        // 此处用 article.articleId 引用外层表，依赖 MP 生成不带别名的 FROM article；
        // 若将来升级 MP 后它改为生成别名，本片段会失效 —— 单测发现不了（单测断言的是
        // wrapper 里的字符串，不是数据库真跑的结果），由冒烟脚本第二层的 tagId 筛选兜住。
        wrapper.exists(query.getTagId() != null,
                "SELECT 1 FROM articleTag t WHERE t.articleId = article.articleId"
                        + " AND t.tagId = {0} AND t.deleted = 0", query.getTagId());
    }

    /** trim 后为空则视为不传；非空则以 trim 后的值做 LIKE（设计规格 §3）。 */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return escapeLike(trimmed);
    }

    /**
     * 转义 LIKE 通配符。技术博客里搜 user_name / 100% / C++ 是家常便饭：不转义时
     * _ 匹配任意单字符、% 匹配任意串，搜一个下划线几乎命中全站文章。
     * 必须先转 \ 自身，否则会把刚写进去的转义符再转一次（1% → 1\% → 1\\%，转义失效）。
     * 依赖 MySQL 默认转义符 \ 且 NO_BACKSLASH_ESCAPES 未开启（设计规格 §4）。
     */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
```

> 若 `wrapper.exists(String, Object...)` 这个重载在当前 MyBatis-Plus 版本不可用（编译报找不到符号），等价写法是：
> ```java
> wrapper.apply(query.getTagId() != null,
>         "EXISTS (SELECT 1 FROM articleTag t WHERE t.articleId = article.articleId"
>                 + " AND t.tagId = {0} AND t.deleted = 0)", query.getTagId());
> ```
> 两者生成的 SQL 语义相同，任选其一。

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
cd backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=ArticleServiceImplTest
```

预期：**PASS**，任务 1 与任务 2 的全部用例绿。

若 `keywordIsNestedInParentheses` 红在 `contains("(")`：说明 `and(...)` 没被正确调用，或 `getTargetSql()` 的括号呈现形式与预期不同。**先打印实际 SQL 再判断**——`like` 生成的 `?` 占位符形态可能影响字符串匹配，但括号本身必须存在，这是不能让步的（去掉括号会导致草稿泄漏）。

- [ ] **步骤 5：Commit**

```bash
git add backend/src/main/java/com/blog/service/impl/ArticleServiceImpl.java \
        backend/src/test/java/com/blog/service/impl/ArticleServiceImplTest.java
git commit -m "feat(backend): article 列表五个筛选参数——EXISTS 去重与 LIKE 通配符转义"
```

---

## 任务 3：Controller 与接口契约

**文件：**
- 创建：`backend/src/main/java/com/blog/controller/ArticleController.java`
- 测试：`backend/src/test/java/com/blog/controller/ArticleControllerTest.java`

- [ ] **步骤 1：编写失败的测试**

创建 `backend/src/test/java/com/blog/controller/ArticleControllerTest.java`：

```java
package com.blog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blog.common.PageResult;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import com.blog.service.ArticleService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ArticleController.class)
@ActiveProfiles("test")
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleService articleService;

    // AdminAuthInterceptor 是 @Component 且实现 HandlerInterceptor，会被 @WebMvcTest 纳入，
    // 它的两个依赖必须提供桩，否则整个切片上下文加载失败 —— 那是与 Web 层代码无关的假失败
    // （CategoryControllerTest 出于同样原因也声明了这两个字段）。
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private AdminTokenStore adminTokenStore;

    private ArticleListVO vo(long id, String title, int viewCount) {
        ArticleListVO vo = new ArticleListVO();
        vo.setArticleId(id);
        vo.setTitle(title);
        vo.setSummary("摘要 " + id);
        vo.setCoverImage("/images/cover-" + id + ".jpg");
        vo.setViewCount(viewCount);
        return vo;
    }

    @Test
    @DisplayName("列表：返回 code=0 与 PageResult 四个字段")
    void listReturnsPageResultEnvelope() throws Exception {
        given(articleService.list(any()))
                .willReturn(PageResult.of(42, 1, 10, List.of(vo(7L, "示例文章", 128))));

        mockMvc.perform(get("/api/article/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(42))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.list[0].articleId").value(7))
                .andExpect(jsonPath("$.data.list[0].title").value("示例文章"))
                .andExpect(jsonPath("$.data.list[0].viewCount").value(128));
    }

    @Test
    @DisplayName("列表：无参调用时查询参数全为 null，钳制留给 Service 层")
    void listBindsNoParamsAsNulls() throws Exception {
        given(articleService.list(any())).willReturn(PageResult.of(0, 1, 10, List.of()));

        mockMvc.perform(get("/api/article/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isArray());

        ArgumentCaptor<ArticleQueryDTO> captor = ArgumentCaptor.forClass(ArticleQueryDTO.class);
        verify(articleService).list(captor.capture());
        ArticleQueryDTO q = captor.getValue();
        assertThat(q.getCategoryId()).isNull();
        assertThat(q.getTagId()).isNull();
        assertThat(q.getKeyword()).isNull();
        assertThat(q.getRecommended()).isNull();
        assertThat(q.getTop()).isNull();
        assertThat(q.getPage()).isNull();
        assertThat(q.getPageSize()).isNull();
    }

    @Test
    @DisplayName("列表：七个查询参数都正确绑定到 DTO")
    void listBindsQueryParams() throws Exception {
        given(articleService.list(any())).willReturn(PageResult.of(0, 2, 5, List.of()));

        mockMvc.perform(get("/api/article/list")
                        .param("categoryId", "3")
                        .param("tagId", "9")
                        .param("keyword", "spring")
                        .param("recommended", "true")
                        .param("top", "true")
                        .param("page", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<ArticleQueryDTO> captor = ArgumentCaptor.forClass(ArticleQueryDTO.class);
        verify(articleService).list(captor.capture());
        ArticleQueryDTO q = captor.getValue();
        assertThat(q.getCategoryId()).isEqualTo(3L);
        assertThat(q.getTagId()).isEqualTo(9L);
        assertThat(q.getKeyword()).isEqualTo("spring");
        assertThat(q.getRecommended()).isTrue();
        assertThat(q.getTop()).isTrue();
        assertThat(q.getPage()).isEqualTo(2);
        assertThat(q.getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("列表：categoryId 非数字时返回 40001，不是 50000")
    void listRejectsNonNumericCategoryId() throws Exception {
        // 客户端错误被误报成 50000 正是主规格 §10 点名要避免的
        mockMvc.perform(get("/api/article/list").param("categoryId", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("列表：不带 token 也能访问（不在 /api/admin/** 下，拦截器不该误伤）")
    void listIsPublicWithoutToken() throws Exception {
        given(articleService.list(any())).willReturn(PageResult.of(0, 1, 10, List.of()));

        mockMvc.perform(get("/api/article/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 真正的内容是「Service 被调用到了」—— 证明拦截器没有短路这次请求，
        // 而不只是「响应里恰好没有 40100」。
        verify(articleService).list(any());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=ArticleControllerTest
```

预期：**编译失败**，报 `ArticleController` 找不到符号。

- [ ] **步骤 3：编写最少实现代码**

创建 `backend/src/main/java/com/blog/controller/ArticleController.java`：

```java
package com.blog.controller;

import com.blog.common.PageResult;
import com.blog.common.Result;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开的文章接口。
 * 路径显式带 /api 前缀：前端 vite 代理 '/api' 无 rewrite，原样转发到本服务的 18088 端口。
 * 不在 /api/admin/** 下，故不受 AdminAuthInterceptor 保护（WebMvcConfig 只拦 admin 前缀）。
 */
@Tag(name = "文章（前台）")
@RestController
@RequestMapping("/api/article")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    /**
     * 参数不带 @RequestParam 注解：Spring MVC 会把查询串绑定到这个 POJO 上。
     * 分页与筛选的钳制一律在 Service 层做，此处只做转发（设计规格 §4）。
     */
    @Operation(summary = "公开文章分页列表（categoryId/tagId/keyword/recommended/top + page/pageSize）")
    @GetMapping("/list")
    public Result<PageResult<ArticleListVO>> list(ArticleQueryDTO query) {
        return Result.ok(articleService.list(query));
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
cd backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=ArticleControllerTest
```

预期：**PASS**。

⚠️ **若 `listRejectsNonNumericCategoryId` 红在得到 `50000` 而不是 `40001`**：这说明 Spring MVC 在 `@ModelAttribute` 绑定失败时抛的是 `BindException` 而非 `MethodArgumentNotValidException`，于是落到了 `GlobalExceptionHandler` 的 catch-all 上——**这是一个真实的缺陷**（客户端错误被误报成服务器故障，主规格 §10 明令避免），不是测试写错了。修法是在 `backend/src/main/java/com/blog/common/GlobalExceptionHandler.java` 里补一个 handler：

```java
    /** @ModelAttribute 绑定失败（如查询参数类型不符）也是客户端错误，不能让 catch-all 兜成 50000。 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? ResultCode.PARAM_INVALID.getMessage()
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        log.warn("参数绑定失败: {}", message);
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), message);
    }
```

需要额外 import `org.springframework.validation.BindException`（`FieldError` 已 import）。补完重跑本步骤。**并回头更新规格 §3 那条待验证点的结论**，把「随版本而异」改成实测结果。

- [ ] **步骤 5：跑一遍后端全量测试，确认没有回归**

运行：
```bash
cd backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test
```

预期：**PASS**。既有 13 个测试类 + 新增 2 个，全绿。若既有测试变红，说明本次改动碰到了不该碰的东西，先查清再继续。

- [ ] **步骤 6：Commit**

```bash
git add backend/src/main/java/com/blog/controller/ArticleController.java \
        backend/src/test/java/com/blog/controller/ArticleControllerTest.java
# 若步骤 4 补了 BindException handler，一并加上：
# git add backend/src/main/java/com/blog/common/GlobalExceptionHandler.java
git commit -m "feat(backend): GET /api/article/list 前台公开列表接口"
```

---

## 任务 4：抽取 `ArticleCard.vue` 并让首页改用

**文件：**
- 创建：`frontend/src/components/ArticleCard.vue`
- 修改：`frontend/src/views/Home.vue`（模板中的卡片块 + 相应样式）
- 测试：`frontend/src/__tests__/article-card.test.ts`

- [ ] **步骤 1：编写失败的测试**

创建 `frontend/src/__tests__/article-card.test.ts`：

```ts
import { describe, it, expect } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import ArticleCard from '../components/ArticleCard.vue'

const BASE = {
  articleId: 7,
  title: '示例文章',
  summary: '摘要文本',
  coverImage: '/images/cover-example.jpg',
  createdAt: '2026-09-21T14:30:00',
  viewCount: 128,
}

function mountCard(overrides: Partial<typeof BASE> = {}) {
  return mount(ArticleCard, {
    props: { ...BASE, ...overrides },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('ArticleCard 文章卡片', () => {
  it('整卡链到 /article/{articleId}', () => {
    const wrapper = mountCard()
    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/article/7')
  })

  it('渲染标题、摘要、浏览量', () => {
    const text = mountCard().text()
    expect(text).toContain('示例文章')
    expect(text).toContain('摘要文本')
    expect(text).toContain('128')
  })

  it('时间只显示日期部分，不显示时分秒', () => {
    // 后端返回 ISO-8601（规格 §2），卡片只取前 10 位
    const text = mountCard().text()
    expect(text).toContain('2026-09-21')
    expect(text).not.toContain('14:30')
  })

  it('coverImage 为空时走占位，不渲染 img', () => {
    const wrapper = mountCard({ coverImage: '' })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('.img-placeholder').exists()).toBe(true)
  })

  it('coverImage 非空时渲染 img，且不出现占位元素', () => {
    const wrapper = mountCard()
    expect(wrapper.find('img').attributes('src')).toBe('/images/cover-example.jpg')
    expect(wrapper.find('.img-placeholder').exists()).toBe(false)
  })
})
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd frontend && npm run test -- article-card
```

预期：**FAIL**，报找不到模块 `../components/ArticleCard.vue`。

- [ ] **步骤 3：编写最少实现代码**

创建 `frontend/src/components/ArticleCard.vue`：

```vue
<script setup lang="ts">
/**
 * 文章卡片。首页与分类页共用（设计规格 §2）——抽出来的理由是：卡片是最容易在两处
 * 复制后各自漂移的东西，而首页马上也要接真数据。
 * props 即后端 ArticleListVO 的六个字段，与 api/article.ts 的 ArticleListItem 一一对应。
 */
defineProps<{
  articleId: number
  title: string
  summary: string
  /** 封面图路径；空串时走 .img-placeholder 占位（主规格 §14），不报错 */
  coverImage: string
  /** ISO-8601，如 2026-09-21T14:30:00；卡片只取日期部分 */
  createdAt: string
  viewCount: number
}>()
</script>

<template>
  <router-link :to="`/article/${articleId}`" class="article-card card">
    <img v-if="coverImage" class="article-cover" :src="coverImage" :alt="title" />
    <div v-else class="article-cover img-placeholder">封面占位</div>

    <div class="article-body">
      <h3>{{ title }}</h3>
      <p class="article-summary">{{ summary }}</p>
      <div class="article-meta">
        <span class="num">浏览 {{ viewCount }} · {{ createdAt.slice(0, 10) }}</span>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.article-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  /* router-link 渲染成 <a>，需显式清掉链接默认样式 */
  text-decoration: none;
  color: inherit;
}
.article-cover {
  width: 200px;
  min-height: 120px;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: var(--radius-card);
}
.article-body {
  flex: 1;
  min-width: 0;
}
.article-body h3 {
  margin: 4px 0 8px;
  font-size: 18px;
}
.article-summary {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
  margin: 0 0 12px;
}
.article-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 13px;
}
</style>
```

> `object-fit: cover` 与 `border-radius` 是为了让真实图片（用户后续提供）保持 16:9 观感且带圆角；`img-placeholder` 的圆角由 `global.css` 提供，两者在视觉上对齐。

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
cd frontend && npm run test -- article-card
```

预期：**PASS**，5 个用例全绿。

- [ ] **步骤 5：让 `Home.vue` 改用 `ArticleCard`**

修改 `frontend/src/views/Home.vue`。

把 `<script setup>` 顶部改为引入组件：

```ts
<script setup lang="ts">
import { ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import CarouselHero from '../components/home/CarouselHero.vue'
import SidebarCategories from '../components/home/SidebarCategories.vue'
import SidebarRecommended from '../components/home/SidebarRecommended.vue'
import NoticeFloat from '../components/home/NoticeFloat.vue'
import ArticleCard from '../components/ArticleCard.vue'

// 骨架数据占位：真实数据来自 GET /api/article/list（后端已就绪，首页接线留待后续）
const articleSkeletons = [
  { articleId: 1, title: '文章标题占位 1', summary: '摘要占位：接入后端后展示真实摘要。', coverImage: '', createdAt: '2026-09-17T00:00:00', viewCount: 0 },
  { articleId: 2, title: '文章标题占位 2', summary: '摘要占位：接入后端后展示真实摘要。', coverImage: '', createdAt: '2026-09-17T00:00:00', viewCount: 0 },
  { articleId: 3, title: '文章标题占位 3', summary: '摘要占位：接入后端后展示真实摘要。', coverImage: '', createdAt: '2026-09-17T00:00:00', viewCount: 0 },
  { articleId: 4, title: '文章标题占位 4', summary: '摘要占位：接入后端后展示真实摘要。', coverImage: '', createdAt: '2026-09-17T00:00:00', viewCount: 0 },
  { articleId: 5, title: '文章标题占位 5', summary: '摘要占位：接入后端后展示真实摘要。', coverImage: '', createdAt: '2026-09-17T00:00:00', viewCount: 0 },
  { articleId: 6, title: '文章标题占位 6', summary: '摘要占位：接入后端后展示真实摘要。', coverImage: '', createdAt: '2026-09-17T00:00:00', viewCount: 0 },
]

/**
 * 首页布局 v2（A 方案）
 * - 正文板块顶部通过负 margin 遮盖轮播图下部（初始只露出轮播上部）
 * - 「查看全部图片」按钮点击后 main 下移复位，图片完整露出
 * - 用户滚动内容时，正文自然上移再次盖住图片（浏览器滚动天然行为）
 */
const isExpanded = ref(false)
const COVER_OFFSET = 180 // 正文盖住轮播的高度（px）

function toggleExpand() {
  isExpanded.value = !isExpanded.value
}
</script>
```

把模板里的卡片循环替换为：

```vue
          <div class="article-list">
            <ArticleCard v-for="item in articleSkeletons" :key="item.articleId" v-bind="item" />
          </div>
```

然后从 `Home.vue` 的 `<style scoped>` 中**删掉**这几条规则（它们已搬进 `ArticleCard.vue`）：

```css
.article-card { ... }
.article-cover { ... }
.article-body { ... }
.article-body h3 { ... }
.article-summary { ... }
.article-meta { ... }
.tag-chip { ... }
```

`.article-list` 与 `.section-title` **保留**（它们是首页布局，不属于卡片）。

> `.tag-chip` 一并删掉：骨架卡片是唯一使用处，`ArticleCard.vue` 不渲染标签（本次不做标签 JOIN，见规格 §1）。

- [ ] **步骤 6：类型检查与全量前端测试**

运行：
```bash
cd frontend && npx vue-tsc -b && npm run test
```

预期：**类型检查无报错**，**全部前端测试 PASS**（既有 4 个文件 + 新增 `article-card.test.ts`）。

- [ ] **步骤 7：Commit**

```bash
git add frontend/src/components/ArticleCard.vue \
        frontend/src/views/Home.vue \
        frontend/src/__tests__/article-card.test.ts
git commit -m "refactor(frontend): 抽出 ArticleCard 组件，首页改用（卡片不再两处复制）"
```

---

## 任务 5：`api/article.ts` 与分类页重写

**文件：**
- 创建：`frontend/src/api/article.ts`
- 重写：`frontend/src/views/Category.vue`
- 测试：`frontend/src/__tests__/category-page.test.ts`

- [ ] **步骤 1：编写失败的测试**

创建 `frontend/src/__tests__/category-page.test.ts`：

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import { defineComponent, h } from 'vue'
// el-pagination 在 main.ts 里是全局注册的，测试环境必须自己装上，
// 否则 Vue 只会打一条 "Failed to resolve component" 警告并渲染成空标签，
// 翻页用例会因为找不到 .el-pager li 而红——而那是环境问题，不是组件问题。
import ElementPlus from 'element-plus'
import Category from '../views/Category.vue'
import { fetchArticleList, type ArticleListItem } from '../api/article'
import { fetchCategoryList } from '../api/category'

// 只桩掉取数函数：组件行为（三态、翻页、路由参数响应）才是被测对象。
vi.mock('../api/article', () => ({ fetchArticleList: vi.fn() }))
vi.mock('../api/category', () => ({ fetchCategoryList: vi.fn() }))

const mockedArticles = vi.mocked(fetchArticleList)
const mockedCategories = vi.mocked(fetchCategoryList)

const ROWS: ArticleListItem[] = [
  {
    articleId: 7,
    title: '第一篇',
    summary: '摘要一',
    coverImage: '',
    createdAt: '2026-09-21T14:30:00',
    viewCount: 128,
  },
  {
    articleId: 8,
    title: '第二篇',
    summary: '摘要二',
    coverImage: '/images/cover-8.jpg',
    createdAt: '2026-09-20T09:00:00',
    viewCount: 3,
  },
]

function page(list: ArticleListItem[], total = list.length, pageNo = 1, pageSize = 10) {
  return { total, page: pageNo, pageSize, list }
}

const Blank = defineComponent({ render: () => h('div') })

async function mountAt(path: string): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Blank },
      { path: '/article/:articleId', component: Blank },
      { path: '/category/:id', component: Category },
    ],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(Category, {
    global: { plugins: [router, ElementPlus] },
  })
  return { wrapper, router }
}

describe('Category 分类页', () => {
  beforeEach(() => {
    mockedArticles.mockReset()
    mockedCategories.mockReset()
    mockedCategories.mockResolvedValue([])
  })

  it('渲染后端返回的文章卡片', async () => {
    mockedArticles.mockResolvedValue(page(ROWS))
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('第一篇')
    expect(wrapper.text()).toContain('第二篇')
    expect(wrapper.text()).not.toContain('暂无文章')
  })

  it('把路由里的 categoryId 作为筛选条件传给后端', async () => {
    mockedArticles.mockResolvedValue(page(ROWS))
    await mountAt('/category/3')
    await flushPromises()

    expect(mockedArticles).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 3, page: 1 }))
  })

  it('空结果显示空态而不是错误态', async () => {
    mockedArticles.mockResolvedValue(page([], 0))
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('暂无文章')
    expect(wrapper.text()).not.toContain('加载失败')
  })

  it('取数失败显示错误态与后端 message，重试可恢复', async () => {
    mockedArticles.mockRejectedValueOnce(new Error('服务暂不可用'))
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).toContain('服务暂不可用')

    mockedArticles.mockResolvedValueOnce(page(ROWS))
    await wrapper.get('.category-retry').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('加载失败')
    expect(wrapper.text()).toContain('第一篇')
  })

  it('路由参数从 /category/1 变到 /category/2 时重新取数', async () => {
    // 两个路径命中同一个组件实例，Vue 会复用而不重建 —— 只在 onMounted 里取数的写法
    // 在这里必红（页面不会变）。这是本用例专门守住的坑（规格 §5）。
    mockedArticles.mockResolvedValue(page(ROWS))
    const { router } = await mountAt('/category/1')
    await flushPromises()

    expect(mockedArticles).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 1 }))

    await router.push('/category/2')
    await flushPromises()

    expect(mockedArticles).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 2 }))
  })

  it('切分类时页码归 1（URL 上带着旧页码的情况）', async () => {
    // 刻意用 /category/2?page=2 而不是干净的 /category/2：后者本身就不带 query，
    // 页码自然没了，等于没测到这个风险。真正的风险路径是手改地址栏、浏览器前进后退、
    // 或将来某个带 query 的分类链接 —— 那样会去请求新分类的第 2 页，很可能直接空列表。
    mockedArticles.mockResolvedValue(page(ROWS, 50, 2))
    const { router } = await mountAt('/category/1?page=2')
    await flushPromises()

    mockedArticles.mockClear()
    mockedArticles.mockResolvedValue(page(ROWS))

    await router.push('/category/2?page=2')
    // 这里有两个串联的异步跳：先按新分类发一次请求，归 1 的 watcher 再 push 一次
    // 清掉 query，引发第二次请求。所以 flushPromises 要跟两次，否则断言时机太早。
    await flushPromises()
    await flushPromises()

    // 用 LastCalledWith：中间那次 page=2 的请求是这条链路的副产品，最终落定的那次才是要断言的。
    expect(mockedArticles).toHaveBeenLastCalledWith(
      expect.objectContaining({ categoryId: 2, page: 1 }),
    )
  })

  it('翻页把页码写进 URL', async () => {
    mockedArticles.mockResolvedValue(page(ROWS, 30, 1))
    const { wrapper, router } = await mountAt('/category/3')
    await flushPromises()

    // el-pagination 的第 2 页按钮
    const secondPage = wrapper.findAll('.el-pager li').find((li) => li.text() === '2')
    expect(secondPage).toBeTruthy()
    await secondPage!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('2')
  })

  it('非法 page 退化为第 1 页', async () => {
    // ?page= 是用户可手改的（规格 §5）
    mockedArticles.mockResolvedValue(page(ROWS))
    await mountAt('/category/3?page=abc')
    await flushPromises()

    expect(mockedArticles).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
  })

  it('用分类名做页头标题', async () => {
    mockedArticles.mockResolvedValue(page(ROWS))
    mockedCategories.mockResolvedValue([
      { categoryId: 3, categoryName: '技术', sortOrder: 1, articleCount: 2 },
    ])
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('技术')
  })

  it('分类名取不到时退化为「分类」二字，且不影响文章列表渲染', async () => {
    // 分类名只是标题装饰，为它把整页拖垮不划算（规格 §5）
    mockedArticles.mockResolvedValue(page(ROWS))
    mockedCategories.mockRejectedValue(new Error('分类接口挂了'))
    const { wrapper } = await mountAt('/category/3')
    await flushPromises()

    expect(wrapper.text()).toContain('分类')
    expect(wrapper.text()).toContain('第一篇')
    expect(wrapper.text()).not.toContain('分类接口挂了')
  })
})
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd frontend && npm run test -- category-page
```

预期：**FAIL**，报找不到模块 `../api/article`。

- [ ] **步骤 3：创建 `frontend/src/api/article.ts`**

```ts
import http, { unwrapResult, type ApiResponse } from './http'

/** 文章列表项，字段与后端 com.blog.dto.ArticleListVO 一一对应 */
export interface ArticleListItem {
  articleId: number
  title: string
  summary: string
  /** 封面图路径；后端可能返回空串，前端走占位 */
  coverImage: string
  /**
   * ISO-8601，如 2026-09-21T14:30:00。
   * 后端刻意保留 Spring Boot 默认格式（规格 §2）：各 JS 引擎都能正确解析，
   * 而 "yyyy-MM-dd HH:mm:ss" 在 Safari 上会得到 Invalid Date。
   */
  createdAt: string
  viewCount: number
}

/** 分页返回体，字段与后端 com.blog.common.PageResult 一一对应 */
export interface PageResult<T> {
  total: number
  /** 后端回显的是钳制生效后的值，不是请求值 */
  page: number
  pageSize: number
  list: T[]
}

/** 查询参数，与后端 com.blog.dto.ArticleQueryDTO 一一对应；全部可选 */
export interface ArticleQuery {
  categoryId?: number
  tagId?: number
  keyword?: string
  /** 仅 true 生效；false 与不传等价，都表示不筛选 */
  recommended?: boolean
  /** 仅 true 生效；false 与不传等价，都表示不筛选 */
  top?: boolean
  page?: number
  pageSize?: number
}

/**
 * 公开文章分页列表。
 * 后端：GET /api/article/list → Result<PageResult<ArticleListVO>>，无需登录。
 */
export async function fetchArticleList(
  query: ArticleQuery = {},
): Promise<PageResult<ArticleListItem>> {
  const res = await http.get<ApiResponse<PageResult<ArticleListItem>>>('/article/list', {
    params: query,
  })
  return unwrapResult(res.data)
}
```

- [ ] **步骤 4：重写 `frontend/src/views/Category.vue`**

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArticleCard from '../components/ArticleCard.vue'
import { fetchArticleList, type ArticleListItem } from '../api/article'
import { fetchCategoryList } from '../api/category'

/** 每页条数固定，不提供选择器（与后端默认值一致，省掉一套要同步进 URL 的状态） */
const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const categoryId = computed(() => Number(route.params.id))

/**
 * URL 是页码的唯一真相。刻意不在组件里另存 currentPage —— 两份状态迟早不一致。
 * 防御性解析：?page= 是用户可手改的，NaN 或小于 1 一律当第 1 页，
 * 不把非法值发给后端（后端也会钳制，但前端不该依赖后端兜底来保证自己的控件不炸）。
 */
const page = computed(() => {
  const raw = Number(route.query.page)
  return Number.isInteger(raw) && raw >= 1 ? raw : 1
})

const list = ref<ArticleListItem[]>([])
const total = ref(0)
const loading = ref(true)
const errorMessage = ref('')
const categoryName = ref('')

async function loadArticles() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchArticleList({
      categoryId: categoryId.value,
      page: page.value,
      pageSize: PAGE_SIZE,
    })
    list.value = result.list
    total.value = result.total
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '未知错误'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 分类名与文章列表**刻意分开**、错误处理也刻意不同：分类名只是标题装饰，
 * 拉不到就退化成「分类」二字，不弹错误、不阻塞文章列表渲染（规格 §5）。
 */
async function loadCategoryName() {
  try {
    const categories = await fetchCategoryList()
    const hit = categories.find((c) => c.categoryId === categoryId.value)
    categoryName.value = hit?.categoryName ?? ''
  } catch {
    categoryName.value = ''
  }
}

/**
 * 必须 watch 而不是只在 onMounted 里取数：/category/1 → /category/2 命中的是同一个
 * 组件实例，Vue 会复用而不重建，onMounted 不会再跑第二次（规格 §5）。
 */
watch(
  [categoryId, page],
  () => {
    loadArticles()
    loadCategoryName()
  },
  { immediate: true },
)

/** 切分类时把页码从 URL 上摘掉，避免去请求新分类的第 N 页（很可能直接空列表）。 */
watch(categoryId, () => {
  if (route.query.page !== undefined) {
    router.push({ query: {} })
  }
})

function onPageChange(next: number) {
  // 回第 1 页时删掉该参数，得到干净的 /category/3 而不是 /category/3?page=1
  if (next <= 1) {
    router.push({ query: {} })
  } else {
    router.push({ query: { page: String(next) } })
  }
}
</script>

<template>
  <div class="category-page">
    <header class="category-header">
      <h1 class="category-title">{{ categoryName || '分类' }}</h1>
      <p v-if="!loading && !errorMessage" class="category-count num">共 {{ total }} 篇</p>
    </header>

    <p v-if="loading" class="category-hint">加载中…</p>

    <div v-else-if="errorMessage" class="category-error">
      <p class="category-hint">加载失败：{{ errorMessage }}</p>
      <button type="button" class="category-retry" @click="loadArticles">重试</button>
    </div>

    <p v-else-if="list.length === 0" class="category-hint">暂无文章</p>

    <template v-else>
      <div class="article-list">
        <ArticleCard v-for="item in list" :key="item.articleId" v-bind="item" />
      </div>

      <el-pagination
        v-if="total > 0"
        class="category-pagination"
        layout="prev, pager, next"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="page"
        @current-change="onPageChange"
      />
    </template>
  </div>
</template>

<style scoped>
.category-page {
  padding: var(--space-3) 0 var(--space-5);
}
.category-header {
  margin-bottom: var(--space-3);
}
.category-title {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 600;
}
.category-count {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}
.category-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}
.category-error {
  display: grid;
  gap: 8px;
  justify-items: start;
}
.category-retry {
  border: 1px solid var(--brand-primary-soft);
  background: transparent;
  color: var(--brand-primary);
  border-radius: var(--radius-btn);
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  transition:
    background-color var(--duration-base) var(--spring-curve),
    box-shadow var(--duration-base) var(--spring-curve);
}
.category-retry:hover {
  background: var(--brand-accent-soft);
  box-shadow: var(--shadow-soft);
}
.article-list {
  display: grid;
  gap: 16px;
}
.category-pagination {
  margin-top: var(--space-3);
  justify-content: center;
}
</style>
```

- [ ] **步骤 5：运行测试验证通过**

运行：
```bash
cd frontend && npm run test -- category-page
```

预期：**PASS**，9 个用例全绿。

若 `翻页把页码写进 URL` 红在找不到 `.el-pager li`：Element Plus 的 `el-pagination` 在 `total` 较小时仍会渲染页码列表，`30/10 = 3` 页足够。**先打印 `wrapper.html()` 看实际 DOM** 再调整选择器，不要改成直接调 `onPageChange`——那样就绕开了「用户点分页器」这个真实路径。

- [ ] **步骤 6：类型检查与全量前端测试**

运行：
```bash
cd frontend && npx vue-tsc -b && npm run test
```

预期：**类型检查无报错**，**全部前端测试 PASS**。

- [ ] **步骤 7：Commit**

```bash
git add frontend/src/api/article.ts \
        frontend/src/views/Category.vue \
        frontend/src/__tests__/category-page.test.ts
git commit -m "feat(frontend): 分类页接上 GET /api/article/list，页码住在 URL 里"
```

---

## 任务 6：冒烟脚本与文档同步

**文件：**
- 创建：`backend/smoke/article-list-smoke.sh`
- 修改：`README.md`
- 修改：`docs/superpowers/specs/2026-09-17-myblog-design.md`
- 修改：`frontend/README.md`
- 修改：`backend/src/main/resources/application.yml`

- [ ] **步骤 1：编写冒烟脚本**

创建 `backend/smoke/article-list-smoke.sh`。**照 `backend/smoke/category-smoke.sh` 的体例写**（同一套头部注释结构、`jq` 硬依赖前置检查、`set -u`、逐步打印 `[步骤 N]`）。先读那份脚本再动手，以下是你必须保留的骨架与**必须写进注释的局限**：

```bash
#!/usr/bin/env bash
# article 列表接口冒烟脚本
# 依据 docs/superpowers/specs/2026-09-21-article-list-slice-design.md §7
#
# 前置条件：
#   1. 已执行 backend/src/main/resources/db/schema.sql 建库建表
#   2. 已填写 backend/config/application-local.yml
#   3. Redis 已启动
#   4. 后端已启动（启动命令见 category-smoke.sh 头部，与之相同）
#
# 运行本脚本：
#   bash backend/smoke/article-list-smoke.sh
#
# ===========================================================================
# 【本脚本分两层，原因是一个硬事实】
#   本切片没有任何文章写入口（管理端 article CRUD 不在范围内），
#   所以真库 article 表是空的 —— 第一层不需要业务数据即可跑，
#   第二层的全部断言都需要先手工灌入样例数据（见文末 INSERT）。
#
# 【第一层证明了什么】
#   任何一次成功的 GET /api/article/list，都在端到端地证明：
#     1) 真实应用能启动（不是单测上下文，不是 @WebMvcTest 切片）
#     2) 依赖注入完整（ArticleServiceImpl 真的拿到了 ArticleMapper）
#     3) 7 个 Mapper 接口都已注册（@MapperScan("com.blog.config.MybatisPlusConfig") 生效）
#   与 category-smoke.sh 的【证据一】同一个道理：若 @MapperScan 被挪回启动类，
#   所有单测与切片依然全绿，而真实应用启动时直接抛 NoSuchBeanDefinitionException。
#
# 【第一层证明不了什么】
#   空库下 total=0、list=[]，与「接口写错了导致什么都查不到」在输出上无法区分。
#   真正的筛选、排序、翻页语义只有第二层能验。
#
# 【单测覆盖不到、只能靠本脚本第二层兜住的点】
#   tagId 的 EXISTS 子查询里用 article.articleId 引用外层表，依赖 MyBatis-Plus
#   生成不带别名的 FROM article。若升级 MP 后它改为生成别名，该片段会失效 ——
#   而单测断言的是 wrapper 里的字符串，不是数据库真跑的结果，发现不了。
#   故升级 MyBatis-Plus 后必须重跑第二层。
# ===========================================================================
```

**编码契约（必须写进注释，否则第二层会假红）**：`category-smoke.sh` 头部那条 Windows/Git Bash 契约只覆盖了**请求体**走 stdin 的情形。GET 的查询串**只能走 argv**，而 argv 里的中文会被 MSYS2 转成系统 ANSI（本机 GBK），服务端按 UTF-8 解码必然匹配不到——表现为「搜索/筛选永远返回 0 条」，看起来像逻辑坏了，实际是编码问题。**故本脚本的 query 参数一律只用 ASCII**，样例数据也因此使用**显式固定 id**（9900 等高位值，避开用户真实数据）。

**脚本主体**（照此实现；`expect_code` / `expect_bool` / `pass_count` / `fail_count` 直接照抄 `category-smoke.sh`，此处不重复）：

```bash
set -u

if ! command -v jq >/dev/null 2>&1; then
  echo "[致命] 未找到 jq。本脚本依赖 jq 解析 JSON 响应体，无法继续。"
  echo "       安装示例：apt-get install jq / brew install jq / choco install jq"
  exit 1
fi

BASE="${BASE:-http://localhost:18088}"
CURL_OPTS=(--max-time 60 --connect-timeout 5)

# ---------------------------------------------------------------------------
# 【Windows/Git Bash 编码契约 · 查询串篇】本脚本所有 query 参数一律只用 ASCII。
#   category-smoke.sh 头部那条契约覆盖的是「请求体走 stdin」，而 GET 的查询串
#   只能走 argv —— 它正好在 MSYS2 会转码的那条通路上。中文关键词会以 GBK 字节
#   发出、被服务端按 UTF-8 解码，结果恒为「匹配不到」，
#   看起来像搜索逻辑坏了，实际是编码问题（排查方向会被彻底带偏）。
#   要测中文关键词，须把 URL 里的中文**预先百分号编码**写死
#   （如 用户 → %E7%94%A8%E6%88%B7），不要指望 argv 能安全传中文。
#   这也是样例数据使用显式固定 id（9900 段）的原因：让筛选参数全是数字。
# ---------------------------------------------------------------------------

SMOKE_CID=9900     # 样例分类 categoryId
SMOKE_TAG=9911     # 样例标签 tagId

# 断言响应体的某个 jq 取值等于期望值（ASCII 值）
# 用法：expect_json <步骤说明> <响应体> <jq过滤表达式> <期望值>
expect_json() {
  local desc="$1" body="$2" filter="$3" want="$4"
  local got
  got=$(echo "$body" | jq -r "$filter" 2>/dev/null)
  if [ "$got" = "$want" ]; then
    echo "  [通过] $desc（$filter=$got）"
    pass_count=$((pass_count + 1))
  else
    echo "  [失败] $desc —— 期望 $filter=$want，实际 $got"
    echo "         原始响应：$body"
    fail_count=$((fail_count + 1))
  fi
}

get() { curl -s "${CURL_OPTS[@]}" "$BASE$1"; }

# ===========================================================================
echo "=== 第一层：不需要业务数据 ==="

BODY=$(get "/api/article/list")
expect_code "无参列表接口可访问" "$BODY" 0
for key in total page pageSize list; do
  expect_bool "响应 data 含 $key 字段" \
    "$(echo "$BODY" | jq -r "(.data | has(\"$key\")) | if . then 1 else 0 end")"
done
expect_bool "data.list 是数组" \
  "$(echo "$BODY" | jq -r '.data.list | if type == "array" then 1 else 0 end')"
expect_json "无参调用 page 默认 1"    "$BODY" '.data.page' 1
expect_json "无参调用 pageSize 默认 10" "$BODY" '.data.pageSize' 10

BODY=$(get "/api/article/list?pageSize=999")
expect_json "pageSize=999 被钳制为 50（回显钳制后的值）" "$BODY" '.data.pageSize' 50

BODY=$(get "/api/article/list?page=0&pageSize=0")
expect_json "page=0 被钳制为 1"     "$BODY" '.data.page' 1
expect_json "pageSize=0 被钳制为 1" "$BODY" '.data.pageSize' 1

BODY=$(get "/api/article/list?categoryId=abc")
expect_code "categoryId 非数字返回 40001" "$BODY" 40001
expect_bool "categoryId 非数字不得落到 50000（客户端错误被误报成服务器故障）" \
  "$([ "$(echo "$BODY" | jq -r '.code')" != "50000" ] && echo 1 || echo 0)"

BODY=$(get "/api/article/list?categoryId=999999")
expect_code "不存在的分类返回 code=0 而非 404" "$BODY" 0
expect_json "不存在的分类 total=0"            "$BODY" '.data.total' 0

BODY=$(get "/api/article/list?keyword=%20%20%20")
expect_code "纯空格 keyword 不报错（等同不传）" "$BODY" 0

# ===========================================================================
echo
echo "=== 第二层：需要先灌入样例数据 ==="

BODY=$(get "/api/article/list?categoryId=$SMOKE_CID")
if [ "$(echo "$BODY" | jq -r '.data.total')" = "0" ]; then
  echo "[跳过] 第二层：categoryId=$SMOKE_CID 下没有文章，样例数据尚未灌入。"
  echo "       请先执行本脚本末尾的 INSERT，再重跑本脚本。"
else
  # 样例数据共 5 篇：3 篇公开 + 1 草稿 + 1 私密。前台只能看到 3 篇。
  # 这里同时验证了「status 恒定条件」与 categoryId 筛选两件事。
  expect_json "该分类下前台可见 3 篇（草稿与私密被 status=1 挡掉）" \
    "$BODY" '.data.total' 3
  # 样例数据里草稿与私密的 viewCount 都是 0，三篇公开的分别是 10/20/30。
  # 结果里出现 viewCount=0 就说明草稿或私密泄漏进了前台列表。
  expect_json "结果里没有 viewCount=0 的文章（即草稿/私密未泄漏）" \
    "$BODY" '[.data.list[] | select(.viewCount == 0)] | length' 0
  expect_json "默认排序里置顶文章排第一"                 "$BODY" '.data.list[0].isTop' 1
  expect_json "置顶文章的浏览量为 10（样例数据特征值）" "$BODY" '.data.list[0].viewCount' 10

  BODY=$(get "/api/article/list?tagId=$SMOKE_TAG")
  expect_json "tagId 命中所挂文章" "$BODY" '.data.total' 1
  # 这篇挂了两个标签。JOIN 写法会让它出现两次，EXISTS 天然去重 —— 这是本断言的真正内容。
  expect_json "挂两个标签的文章只出现一次（EXISTS 去重，不是 JOIN）" \
    "$BODY" '[.data.list[] | select(.articleId == 9902)] | length' 1

  BODY=$(get "/api/article/list?keyword=user_name")
  expect_json "keyword 命中摘要里的词（证明摘要也参与匹配）" "$BODY" '.data.total' 1
  # 未转义时 _ 匹配任意单字符，这条会命中全部文章（total 变成 3）。
  expect_bool "下划线被转义：不会命中全站" \
    "$([ "$(echo "$BODY" | jq -r '.data.total')" = "1" ] && echo 1 || echo 0)"

  BODY=$(get "/api/article/list?keyword=100%25")
  expect_json "百分号被转义：只命中含 100% 的那篇" "$BODY" '.data.total' 1

  BODY=$(get "/api/article/list?recommended=true")
  expect_json "recommended=true 只出推荐位那篇" "$BODY" '.data.total' 1
  expect_json "推荐位那篇的 viewCount 为 20"     "$BODY" '.data.list[0].viewCount' 20

  BODY=$(get "/api/article/list?top=true")
  expect_json "top=true 只出置顶那篇"       "$BODY" '.data.total' 1
  expect_json "置顶那篇的 viewCount 为 10" "$BODY" '.data.list[0].viewCount' 10

  # 翻页不重不漏：两页的 articleId 集合无交集，并集等于总数
  P1=$(get "/api/article/list?categoryId=$SMOKE_CID&pageSize=2&page=1")
  P2=$(get "/api/article/list?categoryId=$SMOKE_CID&pageSize=2&page=2")
  expect_bool "第 1、2 页的 articleId 无交集" \
    "$([ "$(jq -n --argjson a "$(echo "$P1" | jq '.data.list | map(.articleId)')" \
                   --argjson b "$(echo "$P2" | jq '.data.list | map(.articleId)')" \
                   '[$a[] | select(. as $x | $b | index($x))] | length')" = "0" ] && echo 1 || echo 0)"
  expect_bool "两页并集等于总数 3（翻页不遗漏）" \
    "$([ "$(jq -n --argjson a "$(echo "$P1" | jq '.data.list | map(.articleId)')" \
                   --argjson b "$(echo "$P2" | jq '.data.list | map(.articleId)')" \
                   '($a + $b) | unique | length')" = "3" ] && echo 1 || echo 0)"
fi
```

**第二层失败时的判断原则**：先确认样例数据真的灌进去了（`SELECT COUNT(*) FROM article WHERE categoryId = 9900 AND deleted = 0;` 应为 5），再怀疑代码。空库下第二层每一条都会「红」，那不是缺陷。

**结果汇总**（照抄 `category-smoke.sh` 末尾的写法）：

```bash
echo
echo "=========== 汇总 ==========="
echo "通过 $pass_count 项，失败 $fail_count 项"
[ "$fail_count" -eq 0 ] || exit 1
```

脚本末尾附样例数据，**由用户手工执行**：

```sql
-- ============ 第二层冒烟所需的样例数据（手工执行） ============
-- 前置：先执行 schema.sql。
--
-- 【为什么用显式固定 id（9900 段）而不是自增】
--   脚本里的筛选参数必须全是 ASCII：Git Bash 把 URL 里的中文转成 GBK 后再发给
--   原生 curl，服务端按 UTF-8 解码必然匹配不到（见脚本头部编码契约）。
--   所以脚本不能靠「按中文名字去查 id」，只能写死数字。9900 段是高位值，
--   正常使用的自增 id 短期内不会撞上。
--
-- 灌完后核对：
--   SELECT articleId, title, status, isTop, isRecommended, viewCount
--     FROM article WHERE categoryId = 9900 AND deleted = 0;
--   预期 5 行：3 篇 status=1（viewCount 10/20/30），1 篇 status=0，1 篇 status=2。

INSERT INTO category (categoryId, categoryName, sortOrder, createdAt, updatedAt, deleted)
VALUES (9900, '冒烟分类', 99, NOW(), NOW(), 0);

-- 三篇公开 + 一篇草稿 + 一篇私密。
-- 9902 的摘要里刻意放了 user_name 与 100%，用来验证 LIKE 通配符转义。
INSERT INTO article (articleId, title, summary, content, coverImage, categoryId, status, isTop, isRecommended, viewCount, createdAt, updatedAt, deleted) VALUES
  (9901, '冒烟-置顶公开', '置顶的那一篇',                    '# 正文', '', 9900, 1, 1, 0, 10, NOW() - INTERVAL 5 DAY, NOW(), 0),
  (9902, '冒烟-普通公开', '普通的一篇',                      '# 正文', '', 9900, 1, 0, 1, 20, NOW() - INTERVAL 4 DAY, NOW(), 0),
  (9903, '冒烟-含关键词', '这篇摘要里有 user_name 和 100% 两个词', '# 正文', '', 9900, 1, 0, 0, 30, NOW() - INTERVAL 3 DAY, NOW(), 0),
  (9904, '冒烟-草稿不该出现', '草稿摘要',                    '# 正文', '', 9900, 0, 0, 0,  0, NOW() - INTERVAL 2 DAY, NOW(), 0),
  (9905, '冒烟-私密不该出现', '私密摘要',                    '# 正文', '', 9900, 2, 0, 0,  0, NOW() - INTERVAL 1 DAY, NOW(), 0);

-- 给 9902 挂两个标签：用来验证 EXISTS 不会让挂多标签的文章在结果里出现两次
-- （JOIN 写法会，这正是选 EXISTS 的理由）。
INSERT INTO tag (tagId, tagName, createdAt, updatedAt, deleted) VALUES
  (9911, '冒烟标签A', NOW(), NOW(), 0),
  (9912, '冒烟标签B', NOW(), NOW(), 0);

INSERT INTO articleTag (articleId, tagId, createdAt, updatedAt, deleted) VALUES
  (9902, 9911, NOW(), NOW(), 0),
  (9902, 9912, NOW(), NOW(), 0);
```

**清理样例数据**（第二层跑完后手工执行，别留在库里）：

```sql
DELETE FROM articleTag WHERE articleId = 9902;
DELETE FROM article    WHERE articleId BETWEEN 9901 AND 9905;
DELETE FROM tag        WHERE tagId     IN (9911, 9912);
DELETE FROM category   WHERE categoryId = 9900;
```

> 这里是**物理删除**，与本项目「一律逻辑删除」的生产约定相反——这是刻意的：样例数据不是业务数据，留在库里只会污染后续的手工核对与将来的真机数据。生产代码里任何地方都不得照抄这个写法。
>
> **列名必须与 `backend/src/main/resources/db/schema.sql` 逐字核对。** 本项目刻意关闭了 `map-underscore-to-camel-case`（见 `application.yml` 与 `ColumnNamingConventionTest`），DDL 用的是驼峰列名。上面的 INSERT 按驼峰写；执行前先 `DESC article;` 确认，列名不符会直接报 1054 Unknown column。

- [ ] **步骤 2：语法检查冒烟脚本**

运行：
```bash
bash -n backend/smoke/article-list-smoke.sh && echo "语法 OK"
```

预期：输出 `语法 OK`。

**不要尝试运行它**——那需要真实后端、MySQL 与 Redis，超出实现者的验证边界。运行是用户的事。

- [ ] **步骤 3：改写 `application.yml` 的 Jackson 注释**

修改 `backend/src/main/resources/application.yml` 中 `spring.jackson` 那段注释。把现在的

```yaml
  jackson:
    # 不设置 date-format：它只作用于 java.util.Date（本项目几乎不用），
    # 对 java.time.LocalDateTime 无效，是「看似生效实则不生效」的静默陷阱。
    # 将来 article 接口涉及时间字段时，用 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    # 或注册 Jackson2ObjectMapperBuilderCustomizer，不要依赖 date-format 属性。
    time-zone: Asia/Shanghai
```

改成：

```yaml
  jackson:
    # 不设置 date-format：它只作用于 java.util.Date（本项目几乎不用），
    # 对 java.time.LocalDateTime 无效，是「看似生效实则不生效」的静默陷阱。
    #
    # 也不定制 LocalDateTime 的格式，**刻意保留 Boot 默认的 ISO-8601**（2026-09-21T14:30:00）。
    # 两个理由，详见 docs/superpowers/specs/2026-09-21-article-list-slice-design.md §2：
    #   1) ISO-8601 是 JS 各引擎都能正确解析的格式，而 "yyyy-MM-dd HH:mm:ss"
    #      在 Safari/JSC 上会得到 Invalid Date —— 管理端要发时间回后端时这个差别会咬人。
    #   2) Jackson 的全局定制会连带改掉 Redis 里的值形态：RedisConfig 注入的是容器里
    #      同一个 ObjectMapper，把「HTTP 响应格式」和「缓存存储格式」焊在一起。
    time-zone: Asia/Shanghai
```

- [ ] **步骤 4：更新 `README.md` 的「## 状态」**

修改 `README.md` 的 `## 状态` 一节，把「前端已接上首条真实数据链路」那段之后替换/追加为：

```markdown
> **前端已接上两条真实数据链路：** 首页侧栏「分类」卡片读 `GET /api/category/list`（2026-09-20），
> `/category/:id` 分类页读 `GET /api/article/list`（2026-09-21，含分页与 URL 页码）。
> 浏览器实测能渲染真库数据（空库显示「暂无文章」）。
>
> **验证边界：** 编译、Mockito 单测、MockMvc 切片、前端 vitest 与 `vue-tsc` 类型检查由开发侧负责；
> category 竖切链路另经 `backend/smoke/category-smoke.sh` 真实 HTTP 跑通。
> **`GET /api/article/list` 的端到端验证尚未完成**：冒烟脚本 `backend/smoke/article-list-smoke.sh`
> 已就绪但**待手工执行**，且因本切片没有文章写入口，其第二层需要先手工灌入样例数据（脚本末尾附 INSERT）。
> **建库建表（DDL）与脚本末尾的落库核对由使用者手工执行。**
```

- [ ] **步骤 5：更新主规格 §15 与 §9**

修改 `docs/superpowers/specs/2026-09-17-myblog-design.md`：

**§15 第 4 步**改为：

```markdown
4. 前台核心接口 + 缓存/浏览量 —— 进行中：`GET /api/category/list`（含 `blog:category:list` 缓存，TTL 5 分钟，写操作失效）与 `GET /api/article/list`（含 categoryId/tagId/keyword/recommended/top 全部筛选与分页，**不带缓存**，见下方 §9 注记）已就位；文章详情与浏览量统计待实现
```

**§15 第 6 步**改为：

```markdown
6. 前台页面（首页/列表/详情/归档/搜索/友链/关于）—— 进行中：首页侧栏「分类」卡片与 `/category/:id` 分类页已接真实数据；`/tag/:id`、`/archive`、`/search`、`/friends`、`/about`、`/article/:articleId` 仍为占位
```

**§9 缓存表**在 `blog:article:list:{hash}` 那一行下方加注记：

```markdown
> **注记（2026-09-21）：** `blog:article:list:{hash}` **暂缓实现**。该键是参数组合
> （`categoryId`/`tagId`/`keyword`/`recommended`/`top` × `page`/`pageSize`），失效需靠
> `SCAN MATCH` 或维护键索引，复杂度与个人博客的收益不成比例；列表里的 `viewCount`
> 来自 MySQL 定时落库，缓存只会让它更旧。待管理端 article CRUD 落地、有了真实写入压力后重新评估。
> 详见 [2026-09-21-article-list-slice-design.md](./2026-09-21-article-list-slice-design.md) §9。
```

- [ ] **步骤 6：更新 `frontend/README.md`**

在「## 与后端的两个契约」之后追加一节：

```markdown
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
```

- [ ] **步骤 7：Commit**

```bash
git add backend/smoke/article-list-smoke.sh \
        README.md \
        frontend/README.md \
        backend/src/main/resources/application.yml \
        docs/superpowers/specs/2026-09-17-myblog-design.md
git commit -m "docs: article 列表切片的冒烟脚本与四处文档同步

冒烟脚本分两层：第一层不需要业务数据（本切片没有文章写入口，真库 article 表是空的），
第二层需先手工灌样例数据，脚本末尾附 INSERT。"
```

- [ ] **步骤 8：交付说明**

向用户报告时**必须**包含：

1. 后端 `mvn test` 与前端 `npm run test` / `npx vue-tsc -b` 的实际结果
2. **明确说明端到端尚未验证**，需要用户手工执行：
   - 若已建表，先跑脚本末尾的 INSERT 样例数据
   - 后端与 Redis 启动后，`bash backend/smoke/article-list-smoke.sh`
3. 任务 3 步骤 4 的待验证点结论：`categoryId=abc` 实测返回的是 40001 还是 50000？若补了 `BindException` handler，一并说明，并提醒规格 §3 的那条注记需要按实测结果更新
4. 前端页面尚未在浏览器中实测（可用 puppeteer-core 驱动本机 Edge 验证 `/category/:id`，见既有做法；该验证同样属于用户侧）

---

## 附：任务依赖关系

```
任务 1（Service 基础）
  └─> 任务 2（五个筛选参数）        ← 依赖任务 1 的 ArticleServiceImpl
        └─> 任务 3（Controller）    ← 依赖任务 1/2 的 ArticleService
任务 4（ArticleCard）               ← 独立，可与 1-3 并行
  └─> 任务 5（Category.vue）        ← 依赖任务 4 的 ArticleCard.vue
任务 6（冒烟与文档）                ← 依赖全部
```

任务 1→2→3 必须串行（同一批文件）。任务 4→5 可与后端并行。任务 6 最后。
