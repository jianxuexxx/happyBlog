<script setup lang="ts">
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Autoplay, Pagination } from 'swiper/modules'
import 'swiper/css'
import 'swiper/css/pagination'

/**
 * 顶部大轮播图（v2 需求）
 * - 轮播图在 z 轴最底层，站点信息透明叠加在上方
 * - 高度按正常图片比例（16:9 观感）设计
 * - 正文板块（外层）通过负 margin 遮盖本组件底部，按钮在正文顶部
 */
const slides = [1, 2, 3]

const gradientSet = [
  'linear-gradient(135deg, var(--brand-primary-soft), var(--brand-secondary-soft))',
  'linear-gradient(135deg, var(--brand-secondary-soft), var(--brand-accent-soft))',
  'linear-gradient(135deg, var(--brand-accent-soft), var(--brand-primary-soft))',
]

/**
 * 轮播背景图（图片占位策略）：
 * 用变量绑定而非静态字符串，避免 rolldown 编译期解析不存在的文件而报错。
 * 用户提供 public/images/hero-bg.jpg 后即生效；未提供时 @error 隐藏，露出渐变占位。
 */
const HERO_BG = '/images/hero-bg.jpg'

/** 图片未提供时隐藏 img，露出渐变占位 */
function onImgError(e: Event) {
  ;(e.target as HTMLImageElement).style.display = 'none'
}
</script>

<template>
  <section class="carousel-hero">
    <!-- 轮播图：最底层 -->
    <Swiper
      class="carousel-hero-swiper"
      :modules="[Autoplay, Pagination]"
      :autoplay="{ delay: 4000, disableOnInteraction: false }"
      :pagination="{ clickable: true }"
      :loop="true"
    >
      <SwiperSlide v-for="n in slides" :key="n">
        <!-- 轮播图占位：数据接入后替换为封面图（图片占位策略） -->
        <div class="hero-slide" :style="{ background: gradientSet[n - 1] }">
          <!-- eslint-disable-next-line vue/no-unused-vars -->
          <img
            :src="HERO_BG"
            alt="banner"
            class="hero-slide-img"
            @error="onImgError"
          />
        </div>
      </SwiperSlide>
    </Swiper>

    <!-- 站点信息：透明背景叠在轮播上方 -->
    <div class="hero-info">
      <p class="hero-subtitle">记录生活的倒影与诗意的代码</p>
      <h1 class="hero-title">我的博客</h1>
    </div>
  </section>
</template>

<style scoped>
.carousel-hero {
  position: relative;
  height: clamp(420px, 54vh, 640px);
  border-radius: calc(var(--radius-card) * 2);
  overflow: hidden;
  box-shadow: var(--shadow-soft);
  background: var(--bg-color);
}
.carousel-hero-swiper,
.hero-slide {
  width: 100%;
  height: 100%;
}
.hero-slide {
  position: relative;
  border-radius: 0;
}
.hero-slide-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 站点信息：叠在轮播上方，背景透明，仅顶部留柔和渐变便于文字可读 */
.hero-info {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  color: #fff;
  padding: 0 24px;
  background: linear-gradient(
    180deg,
    rgba(21, 21, 28, 0.22),
    rgba(21, 21, 28, 0) 72%
  );
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
  pointer-events: none;
}
.hero-title {
  font-size: clamp(30px, 5vw, 46px);
  font-weight: 700;
  margin: 10px 0 0;
}
.hero-subtitle {
  font-size: 16px;
  opacity: 0.95;
  margin: 0;
}
</style>