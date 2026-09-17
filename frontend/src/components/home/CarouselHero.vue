<script setup lang="ts">
import { ref } from 'vue'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Autoplay, Pagination } from 'swiper/modules'
import 'swiper/css'
import 'swiper/css/pagination'

/**
 * 顶部大轮播图（v2 需求）
 * 交互：默认露出顶部一部分高度，向下拖动展开至全高，向上滑收起。
 */
const MIN_HEIGHT = 140
const MAX_HEIGHT = 420

const containerHeight = ref(MIN_HEIGHT)
const isDragging = ref(false)
let startY = 0
let startHeight = 0

function onDragStart(e: MouseEvent | TouchEvent) {
  isDragging.value = true
  startY = 'touches' in e ? e.touches[0].clientY : e.clientY
  startHeight = containerHeight.value
}

function onDragMove(e: MouseEvent | TouchEvent) {
  if (!isDragging.value) return
  const y = 'touches' in e ? e.touches[0].clientY : e.clientY
  // 向下拖（deltaY > 0）→ 展开；向上拖 → 收起
  const delta = y - startY
  containerHeight.value = Math.min(
    MAX_HEIGHT,
    Math.max(MIN_HEIGHT, startHeight + delta),
  )
}

function onDragEnd() {
  isDragging.value = false
}

// 骨架占位轮播数据：真实数据来自 GET /api/home/recommend（后端接入后替换）
const slides = [1, 2, 3]

const gradientSet = [
  'linear-gradient(135deg, var(--brand-primary-soft), var(--brand-secondary-soft))',
  'linear-gradient(135deg, var(--brand-secondary-soft), var(--brand-accent-soft))',
  'linear-gradient(135deg, var(--brand-accent-soft), var(--brand-primary-soft))',
]
</script>

<template>
  <div
    class="carousel-hero"
    :style="{ height: containerHeight + 'px' }"
    @mousedown="onDragStart"
    @mousemove="onDragMove"
    @mouseup="onDragEnd"
    @mouseleave="onDragEnd"
    @touchstart.passive="onDragStart"
    @touchmove.passive="onDragMove"
    @touchend="onDragEnd"
  >
    <Swiper
      class="carousel-hero-swiper"
      :modules="[Autoplay, Pagination]"
      :autoplay="{ delay: 4000, disableOnInteraction: false }"
      :pagination="{ clickable: true }"
      :loop="true"
    >
      <SwiperSlide v-for="n in slides" :key="n">
        <!-- 轮播图占位：数据接入后替换为封面图/标题/链接。图片占位策略 -->
        <div class="hero-slide img-placeholder" :style="{ background: gradientSet[n - 1] }">
          <h2 class="hero-slide-title">推荐位 {{ n }}（图片占位）</h2>
        </div>
      </SwiperSlide>
    </Swiper>

    <div class="carousel-handle" aria-hidden="true">
      <span class="carousel-handle-bar" />
    </div>
  </div>
</template>

<style scoped>
.carousel-hero {
  position: relative;
  overflow: hidden;
  border-radius: calc(var(--radius-card) * 2);
  background: var(--bg-color);
  box-shadow: var(--shadow-soft);
  cursor: ns-resize;
  touch-action: pan-y;
  user-select: none;
  transition: height 120ms var(--spring-curve);
  margin-bottom: 24px;
}
.carousel-hero-swiper {
  height: 100%;
}
.hero-slide {
  width: 100%;
  height: 100%;
  border-radius: 0;
}
.hero-slide-title {
  color: #fff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  margin: 0;
}
.carousel-handle {
  position: absolute;
  left: 50%;
  bottom: 8px;
  transform: translateX(-50%);
  display: flex;
  justify-content: center;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: 999px;
  padding: 4px 16px;
  pointer-events: none;
}
.carousel-handle-bar {
  width: 40px;
  height: 4px;
  border-radius: 999px;
  background: var(--text-muted);
}
</style>