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

<template>
  <div class="home">
    <!-- 顶部大轮播图（最底层） -->
    <CarouselHero />

    <!-- 正文板块：负 margin 遮盖轮播下部 -->
    <div class="home-body" :class="{ expanded: isExpanded }" :style="{ marginTop: isExpanded ? 0 : -COVER_OFFSET + 'px' }">
      <!-- 展开/收起按钮：位于图片下缘（正文顶部），下箭头图标 -->
      <div class="cover-toolbar">
        <!-- 毛玻璃透明圆按钮：外框透明（毛玻璃），内部为品牌蓝下箭头 -->
        <button
          type="button"
          class="cover-toggle"
          :class="{ 'is-up': isExpanded }"
          aria-label="查看全部图片"
          title="查看全部图片"
          @click="toggleExpand"
        >
          <span class="cover-toggle-icon"><ArrowDown /></span>
        </button>
      </div>

      <div class="home-grid">
        <!-- 左侧栏展位：分类（真实数据，接后端）+ 推荐文章（占位） -->
        <aside class="home-sidebar">
          <SidebarCategories />
          <SidebarRecommended />
        </aside>

        <!-- 主区：文章瀑布流 -->
        <section class="home-main">
          <h2 class="section-title">最新</h2>
          <div class="article-list">
            <ArticleCard v-for="item in articleSkeletons" :key="item.articleId" v-bind="item" />
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

/* 毛玻璃透明圆按钮：外框透明（毛玻璃磨砂），内部品牌蓝箭头 */
.cover-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  box-shadow: var(--shadow-soft);
  cursor: pointer;
  color: var(--brand-primary); /* 箭头颜色（svg 用 currentColor） */
  transition:
    box-shadow var(--duration-base) var(--spring-curve),
    transform var(--duration-base) var(--spring-curve),
    background-color var(--duration-base) var(--spring-curve);
}
.cover-toggle:hover {
  box-shadow: var(--shadow-hover);
  transform: scale(1.06);
}
.cover-toggle:focus-visible {
  outline: 2px solid var(--brand-primary-soft);
  outline-offset: 2px;
}
.cover-toggle-icon {
  display: inline-flex;
  font-size: 18px;
  color: var(--brand-primary); /* 箭头着色（svg 路径 fill=currentColor） */
  transition: rotate var(--duration-base) var(--spring-curve);
}
.cover-toggle-icon svg {
  /* icons-vue 的图标不带 width/height 属性，裸 svg 默认 0×0 不可见，必须显式定尺寸 */
  width: 1em;
  height: 1em;
}
/* 展开时箭头旋转朝上（收起内容） */
.cover-toggle.is-up .cover-toggle-icon {
  rotate: 180deg;
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
  /* 侧栏多张卡片纵向排布，间距走 8pt 栅格 */
  display: grid;
  gap: var(--space-2);
  align-content: start;
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