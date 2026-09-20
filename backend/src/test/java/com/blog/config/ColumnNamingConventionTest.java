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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private record ExpectedEntity(String tableName, String keyColumn, List<String> fieldColumns) {
    }
}
