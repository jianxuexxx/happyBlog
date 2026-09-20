package com.blog.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.blog.entity.Category;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditMetaObjectHandlerTest {

    private final AuditMetaObjectHandler handler = new AuditMetaObjectHandler();

    /**
     * strictInsertFill/strictUpdateFill 会通过 TableInfoHelper 查 Category 的 TableInfo，
     * 借此确认字段确实标了 @TableField(fill=...)。真实运行时 TableInfo 由 Mapper 扫描注册，
     * 单元测试没有容器，故在此手动注册一次。不连数据库。
     */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Category.class);
    }

    @Test
    @DisplayName("插入时填充 createdAt、updatedAt 与 deleted=0")
    void insertFillFillsAuditFieldsAndDeletedFlag() {
        Category entity = new Category();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getDeleted()).isZero();
    }

    @Test
    @DisplayName("插入时不覆盖已显式设置的 deleted")
    void insertFillDoesNotOverrideExplicitDeleted() {
        Category entity = new Category();
        entity.setDeleted(1);
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.getDeleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("更新时只填 updatedAt，不动 createdAt 与 deleted")
    void updateFillOnlyFillsUpdatedAt() {
        Category entity = new Category();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.updateFill(metaObject);

        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getDeleted()).isNull();
    }
}
