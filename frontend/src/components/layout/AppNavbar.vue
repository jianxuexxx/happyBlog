<script setup lang="ts">
import { useThemeStore } from '../../store/theme'
import { Moon, Sunny } from '@element-plus/icons-vue'

const theme = useThemeStore()

const menus = [
  { path: '/', label: '首页' },
  { path: '/archive', label: '归档' },
  { path: '/friends', label: '友链' },
  { path: '/about', label: '关于' },
]
</script>

<template>
  <header class="navbar">
    <router-link to="/" class="navbar-logo">
      <!-- 图片占位：用户提供 site-logo.png 后改用 <img src="/images/site-logo.png"> 同名替换 -->
      <span class="navbar-logo-img img-placeholder" aria-label="logo">H</span>
      <span class="navbar-logo-text">我的博客</span>
    </router-link>

    <nav class="navbar-menu">
      <router-link
        v-for="m in menus"
        :key="m.path"
        :to="m.path"
        class="navbar-link"
      >
        {{ m.label }}
      </router-link>
      <router-link to="/search" class="navbar-link">搜索</router-link>
    </nav>

    <div class="navbar-actions">
      <el-button
        circle
        :icon="theme.isDark ? Sunny : Moon"
        :title="theme.isDark ? '切换到亮色' : '切换到暗色'"
        @click="theme.toggle()"
      />
      <el-button text type="primary" @click="$router.push('/admin')">管理</el-button>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
  height: 64px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-bottom: 1px solid var(--glass-border);
}

.navbar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--text-primary);
}
.navbar-logo-img {
  width: 36px;
  height: 36px;
  border-radius: 10px;
}
.navbar-logo-text {
  font-weight: 600;
  font-size: 18px;
}

.navbar-menu {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.navbar-link {
  padding: 8px 14px;
  border-radius: var(--radius-btn);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  transition:
    background var(--duration-base) var(--spring-curve),
    color var(--duration-base) var(--spring-curve);
}
.navbar-link:hover {
  background: var(--brand-accent-soft);
  color: var(--brand-primary);
}
.router-link-active {
  background: var(--brand-primary);
  color: #fff;
}

.navbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>