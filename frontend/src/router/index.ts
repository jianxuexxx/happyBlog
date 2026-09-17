import {
  createRouter,
  createWebHistory,
  type NavigationGuard,
  type RouteRecordRaw,
} from 'vue-router'
import { getStorage } from '../utils/storage'

const ADMIN_TOKEN_KEY = 'blog-admin-token'

/** 路由表：前台 + /admin 管理端（懒加载） */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('../layouts/AppLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('../views/Home.vue') },
      {
        path: 'article/:articleId',
        name: 'article',
        component: () => import('../views/ArticleDetail.vue'),
      },
      {
        path: 'category/:id',
        name: 'category',
        component: () => import('../views/Category.vue'),
      },
      { path: 'tag/:id', name: 'tag', component: () => import('../views/Tag.vue') },
      {
        path: 'archive',
        name: 'archive',
        component: () => import('../views/Archive.vue'),
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('../views/Search.vue'),
      },
      {
        path: 'friends',
        name: 'friends',
        component: () => import('../views/Friends.vue'),
      },
      { path: 'about', name: 'about', component: () => import('../views/About.vue') },
    ],
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    children: [
      {
        path: 'login',
        name: 'admin-login',
        component: () => import('../views/admin/Login.vue'),
        meta: { guest: true },
      },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('../views/admin/Dashboard.vue'),
        meta: { requiresAuth: true },
      },
      // 管理端后续页面（文章/分类/标签/友链/配置）在对应任务中添加
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFound.vue'),
  },
]

/** 守卫：admin 页需登录；已登录访问 login 重定向到 dashboard */
const beforeEach: NavigationGuard = (to) => {
  const hasToken = !!getStorage<string>(ADMIN_TOKEN_KEY)

  if (to.meta.requiresAuth && !hasToken) {
    return { name: 'admin-login' }
  }
  if (to.meta.guest && hasToken) {
    return { name: 'admin-dashboard' }
  }
  return true
}

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes,
})

router.beforeEach(beforeEach)

export default router