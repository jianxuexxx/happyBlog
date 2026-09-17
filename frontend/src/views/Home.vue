<script setup lang="ts">
// 骨架数据占位：真实数据来自 GET /api/home/recommend 与 /api/article/list（后端接入后替换）
const recommendSkeletons = [1, 2, 3]
const articleSkeletons = [1, 2, 3, 4, 5, 6]
</script>

<template>
  <div class="home">
    <section class="hero">
      <div class="hero-inner">
        <h1 class="hero-title">我的博客</h1>
        <p class="hero-subtitle">记录生活的倒影与诗意的代码</p>
      </div>
    </section>

    <section class="recommend">
      <h2 class="section-title">推荐</h2>
      <div class="recommend-grid">
        <div
          v-for="n in recommendSkeletons"
          :key="n"
          class="rec-card img-placeholder"
          style="height: 160px"
        >
          推荐位 {{ n }}（图片占位）
        </div>
      </div>
    </section>

    <section class="article-list">
      <h2 class="section-title">最新</h2>
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
    </section>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  min-height: 280px;
  border-radius: calc(var(--radius-card) * 2);
  margin-bottom: 32px;
  overflow: hidden;
  /* hero-bg 占位：用户提供 hero-bg.jpg 后替换为 background-image 引用 */
  background: linear-gradient(
    135deg,
    var(--brand-primary-soft),
    var(--brand-secondary-soft)
  );
}
.hero-inner {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.hero-title {
  font-size: 38px;
  font-weight: 700;
  margin: 0;
}
.hero-subtitle {
  font-size: 16px;
  opacity: 0.92;
  margin: 12px 0 0;
}

.section-title {
  font-size: 22px;
  font-weight: 600;
  margin: 24px 0 16px;
}

.recommend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
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
</style>