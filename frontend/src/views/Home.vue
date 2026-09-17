<script setup lang="ts">
import { ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import CarouselHero from '../components/home/CarouselHero.vue'
import SidebarRecommended from '../components/home/SidebarRecommended.vue'
import NoticeFloat from '../components/home/NoticeFloat.vue'

// 骨架数据占位：真实数据来自 GET /api/article/list（后端接入后替换）
const articleSkeletons = [1, 2, 3, 4, 5, 6]

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

<template>
  <div class="home">
    <!-- 顶部大轮播图（最底层） -->
    <CarouselHero />

    <!-- 正文板块：负 margin 遮盖轮播下部 -->
    <div class="home-body" :class="{ expanded: isExpanded }" :style="{ marginTop: isExpanded ? 0 : -COVER_OFFSET + 'px' }">
      <!-- 展开/收起按钮：位于图片下缘（正文顶部），下箭头图标 -->
      <div class="cover-toolbar">
        <el-button
          circle
          :type="isExpanded ? 'default' : 'primary'"
          :icon="ArrowDown"
          :class="{ 'is-up': isExpanded }"
          aria-label="查看全部图片"
          title="查看全部图片"
          @click="toggleExpand"
        />
      </div>

      <div class="home-grid">
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
    </div>

    <!-- 悬浮通知（右上角） -->
    <NoticeFloat />
  </div>
</template>

<style scoped>
.home-body {
  position: relative;
  z-index: 10; /* 盖在轮播之上 */
  border-radius: calc(var(--radius-card) * 2);
  background: var(--bg-gradient);
  background-attachment: scroll;
  padding-top: 12px;
  transition: margin-top var(--duration-base) var(--spring-curve);
}

.cover-toolbar {
  display: flex;
  justify-content: center;
  padding: 4px 0 12px;
}

/* 展开时箭头旋转朝上（收起内容） */
.cover-toolbar :deep(.is-up .el-icon) {
  rotate: 180deg;
  transition: rotate var(--duration-base) var(--spring-curve);
}
.cover-toolbar :deep(.el-icon) {
  transition: rotate var(--duration-base) var(--spring-curve);
}

.home-grid {
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
  .home-grid {
    grid-template-columns: 1fr;
  }
  .home-sidebar {
    position: static;
  }
}
</style>