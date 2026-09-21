package com.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.PageResult;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.entity.Article;
import com.blog.mapper.ArticleMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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

    /**
     * 单元测试没有容器，TableInfo 不会由 Mapper 扫描注册（AuditMetaObjectHandlerTest 同理）。
     * getTargetSql() 要把 SFunction 解析成列名，必须先注册 Article 的 TableInfo。
     */
    @BeforeAll
    static void registerArticleTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        // 本项目列名契约是驼峰（application.yml 显式 map-underscore-to-camel-case=false，
        // 由 ColumnNamingConventionTest 钉死）。MyBatis-Plus 默认是 true，会把 isTop 转成
        // is_top，导致下面排序断言的 contains("isTop"/"createdAt"/"articleId") 全部对不上。
        configuration.setMapUnderscoreToCamelCase(false);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Article.class);
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

    /** 打桩 selectPage：固定返回给定记录与总数。所有用例统一走这里，不要各自复制 willAnswer 块。 */
    private void stubPage(List<Article> records, long total) {
        // 用 doAnswer 而非 given(...).willAnswer(...)：normalizesPage / normalizesPageSize 在
        // 循环里反复 stubPage，given 方式在重打桩时会把上一轮 answer 以 any() 的 null 占位参数
        // 再触发一次（selectPage(null,null)），得到 p 为 null 的 NPE。doAnswer 不会触发原方法。
        doAnswer(invocation -> {
            Page<Article> p = invocation.getArgument(0);
            p.setRecords(records);
            p.setTotal(total);
            return p;
        }).when(articleMapper).selectPage(any(), any());
    }

    /** 取出上一次 selectPage 实际收到的 wrapper。 */
    @SuppressWarnings("unchecked")
    private AbstractWrapper<Article, ?, ?> lastWrapper() {
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        // 用 atLeastOnce 而非默认的 times(1)：同一测试方法可能跑多次 list()（如
        // recommendsAndTopsOnlyWhenTrue 三次、skipsBlankKeyword 两次），默认 verify 会在
        // 第二次捕获时因「调用了 2 次却要求恰好 1 次」抛 TooManyActualInvocations。
        // captor.getValue() 返回的是最后一次捕获到的值，正好就是「上一次」的 wrapper。
        verify(articleMapper, atLeastOnce()).selectPage(any(), captor.capture());
        return (AbstractWrapper<Article, ?, ?>) captor.getValue();
    }

    /** 打桩后跑一次无参查询，返回捕获到的 wrapper。 */
    private AbstractWrapper<Article, ?, ?> captureWrapper(List<Article> records, long total) {
        stubPage(records, total);
        service.list(emptyQuery());
        return lastWrapper();
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
            stubPage(List.of(), 0);

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

            stubPage(List.of(), 0);

            PageResult<ArticleListVO> result = service.list(query(1, raw));

            assertThat(result.getPageSize()).as("pageSize=%s 应钳制为 %s", raw, expected).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("分页钳制：传给 selectPage 的是钳制后的值（不是请求值）")
    void passesClampedValuesToMapper() {
        stubPage(List.of(), 0);

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
        stubPage(List.of(), 0);

        PageResult<ArticleListVO> result = service.list(query(0, 999));

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(50);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
    }

    @Test
    @DisplayName("VO 转换：逐字段映射，且不带上 content 与 deleted")
    void mapsArticleToVoExplicitly() {
        Article a = article(7L, "示例文章", 128);
        a.setContent("正文很长很长");   // 不该出现在 VO 上
        a.setDeleted(0);                // 同上
        stubPage(List.of(a), 1);

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

    // ---------- 五个筛选参数 ----------

    private ArticleQueryDTO filterQuery(java.util.function.Consumer<ArticleQueryDTO> customizer) {
        ArticleQueryDTO q = new ArticleQueryDTO();
        customizer.accept(q);
        return q;
    }

    /**
     * 用给定的查询对象跑一次，返回捕获到的 wrapper。
     * stubPage / lastWrapper 是任务 1 已在本文件里写好的助手，**直接复用**——不要再复制
     * 一份 willAnswer 桩或 ArgumentCaptor 样板进来（同一份样板已经被复制过 5 次）。
     */
    private AbstractWrapper<Article, ?, ?> captureWrapperFor(ArticleQueryDTO q) {
        stubPage(List.of(), 0);
        service.list(q);
        AbstractWrapper<Article, ?, ?> wrapper = lastWrapper();
        // MyBatis-Plus 只在生成 SQL 段（getTargetSql）时才填充 paramNameValuePairs；
        // 提前触发一次，否则 trimsKeyword / escapesLikeWildcardsInKeyword 直接调
        // getParamNameValuePairs() 拿到的是空 Map（filtersByCategoryId 能过是因为它先
        // 断言了 getTargetSql()）。
        wrapper.getTargetSql();
        return wrapper;
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
        // 断言必须看 WHERE 段（getNormal().getSqlSegment()）而不是 getTargetSql()：后者带着
        // 任务 1 的恒定排序 ORDER BY isTop DESC，天然含 isTop，会让 doesNotContain("isTop")
        // 永远失败，也让 onlyTrue 的 contains("isTop") 恒真（压根没验证 top 筛选是否被加上）。
        AbstractWrapper<Article, ?, ?> onlyTrue = captureWrapperFor(filterQuery(q -> {
            q.setRecommended(true);
            q.setTop(true);
        }));
        assertThat(onlyTrue.getExpression().getNormal().getSqlSegment())
                .contains("isRecommended").contains("isTop");

        AbstractWrapper<Article, ?, ?> whenFalse = captureWrapperFor(filterQuery(q -> {
            q.setRecommended(false);
            q.setTop(false);
        }));
        assertThat(whenFalse.getExpression().getNormal().getSqlSegment())
                .as("false 必须等同于不筛选，实际 SQL: %s", whenFalse.getTargetSql())
                .doesNotContain("isRecommended")
                .doesNotContain("isTop");

        AbstractWrapper<Article, ?, ?> whenNull = captureWrapperFor(emptyQuery());
        assertThat(whenNull.getExpression().getNormal().getSqlSegment())
                .doesNotContain("isRecommended")
                .doesNotContain("isTop");
    }

    @Test
    @DisplayName("筛选：keyword 用括号包住 title OR summary，避免 OR 逃逸污染其他条件")
    void keywordIsNestedInParentheses() {
        AbstractWrapper<Article, ?, ?> wrapper =
                captureWrapperFor(filterQuery(q -> q.setKeyword("spring")));

        // ⚠️ 不要退回成 assertThat(sql).contains("(") —— 那是恒真的：MyBatis-Plus 的
        // NormalSegmentList 无条件给条件列表加**外层**括号，所以漏掉 and(...) 嵌套、
        // 直接 like(title).or().like(summary) 时 SQL 是
        //   (status = ? AND title LIKE ? OR summary LIKE ?)
        // 它也含括号、也含 OR、也含 title/summary，那条断言照样全绿，而草稿与私密文章
        // 已经泄漏进前台列表。真正的判据是「包住 OR 的那个括号分组里不含 status」：
        //   正确：(status = ? AND (title LIKE ? OR summary LIKE ?))
        //   泄漏：(status = ? AND title LIKE ? OR summary LIKE ?)
        String sql = wrapper.getTargetSql();
        assertThat(sql).contains("title").contains("summary").contains("OR");

        int or = sql.indexOf("OR");
        assertThat(or).as("实际 SQL: %s", sql).isGreaterThan(-1);
        String orGroup = sql.substring(sql.lastIndexOf('(', or), sql.indexOf(')', or));
        assertThat(orGroup)
                .as("OR 必须被独立括号分组，否则 status=1 会被 OR 绕过、草稿与私密文章泄漏进列表。"
                        + "实际 SQL: %s，OR 所在分组: %s", sql, orGroup)
                .doesNotContain("status");
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

    // ---------- VO 转换 ----------

    @Test
    @DisplayName("VO 转换：viewCount 为 null 时按 0 处理")
    void mapsNullViewCountToZero() {
        Article a = article(7L, "空浏览量", null);
        stubPage(List.of(a), 1);

        PageResult<ArticleListVO> result = service.list(query(1, 10));

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getViewCount()).isZero();
    }
}
