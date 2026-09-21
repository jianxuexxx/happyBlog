<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArticleCard from '../components/ArticleCard.vue'
import { fetchArticleList, type ArticleListItem } from '../api/article'
import { fetchCategoryList } from '../api/category'

/** 每页条数固定，不提供选择器（与后端默认值一致，省掉一套要同步进 URL 的状态） */
const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const categoryId = computed(() => Number(route.params.id))

/**
 * URL 是页码的唯一真相。刻意不在组件里另存 currentPage —— 两份状态迟早不一致。
 * 防御性解析：?page= 是用户可手改的，NaN 或小于 1 一律当第 1 页，
 * 不把非法值发给后端（后端也会钳制，但前端不该依赖后端兜底来保证自己的控件不炸）。
 */
const page = computed(() => {
  const raw = Number(route.query.page)
  return Number.isInteger(raw) && raw >= 1 ? raw : 1
})

const list = ref<ArticleListItem[]>([])
const total = ref(0)
const loading = ref(true)
const errorMessage = ref('')
const categoryName = ref('')

async function loadArticles() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchArticleList({
      categoryId: categoryId.value,
      page: page.value,
      pageSize: PAGE_SIZE,
    })
    list.value = result.list
    total.value = result.total
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '未知错误'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 分类名与文章列表**刻意分开**、错误处理也刻意不同：分类名只是标题装饰，
 * 拉不到就退化成「分类」二字，不弹错误、不阻塞文章列表渲染（规格 §5）。
 */
async function loadCategoryName() {
  try {
    const categories = await fetchCategoryList()
    const hit = categories.find((c) => c.categoryId === categoryId.value)
    categoryName.value = hit?.categoryName ?? ''
  } catch {
    categoryName.value = ''
  }
}

/**
 * 必须 watch 而不是只在 onMounted 里取数：/category/1 → /category/2 命中的是同一个
 * 组件实例，Vue 会复用而不重建，onMounted 不会再跑第二次（规格 §5）。
 */
watch(
  [categoryId, page],
  () => {
    loadArticles()
    loadCategoryName()
  },
  { immediate: true },
)

/** 切分类时把页码从 URL 上摘掉，避免去请求新分类的第 N 页（很可能直接空列表）。 */
watch(categoryId, () => {
  if (route.query.page !== undefined) {
    router.push({ query: {} })
  }
})

function onPageChange(next: number) {
  // 回第 1 页时删掉该参数，得到干净的 /category/3 而不是 /category/3?page=1
  if (next <= 1) {
    router.push({ query: {} })
  } else {
    router.push({ query: { page: String(next) } })
  }
}
</script>

<template>
  <div class="category-page">
    <header class="category-header">
      <h1 class="category-title">{{ categoryName || '分类' }}</h1>
      <p v-if="!loading && !errorMessage" class="category-count num">共 {{ total }} 篇</p>
    </header>

    <p v-if="loading" class="category-hint">加载中…</p>

    <div v-else-if="errorMessage" class="category-error">
      <p class="category-hint">加载失败：{{ errorMessage }}</p>
      <button type="button" class="category-retry" @click="loadArticles">重试</button>
    </div>

    <p v-else-if="list.length === 0" class="category-hint">暂无文章</p>

    <template v-else>
      <div class="article-list">
        <ArticleCard v-for="item in list" :key="item.articleId" v-bind="item" />
      </div>

      <el-pagination
        v-if="total > 0"
        class="category-pagination"
        layout="prev, pager, next"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="page"
        @current-change="onPageChange"
      />
    </template>
  </div>
</template>

<style scoped>
.category-page {
  padding: var(--space-3) 0 var(--space-5);
}
.category-header {
  margin-bottom: var(--space-3);
}
.category-title {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 600;
}
.category-count {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}
.category-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}
.category-error {
  display: grid;
  gap: 8px;
  justify-items: start;
}
.category-retry {
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
.category-retry:hover {
  background: var(--brand-accent-soft);
  box-shadow: var(--shadow-soft);
}
.article-list {
  display: grid;
  gap: 16px;
}
.category-pagination {
  margin-top: var(--space-3);
  justify-content: center;
}
</style>
