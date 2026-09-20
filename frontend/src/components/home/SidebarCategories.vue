<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchCategoryList, type CategoryItem } from '../../api/category'

// 左侧栏展位：真实数据来自 GET /api/category/list（后端已就绪）
const list = ref<CategoryItem[]>([])
const loading = ref(true)
const errorMessage = ref('')

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    list.value = await fetchCategoryList()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '未知错误'
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <nav class="sidebar-categories" aria-label="分类">
    <h3 class="sidebar-title">分类</h3>

    <p v-if="loading" class="sidebar-hint">加载中…</p>

    <div v-else-if="errorMessage" class="sidebar-error">
      <p class="sidebar-hint">加载失败：{{ errorMessage }}</p>
      <button type="button" class="sidebar-retry" @click="load">重试</button>
    </div>

    <p v-else-if="list.length === 0" class="sidebar-hint">暂无分类</p>

    <ul v-else class="sidebar-list">
      <li v-for="item in list" :key="item.categoryId" class="sidebar-item">
        <router-link :to="`/category/${item.categoryId}`" class="category-link">
          <span class="category-name">{{ item.categoryName }}</span>
          <span class="num category-count">{{ item.articleCount }}</span>
        </router-link>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.sidebar-categories {
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
.sidebar-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}
.sidebar-error {
  display: grid;
  gap: 8px;
  justify-items: start;
}
.sidebar-retry {
  border: 1px solid var(--brand-primary-soft);
  background: transparent;
  color: var(--brand-primary);
  border-radius: var(--radius-btn);
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  transition:
    background-color var(--duration-base) var(--spring-curve),
    box-shadow var(--duration-base) var(--spring-curve);
}
.sidebar-retry:hover {
  background: var(--brand-accent-soft);
  box-shadow: var(--shadow-soft);
}
.sidebar-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 4px;
}
.sidebar-item {
  margin: 0;
}
.category-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 8px;
  border-radius: var(--radius-btn);
  text-decoration: none;
  color: var(--text-primary);
  font-size: 13px;
  transition: background var(--duration-base) var(--spring-curve);
}
.category-link:hover {
  background: var(--brand-accent-soft);
}
.category-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.category-count {
  flex-shrink: 0;
  min-width: 22px;
  text-align: center;
  padding: 1px 8px;
  border-radius: 999px;
  /* 底色与字色都取「不随亮暗翻转」的品牌令牌（--brand-* 在 :root.dark 里未被覆盖），
     与 Home.vue 的 .tag-chip 同款。别改用 --text-secondary/--text-primary：它们会翻成浅色，
     压在这块浅底上对比度掉到 1.13/1.03，暗色下等于看不见（浏览器实测值）。 */
  background: var(--brand-accent-soft);
  color: var(--brand-primary);
  font-size: 11px;
}
</style>
