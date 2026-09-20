-- 我的博客系统 建库建表脚本
-- 依据 docs/superpowers/specs/2026-09-20-backend-skeleton-design.md §7
-- 执行方式（在 mysql 客户端中）：source <此文件绝对路径>;
-- 或：mysql -u<user> -p < schema.sql
--
-- 约定：
--   1. 列名使用驼峰（与 Java 实体字段同名，免去映射配置）
--   2. 所有删除一律逻辑删除（deleted 0/1）；不建唯一索引——deleted 只有 0/1，
--      「列 + deleted」的组合唯一键只能容纳一行 deleted=1，撑不起删除历史。
--      唯一性由应用层查询保证（WHERE name = ? AND deleted = 0）；
--      下方保留同列的非唯一索引，仅供查询加速。删后同名可重建。
--   3. 不建物理外键：关系完整性由 Service 层保证，物理外键会阻碍同名重建
--   4. createdAt/updatedAt 的 DEFAULT / ON UPDATE 是兜底（手工 SQL 插入场景），
--      应用层由 MyBatis-Plus 的 MetaObjectHandler 填充

CREATE DATABASE IF NOT EXISTS `myblog`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `myblog`;

-- ---------------------------------------------------------------
-- article 文章表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `article` (
    `articleId`     BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键',
    `title`         VARCHAR(120) NOT NULL                          COMMENT '标题',
    `summary`       VARCHAR(300)          DEFAULT NULL              COMMENT '摘要',
    `content`       LONGTEXT              DEFAULT NULL              COMMENT 'Markdown 正文',
    `coverImage`    VARCHAR(255)          DEFAULT NULL              COMMENT '封面图 URL',
    `categoryId`    BIGINT                DEFAULT NULL              COMMENT '分类ID，删分类后置空',
    `status`        TINYINT      NOT NULL DEFAULT 0                COMMENT '0草稿/1公开/2私密',
    `isTop`         TINYINT      NOT NULL DEFAULT 0                COMMENT '置顶',
    `isRecommended` TINYINT      NOT NULL DEFAULT 0                COMMENT '首页推荐',
    `viewCount`     INT          NOT NULL DEFAULT 0                COMMENT '总浏览量',
    `createdAt`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0                COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`articleId`),
    KEY `idx_article_status` (`status`, `isTop`, `createdAt`),
    KEY `idx_article_categoryId` (`categoryId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章表';

-- ---------------------------------------------------------------
-- category 分类表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
    `categoryId`   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `categoryName` VARCHAR(50) NOT NULL                COMMENT '分类名',
    `sortOrder`    INT         NOT NULL DEFAULT 0      COMMENT '排序，升序',
    `createdAt`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`categoryId`),
    KEY `idx_category_categoryName` (`categoryName`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';

-- ---------------------------------------------------------------
-- tag 标签表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tag` (
    `tagId`     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tagName`   VARCHAR(50) NOT NULL                COMMENT '标签名',
    `createdAt` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`   TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`tagId`),
    KEY `idx_tag_tagName` (`tagName`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签表';

-- ---------------------------------------------------------------
-- articleTag 文章-标签关联表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `articleTag` (
    `id`        BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `articleId` BIGINT   NOT NULL                COMMENT '文章ID',
    `tagId`     BIGINT   NOT NULL                COMMENT '标签ID',
    `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`   TINYINT  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`id`),
    KEY `idx_articleTag_articleId_tagId` (`articleId`, `tagId`, `deleted`),
    KEY `idx_articleTag_tagId` (`tagId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章标签关联表';

-- ---------------------------------------------------------------
-- friendLink 友链表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `friendLink` (
    `friendLinkId` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`         VARCHAR(50)  NOT NULL                COMMENT '站点名',
    `url`          VARCHAR(255) NOT NULL                COMMENT '站点地址',
    `avatar`       VARCHAR(255)          DEFAULT NULL   COMMENT '头像 URL',
    `description`  VARCHAR(200)          DEFAULT NULL   COMMENT '简介',
    `sortOrder`    INT          NOT NULL DEFAULT 0      COMMENT '排序，升序',
    `createdAt`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`friendLinkId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='友情链接表';

-- ---------------------------------------------------------------
-- siteConfig 站点配置表（主键为业务键，无自增列）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `siteConfig` (
    `configKey`   VARCHAR(64) NOT NULL COMMENT '配置键',
    `configValue` TEXT                 COMMENT '配置值',
    `createdAt`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`configKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点配置表';

-- ---------------------------------------------------------------
-- notice 通知表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notice` (
    `noticeId`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`     VARCHAR(120) NOT NULL                COMMENT '标题',
    `content`   TEXT                                 COMMENT '内容',
    `startsAt`  DATETIME     NOT NULL                COMMENT '生效时间起',
    `endsAt`    DATETIME              DEFAULT NULL   COMMENT '生效时间止，空表示长期有效',
    `createdAt` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`noticeId`),
    KEY `idx_notice_startsAt_endsAt` (`startsAt`, `endsAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知表';
