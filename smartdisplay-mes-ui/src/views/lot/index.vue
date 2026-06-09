<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">Lot 管理 / 状态机与流转控制</h1>
        <p class="page-desc">按 Lot 状态机执行 Track In、Track Out、Hold、Release、Rework 和 Scrap，所有敏感动作进入审计链路。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" :disabled="loading" @click="fetchLotList">{{ loading ? '刷新中' : '刷新' }}</button>
        <button v-if="hasAnyTrackIn" class="mes-btn primary" :disabled="!firstTrackInLot" @click="openTrackIn(firstTrackInLot)">Track In</button>
        <button v-if="hasAnyTrackOut" class="mes-btn primary" :disabled="!firstProcessingLot" @click="openTrackOut(firstProcessingLot)">Track Out</button>
        <button v-if="hasAnyHold" class="mes-btn warn" :disabled="!firstHoldableLot" @click="openHold(firstHoldableLot)">Hold</button>
      </div>
    </div>

    <div class="mes-grid cols-4">
      <div v-for="metric in statusMetrics" :key="metric.label" class="mes-card metric-card">
        <div class="metric-label"><span>{{ metric.label }}</span><span>{{ metric.note }}</span></div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-meta"><span>{{ metric.left }}</span><span>{{ metric.right }}</span></div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">Lot 队列</div>
        <span class="status-tag blue">{{ pagination.total }} 条</span>
      </div>
      <div class="mes-card__body">
        <div class="mes-filters">
          <div class="mes-field">
            <label>Lot 批次</label>
            <input v-model.trim="queryForm.lotNo" class="mes-input" placeholder="请输入 Lot" @keyup.enter="handleQuery" />
          </div>
          <div class="mes-field">
            <label>产品</label>
            <input v-model.trim="queryForm.productCode" class="mes-input" placeholder="AMOLED_65" @keyup.enter="handleQuery" />
          </div>
          <div class="mes-field">
            <label>状态</label>
            <select v-model="queryForm.status" class="mes-select">
              <option value="">全部状态</option>
              <option v-for="status in statusOptions" :key="status" :value="status">{{ status }}</option>
            </select>
          </div>
          <div class="mes-field">
            <label>工序</label>
            <select v-model="queryForm.stepCode" class="mes-select">
              <option value="">全部工序</option>
              <option v-for="step in stepOptions" :key="step" :value="step">{{ step }}</option>
            </select>
          </div>
          <button class="mes-btn primary" :disabled="loading" @click="handleQuery">
            {{ loading ? '查询中' : '查询' }}
          </button>
        </div>

        <table class="mes-table lot-table">
          <thead>
            <tr>
              <th>Lot</th>
              <th>产品</th>
              <th>工序</th>
              <th>设备</th>
              <th>数量</th>
              <th>状态</th>
              <th>Hold</th>
              <th>下一动作</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="lot in displayLots"
              :key="lot.lotNo"
              :class="{ danger: isHeld(lot), selected: selectedLot?.lotNo === lot.lotNo }"
              @click="selectedLot = lot"
            >
              <td>{{ lot.lotNo }}</td>
              <td>{{ lot.productCode || '-' }}</td>
              <td>{{ lot.currentStepCode || '-' }}</td>
              <td>{{ lot.currentEquipmentCode || '待分配' }}</td>
              <td>{{ lot.qty || 0 }}</td>
              <td><span class="status-tag" :class="statusType(lot.status)">{{ lot.status || '-' }}</span></td>
              <td>
                <span class="status-tag" :class="isHeld(lot) ? 'red' : 'green'">
                  {{ isHeld(lot) ? '已 Hold' : '正常' }}
                </span>
              </td>
              <td>{{ nextActionText(lot) }}</td>
              <td>
                <div class="row-actions">
                  <button class="mes-btn tiny primary" :disabled="!canTrackIn(lot)" @click.stop="openTrackIn(lot)">进站</button>
                  <button class="mes-btn tiny primary" :disabled="!canTrackOut(lot)" @click.stop="openTrackOut(lot)">出站</button>
                  <button class="mes-btn tiny warn" :disabled="!canHold(lot)" @click.stop="openHold(lot)">Hold</button>
                  <button class="mes-btn tiny" :disabled="!canRelease(lot)" @click.stop="openRelease(lot)">放行</button>
                  <button class="mes-btn tiny" :disabled="!canRework(lot)" @click.stop="openRework(lot)">返工</button>
                  <button class="mes-btn tiny warn" :disabled="!canScrap(lot)" @click.stop="openScrap(lot)">报废</button>
                </div>
              </td>
            </tr>
            <tr v-if="!displayLots.length">
              <td colspan="9">没有符合条件的 Lot</td>
            </tr>
          </tbody>
        </table>

        <div class="pager-row">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchLotList"
            @current-change="fetchLotList"
          />
        </div>
      </div>
    </div>

    <TrackInDialog v-model="trackInVisible" :lot-data="selectedLot" @success="handleOperationSuccess" />
    <TrackOutDialog v-model="trackOutVisible" :lot-data="selectedLot" @success="handleOperationSuccess" />
    <HoldDialog v-model="holdVisible" :lot-data="selectedLot" @success="handleOperationSuccess" />
    <ReleaseDialog v-model="releaseVisible" :lot-data="selectedLot" @success="handleOperationSuccess" />
    <ReworkDialog v-model="reworkVisible" :lot-data="selectedLot" @success="handleOperationSuccess" />
    <ScrapDialog v-model="scrapVisible" :lot-data="selectedLot" @success="handleOperationSuccess" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLotList } from '@/api/lot'
import { hasButton } from '@/utils/permissions'
import HoldDialog from './components/HoldDialog.vue'
import ReleaseDialog from './components/ReleaseDialog.vue'
import ReworkDialog from './components/ReworkDialog.vue'
import ScrapDialog from './components/ScrapDialog.vue'
import TrackInDialog from './components/TrackInDialog.vue'
import TrackOutDialog from './components/TrackOutDialog.vue'

const statusOptions = ['CREATED', 'READY', 'PROCESSING', 'HOLD', 'COMPLETED', 'REWORK', 'SCRAP']

const loading = ref(false)
const lotList = ref([])
const selectedLot = ref(null)
const trackInVisible = ref(false)
const trackOutVisible = ref(false)
const holdVisible = ref(false)
const releaseVisible = ref(false)
const reworkVisible = ref(false)
const scrapVisible = ref(false)

const queryForm = reactive({
  lotNo: '',
  productCode: '',
  status: '',
  stepCode: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const hasAnyTrackIn = computed(() => hasButton('lot:track-in'))
const hasAnyTrackOut = computed(() => hasButton('lot:track-out'))
const hasAnyHold = computed(() => hasButton('lot:hold'))
const stepOptions = computed(() => [...new Set(lotList.value.map(lot => lot.currentStepCode).filter(Boolean))])
const displayLots = computed(() => {
  const product = queryForm.productCode.trim().toLowerCase()
  const step = queryForm.stepCode
  return lotList.value.filter(lot => {
    const matchesProduct = !product || String(lot.productCode || '').toLowerCase().includes(product)
    const matchesStep = !step || lot.currentStepCode === step
    return matchesProduct && matchesStep
  })
})

const firstTrackInLot = computed(() => displayLots.value.find(canTrackIn))
const firstProcessingLot = computed(() => displayLots.value.find(canTrackOut))
const firstHoldableLot = computed(() => displayLots.value.find(canHold))

const statusMetrics = computed(() => {
  const rows = displayLots.value
  const count = status => rows.filter(lot => lot.status === status).length
  const held = rows.filter(isHeld).length
  return [
    { label: '可进站', value: count('READY') + count('REWORK'), note: 'READY/REWORK', left: '执行入口', right: '8 项校验' },
    { label: '加工中', value: count('PROCESSING'), note: 'PROCESSING', left: '待出站', right: '参数采集' },
    { label: 'Hold', value: held, note: '异常控制', left: '需 MRB', right: '禁止进站' },
    { label: '完成/报废', value: count('COMPLETED') + count('SCRAP'), note: '闭环结果', left: '追溯可查', right: '审计留痕' }
  ]
})

async function fetchLotList() {
  loading.value = true
  try {
    const params = {
      current: pagination.page,
      size: pagination.size
    }
    if (queryForm.lotNo) params.lotNo = queryForm.lotNo
    if (queryForm.status) params.status = queryForm.status
    const data = await getLotList(params)
    lotList.value = data?.records || []
    pagination.total = data?.total || 0
    if (!selectedLot.value || !lotList.value.some(lot => lot.lotNo === selectedLot.value?.lotNo)) {
      selectedLot.value = displayLots.value[0] || lotList.value[0] || null
    }
  } catch (error) {
    console.error('获取 Lot 列表失败:', error)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.page = 1
  fetchLotList()
}

function isHeld(lot) {
  return lot?.status === 'HOLD' || Number(lot?.holdFlag || 0) === 1
}

function statusType(status) {
  const typeMap = {
    CREATED: 'gray',
    READY: 'blue',
    PROCESSING: 'green',
    HOLD: 'red',
    COMPLETED: 'teal',
    REWORK: 'amber',
    SCRAP: 'red'
  }
  return typeMap[status] || 'gray'
}

function nextActionText(lot) {
  if (canTrackIn(lot)) return 'Track In'
  if (canTrackOut(lot)) return 'Track Out'
  if (canRelease(lot)) return 'Release / Rework / Scrap'
  if (lot?.status === 'COMPLETED') return '已完成'
  if (lot?.status === 'SCRAP') return '已报废'
  return '待校验'
}

function canTrackIn(row) {
  if (!row) return false
  return hasButton('lot:track-in') && ['READY', 'REWORK'].includes(row.status) && !isHeld(row)
}

function canTrackOut(lot) {
  return hasButton('lot:track-out') && lot?.status === 'PROCESSING'
}

function canHold(lot) {
  return hasButton('lot:hold') && ['READY', 'PROCESSING'].includes(lot?.status) && !isHeld(lot)
}

function canRelease(lot) {
  return hasButton('lot:release') && isHeld(lot)
}

function canRework(lot) {
  return hasButton('lot:rework') && isHeld(lot)
}

function canScrap(lot) {
  return hasButton('lot:scrap') && isHeld(lot)
}

function requireLot(lot) {
  if (!lot) {
    ElMessage.warning('当前没有可操作的 Lot')
    return false
  }
  selectedLot.value = lot
  return true
}

function openTrackIn(lot) {
  if (requireLot(lot)) trackInVisible.value = true
}

function openTrackOut(lot) {
  if (requireLot(lot)) trackOutVisible.value = true
}

function openHold(lot) {
  if (requireLot(lot)) holdVisible.value = true
}

function openRelease(lot) {
  if (requireLot(lot)) releaseVisible.value = true
}

function openRework(lot) {
  if (requireLot(lot)) reworkVisible.value = true
}

function openScrap(lot) {
  if (requireLot(lot)) scrapVisible.value = true
}

function handleOperationSuccess() {
  fetchLotList()
}

onMounted(fetchLotList)
</script>

<style scoped>
.lot-table th:nth-child(9),
.lot-table td:nth-child(9) {
  width: 360px;
}

.row-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pager-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.mes-table tbody tr {
  cursor: pointer;
}

.mes-table tbody tr.selected td {
  background: var(--mes-paper-muted);
  box-shadow: inset 0 1px 0 var(--mes-line-soft), inset 0 -1px 0 var(--mes-line-soft);
}
</style>
