import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

/**
 * 路由骨架 —— 任务 2 最小雏形（保证 main.ts 可编译）
 * 任务 5 将扩展完整路由表（前台 + admin 懒加载 + 守卫）。
 */
const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: () => import('../views/Home.vue') },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFound.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router