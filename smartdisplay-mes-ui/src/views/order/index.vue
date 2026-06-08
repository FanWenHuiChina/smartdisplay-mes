<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">计划与工单 / 工单释放与 Lot 生成</h1>
        <p class="page-desc">将 ERP/APS 下发的生产任务转换为 MES 可执行 Lot，并绑定产品、路线版本、优先级和计划窗口。</p>
      </div>
      <div class="page-actions">
        <button v-if="canCreateOrder" class="mes-btn" :disabled="importing" @click="submitErpImport">ERP 下发</button>
        <button class="mes-btn" :disabled="loading" @click="refreshOrderPage">刷新工单池</button>
        <button v-if="canReleaseOrder" class="mes-btn primary" :disabled="releasing" @click="releaseFirstOrder">释放工单</button>
      </div>
    </div>

    <div class="mes-grid cols-2-wide">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">工单池</div>
          <span class="status-tag blue">待释放 {{ pendingCount }}</span>
        </div>
        <div class="mes-card__body">
          <div class="mes-filters">
            <div class="mes-field"><label>工单 / 产品</label><input class="mes-input" placeholder="请输入工单或产品" /></div>
            <div class="mes-field"><label>状态</label><select class="mes-select"><option>待释放 / 已释放 / Hold</option></select></div>
            <div class="mes-field"><label>路线版本</label><select class="mes-select"><option>全部</option></select></div>
            <div class="mes-field"><label>优先级</label><select class="mes-select"><option>全部</option><option>Hot Lot</option></select></div>
            <button class="mes-btn primary">查询</button>
          </div>
          <table class="mes-table">
            <thead><tr><th>工单</th><th>产品</th><th>计划数</th><th>Route</th><th>优先级</th><th>状态</th><th>计划窗口</th></tr></thead>
            <tbody>
              <tr v-for="order in orders" :key="order.no" :class="{ hot: order.hot }">
                <td>{{ order.no }}</td><td>{{ order.product }}</td><td>{{ order.qty }}</td><td>{{ order.route }}</td>
                <td><span v-if="order.hot" class="status-tag orange">Hot</span><span v-else>普通</span></td>
                <td><span class="status-tag" :class="order.statusType">{{ order.status }}</span></td><td>{{ order.window }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">释放校验</div>
          <span class="status-tag green">7/8 通过</span>
        </div>
        <div class="mes-card__body">
          <div class="matrix two">
            <div v-for="item in releaseChecks" :key="item.title" class="check-cell" :class="item.type">
              <strong>{{ item.title }}</strong><span>{{ item.text }}</span>
            </div>
          </div>
          <div class="mes-filters section-gap">
            <div class="mes-field">
              <label>ERP 数量</label>
              <input v-model.number="erpImportForm.count" class="mes-input" type="number" min="1" max="20" />
            </div>
            <div class="mes-field">
              <label>产品</label>
              <select v-model="erpImportForm.productCode" class="mes-select">
                <option value="AMOLED_65">AMOLED_65</option>
                <option value="AMOLED_67">AMOLED_67</option>
                <option value="FOLD_78">FOLD_78</option>
              </select>
            </div>
            <div class="mes-field">
              <label>计划数</label>
              <input v-model.number="erpImportForm.plannedQty" class="mes-input" type="number" min="1" />
            </div>
            <div class="mes-field">
              <label>优先级</label>
              <select v-model.number="erpImportForm.priority" class="mes-select">
                <option :value="0">普通</option>
                <option :value="5">Hot Lot</option>
              </select>
            </div>
            <button v-if="canCreateOrder" class="mes-btn primary" :disabled="importing" @click="submitErpImport">
              {{ importing ? '下发中' : '下发 ERP 工单' }}
            </button>
          </div>
          <div v-if="erpImportResult" class="check-cell blue section-gap">
            <strong>最近下发</strong>
            <span>{{ erpImportSummary }}</span>
          </div>
          <div class="toolbar">
            <button v-if="canReleaseOrder" class="mes-btn primary" :disabled="releasing" @click="releaseFirstOrder">
              {{ releasing ? '释放中' : `生成 ${previewLotCount} 个 Lot` }}
            </button>
            <button class="mes-btn" :disabled="loading" @click="loadLots">刷新 Lot 预览</button>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">待生成 Lot 预览</div>
        <span class="status-tag teal">按 100 Panel / Lot</span>
      </div>
      <div class="mes-card__body">
        <table class="mes-table">
          <thead><tr><th>Lot</th><th>产品</th><th>数量</th><th>起始工序</th><th>路线</th><th>优先级</th><th>计划进站</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="lot in lots" :key="lot.no">
              <td>{{ lot.no }}</td><td>{{ lot.product }}</td><td>{{ lot.qty }}</td><td>{{ lot.step }}</td><td>{{ lot.route }}</td>
              <td>{{ lot.priority }}</td><td>{{ lot.plan }}</td><td><span class="status-tag blue">预生成</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOrders, getLots, importErpOrders, releaseOrder } from '@/api/pilot'
import { hasButton } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'

const fallbackOrders = [
  { no: 'MO20260606012', product: 'AMOLED_65', qty: '1,000', route: 'RTE_G6_V08', hot: true, status: '待释放', statusType: 'blue', window: '06/06 14:00-20:00' },
  { no: 'MO20260606013', product: 'AMOLED_67', qty: '800', route: 'RTE_G6_V05', status: '已释放', statusType: 'green', window: '06/06 18:00-06/07 02:00' },
  { no: 'MO20260606014', product: 'FOLD_78', qty: '600', route: 'RTE_MOD_V04', status: '待齐套', statusType: 'amber', window: '06/07 08:00-16:00' },
  { no: 'MO20260606015', product: 'AMOLED_65', qty: '1,200', route: 'RTE_G6_V08', status: '计划', statusType: 'gray', window: '06/07 16:00-06/08 04:00' }
]

const releaseChecks = [
  { title: '产品状态', text: 'AMOLED_65 已启用', type: 'green' },
  { title: 'Route 版本', text: 'RTE_G6_V08 已生效', type: 'green' },
  { title: 'BOM', text: 'BOM_65_V06 已生效', type: 'green' },
  { title: '物料齐套', text: 'PI 胶余量 18%', type: 'amber' },
  { title: 'Recipe 覆盖', text: '关键工序 100%', type: 'green' },
  { title: '设备能力', text: '目标线可执行', type: 'green' },
  { title: '工单数量', text: '拆 10 个 Lot', type: 'green' },
  { title: '权限审计', text: '计划员可释放', type: 'green' }
]

const fallbackLots = [
  { no: 'LOT260606-021', product: 'AMOLED_65', qty: 100, step: 'CLEAN', route: 'RTE_G6_V08', priority: 'Hot', plan: '14:30' },
  { no: 'LOT260606-022', product: 'AMOLED_65', qty: 100, step: 'CLEAN', route: 'RTE_G6_V08', priority: 'Hot', plan: '14:40' },
  { no: 'LOT260606-023', product: 'AMOLED_65', qty: 100, step: 'CLEAN', route: 'RTE_G6_V08', priority: '普通', plan: '15:00' }
]

const orders = ref(__DEV_MOCK_FALLBACK__ ? fallbackOrders : [])
const lots = ref(__DEV_MOCK_FALLBACK__ ? fallbackLots : [])
const loading = ref(false)
const importing = ref(false)
const releasing = ref(false)
const erpImportResult = ref(null)
const erpImportForm = ref({
  count: 3,
  productCode: 'AMOLED_65',
  plannedQty: 100,
  priority: 0,
  lineCode: 'LINE_01'
})

const pendingCount = computed(() => orders.value.filter(order => ['CREATED', '待释放', '计划'].includes(order.rawStatus || order.status)).length)
const previewLotCount = computed(() => lots.value.length || 10)
const canCreateOrder = computed(() => hasButton('order:create'))
const canReleaseOrder = computed(() => hasButton('order:release'))
const erpImportSummary = computed(() => {
  if (!erpImportResult.value) return ''
  const result = erpImportResult.value
  const sample = Array.isArray(result.sampleOrderNos) && result.sampleOrderNos.length
    ? `，样例 ${result.sampleOrderNos.slice(0, 3).join(' / ')}`
    : ''
  return `${result.batchNo || 'ERP批次'}：接收 ${result.receivedCount || 0}，创建 ${result.createdCount || 0}，跳过 ${result.skippedCount || 0}${sample}`
})

const statusMap = {
  CREATED: { label: '待释放', type: 'blue' },
  RELEASED: { label: '已释放', type: 'green' },
  HOLD: { label: 'Hold', type: 'red' },
  COMPLETED: { label: '完成', type: 'teal' }
}

function formatQty(value) {
  return Number(value || 0).toLocaleString()
}

function mapOrder(order) {
  const status = statusMap[order.status] || { label: order.status || '计划', type: 'gray' }
  return {
    no: order.orderNo,
    product: order.productCode,
    qty: formatQty(order.plannedQty),
    route: order.routeId ? `RTE-${order.routeId}` : 'RTE_G6_V08',
    hot: Number(order.priority || 0) > 0,
    status: status.label,
    rawStatus: order.status,
    statusType: status.type,
    window: order.startTime ? order.startTime : '未排定'
  }
}

function mapLot(lot) {
  return {
    no: lot.lotNo,
    product: lot.productCode,
    qty: lot.qty,
    step: lot.currentStepCode,
    route: 'RTE_G6_V08',
    priority: Number(lot.priority || 0) > 0 ? 'Hot' : '普通',
    plan: lot.createdTime ? String(lot.createdTime).slice(11, 16) : '待定'
  }
}

async function loadOrders() {
  try {
    const data = await getOrders({ current: 1, size: 20 })
    if (Array.isArray(data.records)) {
      orders.value = data.records.map(mapOrder)
    }
  } catch (error) {
    warnDevFallback('工单接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) orders.value = fallbackOrders
  }
}

async function loadLots() {
  try {
    const data = await getLots({ current: 1, size: 10 })
    if (Array.isArray(data.records)) {
      lots.value = data.records.slice(0, 5).map(mapLot)
    }
  } catch (error) {
    warnDevFallback('Lot 接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) lots.value = fallbackLots
  }
}

async function refreshOrderPage() {
  loading.value = true
  try {
    await Promise.all([loadOrders(), loadLots()])
  } finally {
    loading.value = false
  }
}

function clampImportCount(value) {
  const count = Number(value || 1)
  return Math.min(Math.max(Number.isFinite(count) ? count : 1, 1), 20)
}

async function submitErpImport() {
  if (!canCreateOrder.value) {
    ElMessage.warning('当前角色无权下发 ERP 工单')
    return
  }
  importing.value = true
  try {
    const count = clampImportCount(erpImportForm.value.count)
    erpImportForm.value.count = count
    const timestamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
    const payload = {
      sourceSystem: 'erp-adapter',
      batchNo: `ERP-UI-${timestamp}`,
      orderPrefix: `MOUI${timestamp}`,
      count,
      productCode: erpImportForm.value.productCode,
      plannedQty: Number(erpImportForm.value.plannedQty || 100),
      priority: Number(erpImportForm.value.priority || 0),
      lineCode: erpImportForm.value.lineCode || 'LINE_01'
    }
    erpImportResult.value = await importErpOrders(payload)
    ElMessage.success(`ERP 已下发 ${erpImportResult.value.createdCount || 0} 张工单`)
    await loadOrders()
  } catch (error) {
    console.warn('ERP 工单下发失败', error)
  } finally {
    importing.value = false
  }
}

async function releaseFirstOrder() {
  if (!canReleaseOrder.value) {
    ElMessage.warning('当前角色无权释放工单')
    return
  }
  const target = orders.value.find(order => order.rawStatus === 'CREATED' || order.status === '待释放')
  if (!target) {
    ElMessage.warning('当前没有可释放工单')
    return
  }
  releasing.value = true
  try {
    await releaseOrder(target.no, { lotQty: 100 })
    ElMessage.success(`${target.no} 已释放并生成 Lot`)
    await Promise.all([loadOrders(), loadLots()])
  } catch (error) {
    console.warn('工单释放失败', error)
  } finally {
    releasing.value = false
  }
}

onMounted(() => {
  loadOrders()
  loadLots()
})
</script>
