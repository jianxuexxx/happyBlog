<script setup lang="ts">
/**
 * 文章卡片。首页与分类页共用（设计规格 §2）——抽出来的理由是：卡片是最容易在两处
 * 复制后各自漂移的东西，而首页马上也要接真数据。
 * props 即后端 ArticleListVO 的六个字段，与 api/article.ts 的 ArticleListItem 一一对应。
 */
defineProps<{
  articleId: number
  title: string
  /** 摘要；后端未填时为 null，模板直接插值成空串 */
  summary: string | null
  /** 封面图路径；null 或空串时走 .img-placeholder 占位（主规格 §14），不报错 */
  coverImage: string | null
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
