<script setup lang="ts">
import CarouselHero from '../components/home/CarouselHero.vue'
import SidebarRecommended from '../components/home/SidebarRecommended.vue'
import NoticeFloat from '../components/home/NoticeFloat.vue'

// 骨架数据占位：真实数据来自 GET /api/article/list（后端接入后替换）
const articleSkeletons = [1, 2, 3, 4, 5, 6]
</script>

<template>
  <div class="home">
    <!-- 顶部大轮播图（拖拽展开） -->
    <CarouselHero />

    <div class="home-body">
      <!-- 左侧栏展位：推荐文章 -->
      <aside class="home-sidebar">
        <SidebarRecommended />
      </aside>

      <!-- 主区：文章瀑布流 -->
      <section class="home-main">
        <h2 class="section-title">最新</h2>
        <div class="article-list">
          <article v-for="n in articleSkeletons" :key="n" class="article-card card">
            <div class="article-cover img-placeholder">封面占位</div>
            <div class="article-body">
              <h3>文章标题占位 {{ n }}</h3>
              <p class="article-summary">摘要占位：接入后端后展示真实摘要。</p>
              <div class="article-meta">
                <span class="tag-chip">标签</span>
                <span class="num">浏览 0 · 2026-09-17</span>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>

    <!-- 右侧悬浮通知 -->
    <NoticeFloat />
  </div>
</template>

<style scoped>
.home-body {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
  align-items: start;
}
.home-sidebar {
  position: sticky;
  top: 80px;
}

.section-title {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 16px;
}

.article-list {
  display: grid;
  gap: 16px;
}
.article-card {
  display: flex;
  gap: 16px;
  padding: 16px;
}
.article-cover {
  width: 200px;
  min-height: 120px;
  flex-shrink: 0;
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
.tag-chip {
  background: var(--brand-accent-soft);
  color: var(--brand-primary);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
}

/* 响应式：窄屏收起左侧栏 */
@media (max-width: 900px) {
  .home-body {
    grid-template-columns: 1fr;
  }
  .home-sidebar {
    position: static;
  }
}
</style>