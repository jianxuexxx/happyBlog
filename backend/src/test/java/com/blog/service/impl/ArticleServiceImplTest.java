package com.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        verify(articleMapper).selectPage(any(), captor.capture());
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
