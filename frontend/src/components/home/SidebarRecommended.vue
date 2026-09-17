<script setup lang="ts">
// 左侧栏展位：先展示推荐文章
// 骨架占位：真实数据来自 GET /api/home/recommend（后端接入后替换）
const recommends = [1, 2, 3, 4, 5]
</script>

<template>
  <aside class="sidebar-recommended">
    <h3 class="sidebar-title">推荐文章</h3>
    <ul class="sidebar-list">
      <li v-for="n in recommends" :key="n" class="sidebar-item">
        <router-link to="/article/0" class="sidebar-link">
          <span class="sidebar-cover img-placeholder">封面</span>
          <span class="sidebar-text">
            <span class="sidebar-item-title">推荐文章标题占位 {{ n }}</span>
            <span class="num sidebar-date">2026-09-17</span>
          </span>
        </router-link>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.sidebar-recommended {
  background: var(--glass-bg);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-soft);
  padding: 16px;
}
.sidebar-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}
.sidebar-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
}
.sidebar-item {
  margin: 0;
}
.sidebar-link {
  display: flex;
  gap: 10px;
  align-items: center;
  text-decoration: none;
  color: var(--text-primary);
  border-radius: var(--radius-btn);
  padding: 6px;
  transition: background var(--duration-base) var(--spring-curve);
}
.sidebar-link:hover {
  background: var(--brand-accent-soft);
}
.sidebar-cover {
  width: 56px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 8px;
  font-size: 10px;
}
.sidebar-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.sidebar-item-title {
  font-size: 13px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sidebar-date {
  font-size: 11px;
  color: var(--text-muted);
}
</style>