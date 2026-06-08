import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../views/layout/MainLayout.vue'
import { firstAccessiblePath, hasMenu, menuForPath, setPreviewPermissions } from '@/utils/permissions'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue')
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/overview',
    children: [
      {
        path: 'overview',
        name: 'Overview',
        meta: { menu: 'dashboard' },
        component: () => import('../views/overview/index.vue')
      },
      {
        path: 'order',
        name: 'OrderRelease',
        meta: { menu: 'order' },
        component: () => import('../views/order/index.vue')
      },
      {
        path: 'master',
        name: 'MasterData',
        meta: { menu: 'master' },
        component: () => import('../views/master/index.vue')
      },
      {
        path: 'execution',
        name: 'Execution',
        meta: { menu: 'execution' },
        component: () => import('../views/execution/index.vue')
      },
      {
        path: 'equipment',
        name: 'EquipmentAutomation',
        meta: { menu: 'equipment' },
        component: () => import('../views/equipment/index.vue')
      },
      {
        path: 'quality',
        name: 'Quality',
        meta: { menu: 'quality' },
        component: () => import('../views/quality/index.vue')
      },
      {
        path: 'material',
        name: 'Material',
        meta: { menu: 'material' },
        component: () => import('../views/material/index.vue')
      },
      {
        path: 'trace',
        name: 'Trace',
        meta: { menu: 'trace' },
        component: () => import('../views/trace/index.vue')
      },
      {
        path: 'ai',
        name: 'AiReports',
        meta: { menu: 'ai' },
        component: () => import('../views/ai/index.vue')
      },
      {
        path: 'system',
        name: 'System',
        meta: { menu: 'system' },
        component: () => import('../views/system/index.vue')
      },
      {
        path: 'dashboard',
        redirect: '/overview'
      },
      {
        path: 'lot',
        redirect: '/execution'
      },
      {
        path: 'recipe',
        redirect: '/master'
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to) => {
  const isDevPreview = import.meta.env.DEV && to.query.preview === '1'

  if (isDevPreview && !localStorage.getItem('token')) {
    localStorage.setItem('token', 'dev-preview-token')
    localStorage.setItem('username', 'preview')
    localStorage.setItem('realName', '预览用户')
    localStorage.setItem('role', 'PREVIEW')
    setPreviewPermissions()
  }

  const token = localStorage.getItem('token')

  if (to.path === '/login') {
    return token ? '/overview' : true
  }

  if (!token) {
    return '/login'
  }

  const menuKey = to.meta.menu || menuForPath(to.path)
  if (menuKey && !hasMenu(menuKey)) {
    return firstAccessiblePath()
  }

  return true
})

export default router
