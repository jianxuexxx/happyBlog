package com.blog.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.blog.entity.Article;
import com.blog.entity.ArticleTag;
import com.blog.entity.Category;
import com.blog.entity.FriendLink;
import com.blog.entity.Notice;
import com.blog.entity.SiteConfig;
import com.blog.entity.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 列名映射回归护栏。
 *
 * <p>本项目的 DDL 与本项目实体字段统一使用驼峰列名，因此
 * {@code mybatis-plus.configuration.map-underscore-to-camel-case} 必须为 false。
 * 若为 true，MyBatis-Plus 会把没有显式 {@code @TableField(value=...)} 的字段名
 * 经 camelToUnderline 转成下划线去找列，BaseMapper 生成的 insert / selectById /
 * updateById / 逻辑删除会全部抛 1054 Unknown column —— 这一失效不会让任何现有单测变红，
 * 只有连上真库才暴露，故用本测试把配置与最终列名钉死。
 */
class ColumnNamingConventionTest {

    private static final String UNDERSCORE_KEY = "mybatis-plus.configuration.map-underscore-to-camel-case";

    /** 建表语句起始行，捕获表名（如 {@code CREATE TABLE IF NOT EXISTS `category` (}）。 */
    private static final Pattern CREATE_TABLE =
            Pattern.compile("^CREATE TABLE(?: IF NOT EXISTS)?\\s+`([^`]+)`\\s*\\(", Pattern.CASE_INSENSITIVE);

    /** 列定义行：行首第一个 token 就是反引号包起来的列名。 */
    private static final Pattern COLUMN_DEF = Pattern.compile("^`([^`]+)`");

    /** 实体 → 期望的驼峰列名（主键 + 普通字段 + 逻辑删除字段），即任务 11 DDL 的列名契约。 */
    private static final Map<Class<?>, ExpectedEntity> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put(Article.class, new ExpectedEntity("article", "articleId", List.of(
                "title", "summary", "content", "coverImage", "categoryId", "status",
                "isTop", "isRecommended", "viewCount", "createdAt", "updatedAt", "deleted")));
        EXPECTED.put(Category.class, new ExpectedEntity("category", "categoryId", List.of(
                "categoryName", "sortOrder", "createdAt", "updatedAt", "deleted")));
        EXPECTED.put(Tag.class, new ExpectedEntity("tag", "tagId", List.of(
                "tagName", "createdAt", "updatedAt", "deleted")));
        EXPECTED.put(ArticleTag.class, new ExpectedEntity("articleTag", "id", List.of(
                "articleId", "tagId", "createdAt", "updatedAt", "deleted")));
        EXPECTED.put(FriendLink.class, new ExpectedEntity("friendLink", "friendLinkId", List.of(
                "name", "url", "avatar", "description", "sortOrder", "createdAt", "updatedAt", "deleted")));
        EXPECTED.put(SiteConfig.class, new ExpectedEntity("siteConfig", "configKey", List.of(
                "configValue", "createdAt", "updatedAt", "deleted")));
        EXPECTED.put(Notice.class, new ExpectedEntity("notice", "noticeId", List.of(
                "title", "content", "startsAt", "endsAt", "createdAt", "updatedAt", "deleted")));
    }

    private static final Map<Class<?>, TableInfo> TABLE_INFOS = new LinkedHashMap<>();

    @BeforeAll
    static void registerTableInfosWithRealConfig() throws IOException {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(readUnderscoreMappingFromRealConfig());

        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : EXPECTED.keySet()) {
            // TableInfoHelper 有按 Class 的静态缓存，且 initTableInfo 在缓存项 Configuration
            // 相同（Configuration 未重写 equals，实为同一实例）时会直接复用缓存。
            // AuditMetaObjectHandlerTest 也会注册 Category，为避免测试间互相干扰、
            // 保证本测试与执行顺序无关，注册前先显式清除缓存项（TableInfoHelper.remove 公开可用）。
            TableInfoHelper.remove(entity);
            TABLE_INFOS.put(entity, TableInfoHelper.initTableInfo(assistant, entity));
        }
    }

    @AfterAll
    static void clearTableInfoCache() {
        EXPECTED.keySet().forEach(TableInfoHelper::remove);
    }

    private static boolean readUnderscoreMappingFromRealConfig() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources)
                .as("application.yml 应能被解析出至少一个 PropertySource")
                .isNotEmpty();

        // 用 addFirst 让「仓库里这份文件的值」拥有最高优先级：本测试守护的是提交进仓的配置，
        // 不应被环境里偶然存在的系统属性/环境变量掩盖掉。
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(source -> environment.getPropertySources().addFirst(source));

        Boolean value = environment.getProperty(UNDERSCORE_KEY, Boolean.class);
        assertThat(value)
                .as("application.yml 必须显式声明 %s", UNDERSCORE_KEY)
                .isNotNull();
        return value;
    }

    @Test
    @DisplayName("application.yml 的 map-underscore-to-camel-case 必须为 false")
    void realConfigKeepsCamelCaseColumns() throws IOException {
        assertThat(readUnderscoreMappingFromRealConfig())
                .as("DDL 是驼峰列名，若该开关为 true，MP 会去找下划线列名，全部 SQL 将 1054")
                .isFalse();
    }

    @Test
    @DisplayName("7 个实体的表名、主键列与全部字段列都是驼峰，不含下划线")
    void allEntitiesGenerateCamelCaseTableAndColumnNames() {
        List<String> checkedColumns = new ArrayList<>();

        EXPECTED.forEach((entity, expected) -> {
            TableInfo tableInfo = TABLE_INFOS.get(entity);

            assertThat(tableInfo)
                    .as("%s 未注册出 TableInfo", entity.getSimpleName())
                    .isNotNull();

            assertThat(tableInfo.getTableName())
                    .as("%s 的表名", entity.getSimpleName())
                    .isEqualTo(expected.tableName())
                    .doesNotContain("_");

            assertThat(tableInfo.getKeyColumn())
                    .as("%s 的主键列", entity.getSimpleName())
                    .isEqualTo(expected.keyColumn())
                    .doesNotContain("_");

            List<String> actualColumns = tableInfo.getFieldList().stream()
                    .map(TableFieldInfo::getColumn)
                    .toList();
            checkedColumns.addAll(actualColumns);

            // 用 assertThat(...).containsExactlyInAnyOrder 比对，顺带保证字段列表非空且不多不少，
            // 避免断言空集合而「空转通过」。
            assertThat(actualColumns)
                    .as("%s 的字段列", entity.getSimpleName())
                    .containsExactlyInAnyOrderElementsOf(expected.fieldColumns());

            actualColumns.forEach(column -> assertThat(column)
                    .as("%s 生成的列名不应含下划线", entity.getSimpleName())
                    .doesNotContain("_"));
        });

        // 兜底：确认本测试确实覆盖到了多词字段（否则整体断言可能空转）。
        assertThat(checkedColumns)
                .as("应覆盖到多词字段，证明断言不是空转")
                .contains("coverImage", "categoryName", "sortOrder", "viewCount",
                        "articleId", "startsAt", "configValue", "createdAt");
    }

    /**
     * 把真实 DDL 也钉进这份契约。
     *
     * <p>上面两个用例比对的双方是 EXPECTED 这张 Java 常量表与实体推导出的 TableInfo，
     * <b>完全不读</b> db/schema.sql —— 二者可以各自漂移而全部用例依然全绿，
     * 一致性此前只能靠人工逐列比对维持（那正说明它没有护栏）。
     * 本用例用<b>同一份</b> EXPECTED 去核对仓库里真正要执行的建表脚本，
     * 做到「一处口径、两个消费者」：改 EXPECTED 会红，改 schema.sql 也会红。
     *
     * <p>本项目列名契约的意义正在于「DDL 列的驼峰名 == 实体字段名」：
     * 只有两边逐字一致，BaseMapper 生成的 SQL 才找得到列。故这里断言的是逐字相等，
     * 而不是「都含驼峰」这类宽松条件。
     */
    @Test
    @DisplayName("db/schema.sql 的 7 张表名与全部列名必须与 EXPECTED 契约逐字一致")
    void schemaSqlColumnsMatchSameExpectedContract() throws IOException {
        Map<String, List<String>> actualTables = parseSchemaTables();

        // 同一份 EXPECTED，另一种展开方式：表名 → （主键列 + 普通字段列 + 逻辑删除列）
        Map<String, List<String>> expectedTables = new LinkedHashMap<>();
        EXPECTED.values().forEach(entity -> {
            List<String> columns = new ArrayList<>();
            columns.add(entity.keyColumn());
            columns.addAll(entity.fieldColumns());
            expectedTables.put(entity.tableName(), columns);
        });

        // 表名双向核对：schema.sql 里漏一张、多一张、拼写不符，都会在这里失败。
        assertThat(actualTables.keySet())
                .as("db/schema.sql 里 CREATE TABLE 的表名集合")
                .containsExactlyInAnyOrderElementsOf(expectedTables.keySet());

        // 列名双向核对：多了列、少了列、拼写不符（如 categoryNameX）都会失败。
        expectedTables.forEach((table, columns) -> {
            assertThat(columns)
                    .as("契约中表 %s 的列不应为空（否则下面的断言会空转通过）", table)
                    .isNotEmpty();
            assertThat(actualTables.get(table))
                    .as("db/schema.sql 中表 %s 的列", table)
                    .containsExactlyInAnyOrderElementsOf(columns);
        });
    }

    /**
     * 从 classpath 上的 db/schema.sql 解析出「表名 → 列名列表」。
     *
     * <p>解析规则（刻意写得保守，宁可不认也不能认错）：
     * <ol>
     *   <li>按行处理；整行 {@code --} 注释与空行直接跳过（本文件没有行尾注释）</li>
     *   <li>行首为 {@code CREATE TABLE [IF NOT EXISTS] `x` (} 的行开启一张表</li>
     *   <li>行首为 {@code )} 的行（{@code ) ENGINE=InnoDB ... ;}）结束当前表，
     *       其后的内容（如 {@code USE `happyblog`;}）不属于任何表，一律忽略</li>
     *   <li>表内只有「行首就是反引号」的行才算列定义。这条规则把两类行自然排除掉：
     *       {@code PRIMARY KEY (`articleId`),} 行首是 PRIMARY；
     *       {@code KEY `idx_category_categoryName` (`categoryName`, `deleted`),} 行首是 KEY。
     *       索引名同样被反引号包着，所以绝不能用「抓出所有反引号串」这种粗暴做法 ——
     *       那会把 idx_category_categoryName 当成列名，两边一起错还互相抵消。</li>
     * </ol>
     */
    private static Map<String, List<String>> parseSchemaTables() throws IOException {
        String ddl = new ClassPathResource("db/schema.sql").getContentAsString(StandardCharsets.UTF_8);

        Map<String, List<String>> tables = new LinkedHashMap<>();
        String currentTable = null;
        for (String rawLine : ddl.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }

            Matcher createTable = CREATE_TABLE.matcher(line);
            if (createTable.find()) {
                currentTable = createTable.group(1);
                tables.put(currentTable, new ArrayList<>());
                continue;
            }

            if (line.startsWith(")")) {
                currentTable = null;
                continue;
            }

            if (currentTable == null) {
                continue;
            }

            Matcher column = COLUMN_DEF.matcher(line);
            if (column.find()) {
                tables.get(currentTable).add(column.group(1));
            }
        }
        return tables;
    }

    private record ExpectedEntity(String tableName, String keyColumn, List<String> fieldColumns) {
    }
}
