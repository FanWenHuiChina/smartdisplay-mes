<template>
  <div class="mes-shell">
    <header class="mes-top">
      <div class="mes-brand">
        <div class="mes-logo">MES</div>
        <div class="mes-brand__text">
          <strong>SmartDisplay MES</strong>
          <span>AMOLED 面板 / 模组制造执行系统</span>
        </div>
      </div>

      <div class="mes-context">
        <span class="context-chip">基地：广州模组线</span>
        <span class="context-chip">产线：G6-FLEX-LINE-01</span>
        <span class="context-chip">班次：白班 08:00-20:00</span>
        <span class="context-chip">刷新：{{ refreshText }}</span>
      </div>

      <div class="mes-userbar">
        <button class="mes-btn" @click="goTo('/overview')">{{ alertButtonText }}</button>
        <button v-if="canMenu('system')" class="mes-btn" @click="goTo('/system')">审计</button>
        <button v-if="canButton('quality:mrb-review')" class="mes-btn primary" @click="goTo('/quality')">新建异常</button>
        <button class="mes-btn" @click="handleLogout">退出</button>
      </div>
    </header>

    <nav class="mes-tabs" aria-label="一级模块">
      <router-link
        v-for="item in visibleTopModules"
        :key="item.path"
        class="mes-tab"
        :class="{ active: isTopActive(item) }"
        :to="item.path"
      >
        {{ item.title }}
      </router-link>
    </nav>

    <div class="mes-workspace">
      <aside class="mes-side">
        <section v-for="group in visibleSideGroups" :key="group.title" class="side-section">
          <div class="side-title">{{ group.title }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.title"
            :to="item.path"
            class="side-link"
            :class="{ active: isSideActive(group, item) }"
          >
            <span>{{ item.title }}</span>
            <span v-if="item.badge" class="side-badge" :class="item.badgeType">{{ item.badge }}</span>
          </router-link>
        </section>
      </aside>

      <main class="mes-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOverview } from '@/api/pilot'
import { hasButton, hasMenu } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'

const route = useRoute()
const router = useRouter()

const topModules = [
  { title: '生产总览', path: '/overview', match: ['/overview'], menu: 'dashboard' },
  { title: '计划与工单', path: '/order', match: ['/order'], menu: 'order' },
  { title: '工艺主数据', path: '/master', match: ['/master', '/recipe'], menu: 'master' },
  { title: '生产执行', path: '/execution', match: ['/execution', '/lot'], menu: 'execution' },
  { title: '设备与自动化', path: '/equipment', match: ['/equipment'], menu: 'equipment' },
  { title: '质量管理', path: '/quality', match: ['/quality'], menu: 'quality' },
  { title: '物料与载具', path: '/material', match: ['/material'], menu: 'material' },
  { title: '追溯分析', path: '/trace', match: ['/trace'], menu: 'trace' },
  { title: '报表与AI', path: '/ai', match: ['/ai'], menu: 'ai' },
  { title: '系统管理', path: '/system', match: ['/system'], menu: 'system' }
]

const sideGroups = [
  {
    title: '生产总览',
    items: [
      { title: '产线总控', path: '/overview', menu: 'dashboard', badgeKey: 'lineControl', badgeType: 'green' },
      { title: 'WIP 分布', path: '/overview', menu: 'dashboard', badgeKey: 'wip' },
      { title: '异常队列', path: '/overview', menu: 'dashboard', badgeKey: 'alerts', badgeType: 'red' },
      { title: '瓶颈分析', path: '/overview', menu: 'dashboard', badgeKey: 'bottleneck', badgeType: 'amber' }
    ]
  },
  {
    title: '计划与工单',
    items: [
      { title: '工单池', path: '/order', menu: 'order' },
      { title: '释放与拆 Lot', path: '/order', menu: 'order' },
      { title: 'Hot Lot 管控', path: '/order', menu: 'order' },
      { title: '派工队列', path: '/order', menu: 'order' }
    ]
  },
  {
    title: '工艺主数据',
    items: [
      { title: '产品与BOM', path: '/master', menu: 'master' },
      { title: 'Route 工艺路线', path: '/master', menu: 'master' },
      { title: 'Recipe 与规格', path: '/master', menu: 'master' },
      { title: '设备能力矩阵', path: '/master', menu: 'master' }
    ]
  },
  {
    title: '生产执行',
    items: [
      { title: '电子流程卡', path: '/execution', menu: 'execution' },
      { title: 'Track In / Out', path: '/execution', menu: 'execution' },
      { title: 'Hold / Release', path: '/execution', menu: 'execution' },
      { title: 'Rework / Scrap', path: '/execution', menu: 'execution' }
    ]
  },
  {
    title: '质量与设备',
    items: [
      { title: 'EAP / Recipe下载', path: '/equipment', menu: 'equipment' },
      { title: 'SPC 与缺陷', path: '/quality', menu: 'quality' },
      { title: 'MRB 处置', path: '/quality', menu: 'quality' },
      { title: '物料批次与载具', path: '/material', menu: 'material' }
    ]
  }
]

const dashboardSummary = ref(null)
const lastRefreshAt = ref(new Date())
const fallbackDashboardBadges = {
  lineControl: '96.82%',
  wip: '128',
  alerts: '7',
  bottleneck: '2'
}

const dashboardBadges = computed(() => {
  const summary = dashboardSummary.value
  if (!summary) {
    return __DEV_MOCK_FALLBACK__ ? fallbackDashboardBadges : {}
  }

  const metrics = Array.isArray(summary.metrics) ? summary.metrics : []
  const alerts = Array.isArray(summary.alerts) ? summary.alerts : []
  const routeSteps = Array.isArray(summary.routeSteps) ? summary.routeSteps : []
  const bottleneckCount = routeSteps.filter(step => ['BOTTLENECK', 'ALARM'].includes(String(step.status || '').toUpperCase())).length

  return {
    lineControl: metricValue(metrics, '综合良率') || metricValue(metrics, '设备 OEE'),
    wip: metricValue(metrics, '今日投入 Lot'),
    alerts: alerts.length ? String(alerts.length) : metricValue(metrics, 'Hold 待处置'),
    bottleneck: bottleneckCount ? String(bottleneckCount) : ''
  }
})

const refreshText = computed(() => lastRefreshAt.value.toLocaleTimeString('zh-CN', { hour12: false }))
const alertButtonText = computed(() => {
  const count = dashboardBadges.value.alerts
  return count ? `消息 ${count}` : '消息'
})

const visibleTopModules = computed(() => topModules.filter(item => hasMenu(item.menu)))
const visibleSideGroups = computed(() => sideGroups
  .map(group => ({
    ...group,
    items: group.items
      .filter(item => hasMenu(item.menu))
      .map(applyNavigationBadge)
  }))
  .filter(group => group.items.length > 0))

const canMenu = hasMenu
const canButton = hasButton
const isTopActive = (item) => item.match.some((path) => route.path.startsWith(path))
const isSideActive = (group, item) => {
  const firstMatchedItem = group.items.find(candidate => candidate.path === route.path)
  return firstMatchedItem?.title === item.title
}

function applyNavigationBadge(item) {
  if (!item.badgeKey) return item
  const badge = dashboardBadges.value[item.badgeKey]
  return {
    ...item,
    badge,
    badgeType: item.badgeType
  }
}

function metricValue(metrics, label) {
  const metric = metrics.find(item => item.label === label)
  return metric?.value ? String(metric.value) : ''
}

async function loadNavigationSummary() {
  try {
    dashboardSummary.value = await getOverview()
    lastRefreshAt.value = new Date()
  } catch (error) {
    warnDevFallback('导航总览接口不可用', error)
    dashboardSummary.value = null
  }
}

const goTo = (path) => {
  router.push(path)
}

const handleLogout = () => {
  ElMessageBox.confirm('确认退出登录？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('realName')
    localStorage.removeItem('role')
    localStorage.removeItem('permissions')
    ElMessage.success('已退出登录')
    router.push('/login')
  }).catch(() => {})
}

onMounted(() => {
  loadNavigationSummary()
})
</script>

<style scoped>
.mes-shell {
  min-height: 100vh;
  display: grid;
  grid-template-rows: 54px 40px minmax(0, 1fr);
  background: var(--mes-page);
}

.mes-top {
  background: var(--mes-paper);
  border-bottom: 1px solid var(--mes-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  gap: 14px;
}

.mes-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 260px;
}

.mes-logo {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  border: 1px solid var(--mes-line);
  background: var(--mes-soft);
  display: grid;
  place-items: center;
  color: var(--mes-ink);
  font-weight: 680;
  font-size: 12px;
}

.mes-brand__text {
  display: grid;
  gap: 2px;
}

.mes-brand__text strong {
  font-size: 14px;
  line-height: 1.15;
  font-weight: 680;
}

.mes-brand__text span {
  color: var(--mes-sub);
  font-size: 12px;
}

.mes-context {
  display: flex;
  align-items: center;
  gap: 0;
  flex-wrap: wrap;
  min-width: 0;
  flex: 1;
  justify-content: center;
}

.context-chip {
  border: 0;
  background: transparent;
  border-radius: 0;
  padding: 0 9px;
  color: var(--mes-muted);
  font-size: 12px;
  white-space: nowrap;
}

.context-chip + .context-chip {
  border-left: 1px solid var(--mes-line-soft);
}

.mes-userbar {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.mes-tabs {
  height: 40px;
  background: var(--mes-paper-muted);
  border-bottom: 1px solid var(--mes-line);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  overflow-x: auto;
}

.mes-tab {
  height: 30px;
  border-radius: 6px;
  border: 1px solid transparent;
  color: var(--mes-sub);
  padding: 0 9px;
  display: inline-flex;
  align-items: center;
  text-decoration: none;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 560;
}

.mes-tab:hover {
  background: var(--mes-control-hover);
  color: var(--mes-ink);
}

.mes-tab.active {
  border-color: var(--mes-line);
  background: var(--mes-paper);
  color: var(--mes-ink);
  box-shadow: 0 1px 0 rgba(24, 24, 22, 0.02);
}

.mes-workspace {
  min-height: 0;
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
}

.mes-side {
  background: var(--mes-page);
  border-right: 1px solid var(--mes-line);
  padding: 12px 8px 16px;
  overflow: auto;
}

.side-section {
  margin-bottom: 16px;
}

.side-title {
  color: var(--mes-weak);
  font-size: 12px;
  font-weight: 600;
  padding: 0 9px 7px;
}

.side-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 32px;
  padding: 6px 9px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--mes-sub);
  text-decoration: none;
  cursor: pointer;
  font-size: 13px;
}

.side-link:hover {
  background: var(--mes-control-hover);
  color: var(--mes-ink);
}

.side-link.active {
  border-color: var(--mes-line);
  background: var(--mes-paper);
  color: var(--mes-ink);
  font-weight: 600;
  box-shadow: 0 1px 0 rgba(24, 24, 22, 0.02);
}

.side-badge {
  min-width: 20px;
  height: 20px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 7px;
  background: var(--mes-soft-2);
  color: var(--mes-sub);
  font-size: 12px;
  font-weight: 700;
}

.side-badge.red {
  background: var(--mes-red-soft);
  color: var(--mes-red);
}

.side-badge.amber {
  background: var(--mes-amber-soft);
  color: var(--mes-amber);
}

.side-badge.green {
  background: var(--mes-green-soft);
  color: var(--mes-green);
}

.mes-content {
  min-width: 0;
  height: calc(100vh - 94px);
  overflow: auto;
  padding: 16px;
}

@media (max-width: 1320px) {
  .mes-context {
    justify-content: flex-start;
    overflow: hidden;
  }

  .context-chip:nth-child(n + 3) {
    display: none;
  }
}

@media (max-width: 1180px) {
  .mes-shell {
    grid-template-rows: auto 42px minmax(0, 1fr);
  }

  .mes-top {
    min-height: 58px;
    align-items: flex-start;
    padding: 10px 14px;
    flex-wrap: wrap;
  }

  .mes-brand {
    min-width: 220px;
  }

  .mes-userbar {
    margin-left: auto;
  }

  .mes-workspace {
    grid-template-columns: 1fr;
  }

  .mes-side {
    display: none;
  }
}
</style>
