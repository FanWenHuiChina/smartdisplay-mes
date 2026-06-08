<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">生产执行 / 电子流程卡与 Track In/Out</h1>
        <p class="page-desc">每一次进站、出站、Move、Hold、Release、Rework、Scrap 都写入审计和追溯链路。</p>
      </div>
      <div class="page-actions">
        <button v-if="canHold" class="mes-btn" @click="holdSelectedLot">批量 Hold</button>
        <button class="mes-btn">打印流程卡</button>
        <button v-if="canTrackIn" class="mes-btn primary" @click="trackInSelectedLot">Track In</button>
        <button v-if="canTrackOut" class="mes-btn primary" @click="trackOutSelectedLot">Track Out</button>
      </div>
    </div>

    <div class="mes-card">
      <div class="mes-card__head">
        <div class="mes-card__title">Lot 执行队列</div>
        <span class="status-tag blue">当前工序 COATING</span>
      </div>
      <div class="mes-card__body">
        <div class="mes-filters">
          <div class="mes-field"><label>Lot / SN</label><input class="mes-input" placeholder="请输入 Lot / SN" /></div>
          <div class="mes-field"><label>工序</label><select class="mes-select"><option>全部工序</option></select></div>
          <div class="mes-field"><label>状态</label><select class="mes-select"><option>READY / PROCESSING / HOLD</option></select></div>
          <div class="mes-field"><label>设备</label><select class="mes-select"><option>全部设备</option></select></div>
          <button class="mes-btn primary">查询</button>
        </div>
        <table class="mes-table">
          <thead><tr><th>Lot</th><th>产品</th><th>Route</th><th>当前工序</th><th>设备</th><th>状态</th><th>等待</th><th>动作</th></tr></thead>
          <tbody>
            <tr
              v-for="lot in lotQueue"
              :key="lot.no"
              :class="{ danger: lot.status === 'HOLD', selected: selectedLot?.no === lot.no }"
              @click="selectLot(lot)"
            >
              <td>{{ lot.no }}</td><td>{{ lot.product }}</td><td>{{ lot.route }}</td><td>{{ lot.step }}</td><td>{{ lot.equipment }}</td>
              <td><span class="status-tag" :class="lot.statusType">{{ lot.status }}</span></td>
              <td>{{ lot.wait }}</td><td><span class="status-tag" :class="lot.actionType">{{ lot.action }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">Track In 校验链</div>
          <span class="status-tag green">8 项校验</span>
        </div>
        <div class="mes-card__body">
          <div class="matrix">
            <div v-for="item in checks" :key="item.title" class="check-cell" :class="item.type">
              <strong>{{ item.title }}</strong><span>{{ item.text }}</span>
            </div>
          </div>
          <div class="toolbar">
            <button v-if="canTrackIn" class="mes-btn primary" @click="trackInSelectedLot">确认 Track In</button>
            <button v-if="canTrackOut" class="mes-btn primary" @click="trackOutSelectedLot">确认 Track Out</button>
            <button class="mes-btn">切换设备</button>
            <button v-if="canHold" class="mes-btn warn" @click="holdSelectedLot">Hold 当前 Lot</button>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">电子流程卡</div>
          <span class="status-tag blue">{{ selectedLot?.no || '-' }}</span>
        </div>
        <div class="mes-card__body timeline">
          <div v-for="event in timeline" :key="event.title" class="timeline-event">
            <div class="event-dot" :class="event.type"></div>
            <div class="event-card">
              <div class="event-main"><span>{{ event.title }}</span><span>{{ event.time }}</span></div>
              <div class="event-meta">{{ event.meta }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLots, holdLot, trackInLot, trackOutLot } from '@/api/pilot'
import { hasButton } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'

const fallbackLotQueue = [
  { no: 'LOT260606-017', product: 'AMOLED_65', route: 'RTE_G6_V08', step: 'COATING', equipment: 'COATER_02', status: 'HOLD', statusType: 'red', wait: '38 min', action: 'Release 需质量工程师', actionType: 'red' },
  { no: 'LOT260606-018', product: 'AMOLED_65', route: 'RTE_G6_V08', step: 'COATING', equipment: '待分配', status: 'READY', statusType: 'blue', wait: '11 min', action: '可 Track In', actionType: 'green' },
  { no: 'LOT260606-019', product: 'AMOLED_67', route: 'RTE_G6_V05', step: 'EVAP', equipment: 'EVAP_01', status: 'PROCESSING', statusType: 'green', wait: '加工中', action: '可 Track Out', actionType: 'teal' },
  { no: 'LOT260606-020', product: 'AMOLED_65', route: 'RTE_G6_V08', step: 'AOI', equipment: 'INSPECT_03', status: 'PROCESSING', statusType: 'green', wait: '检测中', action: '录入缺陷', actionType: 'purple' }
]

const checks = __DEV_MOCK_FALLBACK__ ? [
  { title: 'Lot 状态', text: 'READY', type: 'green' },
  { title: 'Route 下一站', text: 'COATING 合法', type: 'green' },
  { title: '设备状态', text: 'IDLE', type: 'green' },
  { title: '设备能力', text: '支持涂胶', type: 'green' },
  { title: 'Recipe', text: 'RCP_COAT_V12', type: 'green' },
  { title: 'Hold', text: '未 Hold', type: 'green' },
  { title: '物料齐套', text: 'PI 胶 18%', type: 'amber' },
  { title: '权限', text: '操作员可执行', type: 'green' },
  { title: '班次', text: '白班', type: 'green' },
  { title: '审计', text: '将写入日志', type: 'blue' }
] : []

const timeline = __DEV_MOCK_FALLBACK__ ? [
  { title: '工单释放', time: '14:05', meta: 'MO20260606012 / 拆分 Lot / route=RTE_G6_V08', type: 'green' },
  { title: 'CLEAN 完成', time: '14:28', meta: 'EQ=CLEAN_01 / result=OK / operator=op1007', type: 'green' },
  { title: '等待 COATING', time: '当前', meta: '目标设备 COATER_01 / Recipe 已匹配 / 等待 11 分钟', type: 'amber' }
] : []

const lotQueue = ref(__DEV_MOCK_FALLBACK__ ? fallbackLotQueue : [])
const selectedLotNo = ref('')
const selectedLot = computed(() =>
  lotQueue.value.find(lot => lot.no === selectedLotNo.value)
  || lotQueue.value.find(lot => lot.status === 'READY')
  || lotQueue.value[0])
const canTrackIn = computed(() => hasButton('lot:track-in'))
const canTrackOut = computed(() => hasButton('lot:track-out'))
const canHold = computed(() => hasButton('lot:hold'))

const statusTypeMap = {
  CREATED: 'gray',
  READY: 'blue',
  PROCESSING: 'green',
  HOLD: 'red',
  COMPLETED: 'teal',
  REWORK: 'amber',
  SCRAP: 'red'
}

const actionMap = {
  READY: { text: '可 Track In', type: 'green' },
  PROCESSING: { text: '可 Track Out', type: 'teal' },
  HOLD: { text: 'Release 需质量工程师', type: 'red' },
  COMPLETED: { text: '已完成', type: 'blue' },
  REWORK: { text: '返工中', type: 'amber' },
  SCRAP: { text: '已报废', type: 'red' }
}

const equipmentByStep = {
  CLEAN: 'CLEANER_01',
  COATING: 'COATER_01',
  EXPOSURE: 'EXPOSURE_01',
  ETCH: 'ETCH_02',
  EVAPORATION: 'EVAP_01',
  ENCAPSULATION: 'ENCAP_01',
  INSPECTION: 'INSPECT_01',
  AGING: 'AGING_01'
}

function mapLot(lot) {
  const action = actionMap[lot.status] || { text: '待处理', type: 'gray' }
  return {
    no: lot.lotNo,
    product: lot.productCode,
    route: 'RTE_G6_V08',
    step: lot.currentStepCode,
    equipment: lot.currentEquipmentCode || '待分配',
    status: lot.status,
    statusType: statusTypeMap[lot.status] || 'gray',
    wait: lot.status === 'PROCESSING' ? '加工中' : '待流转',
    action: action.text,
    actionType: action.type
  }
}

function selectLot(lot) {
  selectedLotNo.value = lot?.no || ''
}

async function loadLots() {
  try {
    const data = await getLots({ current: 1, size: 20 })
    if (Array.isArray(data.records)) {
      lotQueue.value = data.records.map(mapLot)
      if (!lotQueue.value.some(lot => lot.no === selectedLotNo.value)) {
        selectedLotNo.value = lotQueue.value.find(lot => lot.status === 'READY')?.no || lotQueue.value[0]?.no || ''
      }
    }
  } catch (error) {
    warnDevFallback('执行队列接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) lotQueue.value = fallbackLotQueue
  }
}

async function trackInSelectedLot() {
  if (!canTrackIn.value) {
    ElMessage.warning('当前角色无权执行 Track In')
    return
  }
  const lot = selectedLot.value
  if (!lot || lot.status !== 'READY') {
    ElMessage.warning('请选择 READY 状态 Lot')
    return
  }
  try {
    await trackInLot(lot.no, {
      stepCode: lot.step,
      equipmentCode: equipmentByStep[lot.step] || 'COATER_01',
      operator: 'op1007'
    })
    ElMessage.success(`${lot.no} Track In 成功`)
    await loadLots()
  } catch (error) {
    console.warn('Track In 失败', error)
  }
}

async function trackOutSelectedLot() {
  if (!canTrackOut.value) {
    ElMessage.warning('当前角色无权执行 Track Out')
    return
  }
  const lot = selectedLot.value?.status === 'PROCESSING'
    ? selectedLot.value
    : lotQueue.value.find(item => item.status === 'PROCESSING')
  if (!lot) {
    ElMessage.warning('当前没有 PROCESSING 状态 Lot')
    return
  }
  try {
    await trackOutLot(lot.no, {
      result: 'OK',
      processParams: JSON.stringify({ temperature: 150, speed: 300, source: 'ui-demo' }),
      remark: '前端试点工作台出站'
    })
    ElMessage.success(`${lot.no} Track Out 成功`)
    await loadLots()
  } catch (error) {
    console.warn('Track Out 失败', error)
  }
}

async function holdSelectedLot() {
  if (!canHold.value) {
    ElMessage.warning('当前角色无权 Hold Lot')
    return
  }
  const lot = selectedLot.value
  if (!lot) return
  try {
    await holdLot(lot.no, { holdReason: '前端试点工作台手动 Hold', holdType: 'QUALITY', holdBy: 'qe1003' })
    ElMessage.success(`${lot.no} 已 Hold`)
    await loadLots()
  } catch (error) {
    console.warn('Hold 失败', error)
  }
}

onMounted(loadLots)
</script>

<style scoped>
.mes-table tbody tr {
  cursor: pointer;
}

.mes-table tbody tr.selected td {
  background: var(--mes-paper-muted);
  box-shadow: inset 0 1px 0 var(--mes-line-soft), inset 0 -1px 0 var(--mes-line-soft);
}
</style>
