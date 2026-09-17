<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getStorage, setStorage } from '../../utils/storage'

/**
 * 右侧悬浮通知（v2 需求）
 * 当天有新通知（notice 表）时弹悬浮框；当天只弹一次（localStorage 记录日期）。
 */

interface Notice {
  noticeId: number
  title: string
  content: string
}

const visible = ref(false)
const notice = ref<Notice | null>(null)
const NOTICE_SEEN_KEY = 'blog:notice-seen'

function today(): string {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

async function fetchTodayNotice(): Promise<Notice | null> {
  // 骨架占位：真实数据来自 GET /api/notice/today（后端接入后替换）
  // 返回 null 时不弹框；后端接入后此处替换为 http 请求
  return null
}

onMounted(async () => {
  const n = await fetchTodayNotice()
  if (!n) return
  const seenDate = getStorage<string>(NOTICE_SEEN_KEY)
  if (seenDate === today()) return
  notice.value = n
  visible.value = true
})

function close() {
  visible.value = false
  setStorage(NOTICE_SEEN_KEY, today())
}
</script>

<template>
  <Transition name="notice-fade">
    <div v-if="visible && notice" class="notice-float card">
      <div class="notice-header">
        <span class="notice-badge" />
        <strong class="notice-title">{{ notice.title }}</strong>
        <button class="notice-close" aria-label="关闭" @click="close">×</button>
      </div>
      <p class="notice-content">{{ notice.content }}</p>
    </div>
  </Transition>
</template>

<style scoped>
.notice-float {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 200;
  width: 300px;
  max-width: calc(100vw - 48px);
  padding: 16px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}
.notice-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.notice-badge {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand-secondary);
}
.notice-title {
  font-size: 14px;
}
.notice-close {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  border-radius: 6px;
  padding: 2px 6px;
  transition: background var(--duration-base) var(--spring-curve);
}
.notice-close:hover {
  background: var(--brand-accent-soft);
  color: var(--text-primary);
}
.notice-content {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
}
</style>

<style>
/* 悬浮通知进出场动画 */
.notice-fade-enter-active,
.notice-fade-leave-active {
  transition:
    opacity var(--duration-base) var(--spring-curve),
    transform var(--duration-base) var(--spring-curve);
}
.notice-fade-enter-from,
.notice-fade-leave-to {
  opacity: 0;
  transform: translateY(16px);
}
</style>