<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">工艺主数据 / Route、BOM、Recipe 与规格</h1>
        <p class="page-desc">主数据决定执行规则，版本、生效状态、审批与追溯快照是商用 MES 的关键。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" :disabled="!versionCompareRows.length" @click="compareMasterVersions">版本对比</button>
        <button v-if="canBomChange" class="mes-btn" :disabled="bomActionLoading" @click="createBomChange">ECO 审批</button>
        <button
          v-if="canPublishRecipe"
          class="mes-btn primary"
          :disabled="recipeActionLoading || !firstPublishableRecipe"
          @click="publishFirstRecipe"
        >发布版本</button>
      </div>
    </div>

    <div v-if="showVersionCompare && versionCompareRows.length" class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">版本对比快照</div>
        <span class="status-tag green">{{ versionCompareRows.length }} 项差异</span>
      </div>
      <div class="mes-card__body">
        <table class="mes-table">
          <thead><tr><th>对象</th><th>当前版本</th><th>目标版本</th><th>影响</th></tr></thead>
          <tbody>
            <tr v-for="row in versionCompareRows" :key="row.object">
              <td>{{ row.object }}</td>
              <td>{{ row.current }}</td>
              <td>{{ row.target }}</td>
              <td>{{ row.impact }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="mes-grid master-grid">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">组织 / 基地 / 产线</div>
          <span class="status-tag blue">{{ activeLine?.lineCode || 'LINE_01' }}</span>
        </div>
        <div class="mes-card__body detail-list">
          <div class="mes-field"><label>基地</label><input class="mes-input" :value="activeSiteText" readonly /></div>
          <div v-for="row in details" :key="row.label" class="detail-row">
            <b>{{ row.label }}</b><span>{{ row.value }}</span>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">Route 工艺路线版本</div>
          <span class="status-tag green">当前生效 V08</span>
        </div>
        <div class="mes-card__body">
          <div class="route-line">
            <div v-for="step in routeSteps" :key="step.seq" class="route-step" :class="{ current: step.current }">
              <strong>{{ step.seq }} {{ step.name }}</strong>
              <span>{{ step.meta }}</span>
              <span class="status-tag" :class="step.type">{{ step.tag }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">产线主数据</div>
          <span class="status-tag green">{{ activeLines.length }} 条</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>产线</th><th>基地</th><th>类型</th><th>区域</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="line in lines" :key="line.lineCode">
                <td>{{ line.lineCode }}</td>
                <td>{{ line.siteCode }}</td>
                <td>{{ line.lineType }}</td>
                <td>{{ line.workshop }}</td>
                <td><span class="status-tag" :class="statusType(line.status)">{{ line.status }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">班次主数据</div>
          <span class="status-tag blue">{{ activeShifts.length }} 个生效班次</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>班次</th><th>产线</th><th>开始</th><th>结束</th><th>跨天</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="shift in shifts" :key="shift.shiftCode">
                <td>{{ shift.shiftName }}</td>
                <td>{{ shift.lineCode }}</td>
                <td>{{ shift.startTime }}</td>
                <td>{{ shift.endTime }}</td>
                <td>{{ shift.crossDay ? '是' : '否' }}</td>
                <td><span class="status-tag" :class="statusType(shift.status)">{{ shift.status }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">Recipe 参数规格</div>
          <span class="status-tag blue">{{ activeRecipe?.recipeCode || '-' }}</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>参数</th><th>目标</th><th>下限</th><th>上限</th><th>单位</th><th>控制</th></tr></thead>
            <tbody>
              <tr v-for="param in recipeParams" :key="param.name">
                <td>{{ param.name }}</td><td>{{ param.target }}</td><td>{{ param.low }}</td><td>{{ param.high }}</td><td>{{ param.unit }}</td>
                <td><span class="status-tag" :class="param.type">{{ param.control }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">BOM / 物料版本</div>
          <span class="status-tag green">齐套检查</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>物料</th><th>名称</th><th>单位耗量</th><th>损耗</th><th>批次控制</th></tr></thead>
            <tbody>
              <tr v-for="item in bomItems" :key="item.code">
                <td>{{ item.code }}</td><td>{{ item.name }}</td><td>{{ item.qty }}</td><td>{{ item.loss }}</td>
                <td><span class="status-tag" :class="item.type">{{ item.control }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">BOM 变更审批 / 替代料策略</div>
        <span class="status-tag blue">{{ bomChangeRequests.length }} 单</span>
      </div>
      <div class="mes-card__body">
        <div class="bom-actions">
          <button v-if="canBomChange" class="mes-btn" :disabled="bomActionLoading" @click="createBomChange">提交变更草稿</button>
          <button v-if="canBomEcoApprove" class="mes-btn" :disabled="bomActionLoading || !firstPendingEcoTask" @click="approveFirstEcoTask">会签通过</button>
          <button v-if="canBomChange" class="mes-btn" :disabled="bomActionLoading || !firstReviewableChange" @click="reviewFirstBomChange">人工复核</button>
          <button v-if="canBomChange" class="mes-btn primary" :disabled="bomActionLoading || !firstApprovedChange" @click="publishFirstBomChange">发布生效</button>
          <span class="status-tag green">替代料按 substitute_group + priority 自动选择</span>
        </div>
        <table class="mes-table">
          <thead><tr><th>变更单</th><th>产品</th><th>源BOM</th><th>目标版本</th><th>状态</th><th>ECO</th><th>会签</th><th>附件</th><th>原因</th><th>更新时间</th></tr></thead>
          <tbody>
            <tr v-for="change in bomChangeRows" :key="change.changeNo">
              <td>{{ change.changeNo }}</td>
              <td>{{ change.productCode }}</td>
              <td>{{ change.sourceBomCode || '-' }}</td>
              <td>{{ change.targetVersion }}</td>
              <td><span class="status-tag" :class="statusType(change.status)">{{ change.status }}</span></td>
              <td><span class="status-tag" :class="ecoRiskType(change.ecoRiskLevel)">{{ change.ecoRiskLevel || 'MEDIUM' }}</span></td>
              <td>
                <span class="status-tag" :class="statusType(change.ecoApprovalStatus)">{{ change.approvalSummary }}</span>
              </td>
              <td><span class="status-tag" :class="change.attachmentCount > 0 ? 'green' : 'amber'">{{ change.attachmentCount }} 份</span></td>
              <td>{{ change.reason }}</td>
              <td>{{ change.time }}</td>
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
import { hasButton } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'
import {
  getBomChangeRequests,
  getBoms,
  getProductionLines,
  getRecipes,
  getShifts,
  getSites,
  decideBomEcoApproval,
  publishRecipe,
  publishBomChange,
  reviewBomChange,
  submitBomChange
} from '@/api/pilot'

const canPublishRecipe = computed(() => hasButton('recipe:publish'))
const canBomChange = computed(() => hasButton('bom:change'))
const canBomEcoApprove = computed(() => hasButton('bom:eco-approve'))

const sites = ref([])
const lines = ref([])
const shifts = ref([])
const boms = ref([])
const recipes = ref([])
const bomChangeRequests = ref([])
const bomActionLoading = ref('')
const recipeActionLoading = ref('')
const showVersionCompare = ref(false)

const activeLines = computed(() => lines.value.filter(line => line.status === 'ACTIVE'))
const activeShifts = computed(() => shifts.value.filter(shift => shift.status === 'ACTIVE'))
const activeLine = computed(() => activeLines.value[0] || lines.value[0])
const activeSite = computed(() => sites.value.find(site => site.siteCode === activeLine.value?.siteCode) || sites.value[0])
const activeSiteText = computed(() => activeSite.value ? `${activeSite.value.siteCode} ${activeSite.value.siteName}` : '-')
const details = computed(() => [
  { label: '基地类型', value: activeSite.value?.siteType || '-' },
  { label: '区域', value: activeSite.value?.region || '-' },
  { label: '当前产线', value: activeLine.value ? `${activeLine.value.lineName} / ${activeLine.value.status}` : '-' },
  { label: '车间段', value: activeLine.value?.workshop || '-' },
  { label: '班次策略', value: activeShifts.value.map(shift => shift.shiftName).join(' / ') || '-' },
  { label: '数据范围', value: '角色按产线 line_code 过滤 Lot、质量、异常、物料和载具数据' }
])

const fallbackSites = [
  { siteCode: 'SITE_HF_01', siteName: '合肥显示试点基地', siteType: 'DISPLAY_FAB', region: '华东', status: 'ACTIVE' }
]

const fallbackLines = [
  { lineCode: 'LINE_01', lineName: 'G6柔性AMOLED试点线', siteCode: 'SITE_HF_01', lineType: 'G6_FLEX_AMOLED', workshop: 'Array-Cell-Module', status: 'ACTIVE' },
  { lineCode: 'LINE_02', lineName: 'G6柔性AMOLED预留线', siteCode: 'SITE_HF_01', lineType: 'G6_FLEX_AMOLED', workshop: 'Array-Cell-Module', status: 'PLANNED' }
]

const fallbackShifts = [
  { shiftCode: 'SHIFT_D_LINE_01', shiftName: 'LINE_01 白班', lineCode: 'LINE_01', startTime: '08:00:00', endTime: '20:00:00', crossDay: 0, status: 'ACTIVE' },
  { shiftCode: 'SHIFT_N_LINE_01', shiftName: 'LINE_01 夜班', lineCode: 'LINE_01', startTime: '20:00:00', endTime: '08:00:00', crossDay: 1, status: 'ACTIVE' }
]

const statusType = status => {
  if (['ACTIVE', 'APPROVED', 'PUBLISHED'].includes(status)) return 'green'
  if (status === 'PLANNED') return 'blue'
  if (['INACTIVE', 'DRAFT'].includes(status)) return 'gray'
  if (status === 'REJECTED') return 'red'
  return 'amber'
}

const ecoRiskType = risk => {
  if (risk === 'HIGH') return 'red'
  if (risk === 'LOW') return 'green'
  return 'amber'
}

const activeBom = computed(() => boms.value.find(item => item.status === 'ACTIVE') || boms.value[0])
const activeRecipe = computed(() => recipes.value.find(item => item.status === 'ACTIVE') || recipes.value[0])
const firstPublishableRecipe = computed(() => recipes.value.find(item => ['DRAFT', 'INACTIVE'].includes(item.status)))
const firstSubmittedChange = computed(() => bomChangeRequests.value.find(item => item.status === 'SUBMITTED'))
const firstReviewableChange = computed(() => bomChangeRequests.value.find(item => item.status === 'SUBMITTED' && (item.ecoApprovalStatus === 'APPROVED' || !pendingEcoTasks(item).length)))
const firstApprovedChange = computed(() => bomChangeRequests.value.find(item => item.status === 'APPROVED'))
const firstPendingEcoTask = computed(() => {
  const change = firstSubmittedChange.value
  if (!change) return null
  return pendingEcoTasks(change)[0] || null
})
const bomChangeRows = computed(() => {
  const rows = bomChangeRequests.value.length ? bomChangeRequests.value : (__DEV_MOCK_FALLBACK__ ? fallbackBomChanges : [])
  return rows.map(item => ({
    ...item,
    attachmentCount: item.attachmentCount ?? (Array.isArray(item.attachments) ? item.attachments.length : 0),
    approvalSummary: ecoApprovalSummary(item),
    time: formatTime(item.publishedTime || item.reviewedTime || item.requestedTime)
  }))
})
const versionCompareRows = computed(() => {
  const rows = []
  const bom = activeBom.value
  const change = firstSubmittedChange.value || firstApprovedChange.value || bomChangeRequests.value[0]
  if (bom && change) {
    rows.push({
      object: `BOM ${bom.productCode || change.productCode || '-'}`,
      current: `${bom.bomCode || '-'} / ${bom.bomVersion || '-'}`,
      target: `${change.targetBomCode || change.changeNo || '-'} / ${change.targetVersion || '-'}`,
      impact: change.reason || change.impactScope || '替代料、齐套校验和消耗追溯'
    })
  }
  const recipe = activeRecipe.value
  const candidate = firstPublishableRecipe.value
  if (recipe && candidate) {
    rows.push({
      object: `Recipe ${recipe.productCode || candidate.productCode || '-'}`,
      current: `${recipe.recipeCode || '-'} / ${recipe.version || recipe.recipeVersion || '-'}`,
      target: `${candidate.recipeCode || '-'} / ${candidate.version || candidate.recipeVersion || '-'}`,
      impact: `${candidate.stepCode || recipe.stepCode || '工序'} 参数上下限、设备匹配和 Track In 校验`
    })
  }
  return rows
})

const loadOrgMasterData = async () => {
  try {
    const [siteRows, lineRows, shiftRows] = await Promise.all([
      getSites(),
      getProductionLines(),
      getShifts()
    ])
    sites.value = siteRows?.length ? siteRows : (__DEV_MOCK_FALLBACK__ ? fallbackSites : [])
    lines.value = lineRows?.length ? lineRows : (__DEV_MOCK_FALLBACK__ ? fallbackLines : [])
    shifts.value = shiftRows?.length ? shiftRows : (__DEV_MOCK_FALLBACK__ ? fallbackShifts : [])
  } catch (error) {
    warnDevFallback('组织主数据接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) {
      sites.value = fallbackSites
      lines.value = fallbackLines
      shifts.value = fallbackShifts
    }
  }
}

onMounted(loadOrgMasterData)

async function loadRecipeData() {
  try {
    const data = await getRecipes({ current: 1, size: 20 })
    const rows = Array.isArray(data?.records) ? data.records : []
    recipes.value = rows
  } catch (error) {
    recipes.value = []
  }
}

async function loadBomData() {
  try {
    const [bomRows, changeRows] = await Promise.all([
      getBoms(),
      getBomChangeRequests()
    ])
    boms.value = Array.isArray(bomRows) && bomRows.length ? bomRows : (__DEV_MOCK_FALLBACK__ ? fallbackBoms : [])
    bomChangeRequests.value = Array.isArray(changeRows) ? changeRows : []
  } catch (error) {
    warnDevFallback('BOM接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) boms.value = fallbackBoms
    bomChangeRequests.value = []
  }
}

async function createBomChange() {
  if (!canBomChange.value) {
    ElMessage.warning('当前角色无权提交 BOM 变更')
    return
  }
  const source = activeBom.value || (__DEV_MOCK_FALLBACK__ ? fallbackBoms[0] : null)
  if (!source) {
    ElMessage.warning('当前没有可用于变更的有效 BOM')
    return
  }
  const targetVersion = nextBomVersion(source.bomVersion)
  try {
    bomActionLoading.value = 'submit'
    await submitBomChange({
      sourceBomCode: source.bomCode,
      productCode: source.productCode,
      targetBomCode: `${source.bomCode.replace(/_V\d+$/i, '')}_${targetVersion}`,
      targetVersion,
      reason: '试点替代料策略与版本发布审批',
      ecoRiskLevel: 'MEDIUM',
      approvalRoles: ['PE', 'QE', 'PLANNER'],
      validationPlan: '完成替代料试产验证、IQC证据和良率复核后发布',
      rollbackPlan: '发布异常时回退上一版 ACTIVE BOM 并冻结目标版本',
      impactScope: '单基地/单产线试点产品',
      validationFileName: `${source.productCode || 'PRODUCT'}替代料验证报告.pdf`,
      validationFileUrl: `qms://eco/${source.bomCode}-${targetVersion}-substitute-validation.pdf`,
      validationFileHash: `sha256:${source.bomCode}-${targetVersion}`,
      attachmentRole: 'SUBSTITUTE_VALIDATION',
      operator: localStorage.getItem('username') || 'pe'
    })
    ElMessage.success('BOM变更草稿已提交')
    await loadBomData()
  } finally {
    bomActionLoading.value = ''
  }
}

async function reviewFirstBomChange() {
  const change = firstSubmittedChange.value
  if (!change) return
  try {
    bomActionLoading.value = 'review'
    await reviewBomChange(change.changeNo, {
      decision: 'APPROVED',
      reviewer: localStorage.getItem('username') || 'pe',
      comment: '替代料策略已完成试点审批'
    })
    ElMessage.success('BOM变更已审批通过')
    await loadBomData()
  } finally {
    bomActionLoading.value = ''
  }
}

async function publishFirstBomChange() {
  const change = firstApprovedChange.value
  if (!change) return
  try {
    bomActionLoading.value = 'publish'
    await publishBomChange(change.changeNo, {
      publisher: localStorage.getItem('username') || 'pe'
    })
    ElMessage.success('BOM版本已发布生效')
    await loadBomData()
  } finally {
    bomActionLoading.value = ''
  }
}

async function publishFirstRecipe() {
  if (!canPublishRecipe.value) {
    ElMessage.warning('当前角色无权发布 Recipe')
    return
  }
  const recipe = firstPublishableRecipe.value
  if (!recipe) {
    ElMessage.warning('当前没有可发布的 Recipe 版本')
    return
  }
  try {
    recipeActionLoading.value = 'publish'
    await publishRecipe(recipe.id || recipe.recipeId)
    ElMessage.success('Recipe 版本已发布')
    await loadRecipeData()
  } finally {
    recipeActionLoading.value = ''
  }
}

function compareMasterVersions() {
  if (!versionCompareRows.value.length) {
    ElMessage.warning('当前没有可对比的 BOM 或 Recipe 版本')
    return
  }
  showVersionCompare.value = true
  ElMessage.success(`已生成 ${versionCompareRows.value.length} 项版本对比`)
}

function nextBomVersion(version) {
  const matched = String(version || 'V01').match(/V(\d+)/i)
  const next = matched ? Number(matched[1]) + 1 : 2
  return `V${String(next).padStart(2, '0')}`
}

function pendingEcoTasks(change) {
  const tasks = Array.isArray(change?.ecoApprovalTasks) ? change.ecoApprovalTasks : []
  return tasks.filter(task => task.approvalStatus === 'PENDING')
}

function ecoApprovalSummary(change) {
  const tasks = Array.isArray(change?.ecoApprovalTasks) ? change.ecoApprovalTasks : []
  if (!tasks.length) return change?.ecoApprovalStatus || '-'
  const approved = tasks.filter(task => task.approvalStatus === 'APPROVED').length
  const rejected = tasks.filter(task => task.approvalStatus === 'REJECTED').length
  if (rejected) return `REJECTED ${rejected}/${tasks.length}`
  return `${change?.ecoApprovalStatus || 'PENDING'} ${approved}/${tasks.length}`
}

async function approveFirstEcoTask() {
  const task = firstPendingEcoTask.value
  if (!task) return
  try {
    bomActionLoading.value = 'eco'
    await decideBomEcoApproval(task.taskNo, {
      decision: 'APPROVE',
      approver: localStorage.getItem('username') || 'pe',
      opinion: `${task.approvalRole || 'ECO'}会签通过`
    })
    ElMessage.success('ECO 会签任务已通过')
    await loadBomData()
  } finally {
    bomActionLoading.value = ''
  }
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.toLocaleDateString()} ${date.toTimeString().slice(0, 5)}`
}

onMounted(loadBomData)
onMounted(loadRecipeData)

const routeSteps = [
  { seq: '010', name: 'CLEAN', meta: '需 Recipe / 无 QC', tag: '2 设备', type: 'green' },
  { seq: '020', name: 'COATING', meta: 'Recipe 必需 / 参数采集', tag: '4 Recipe', type: 'blue', current: true },
  { seq: '030', name: 'EXPOSURE', meta: 'Route 防跳站', tag: '3 设备', type: 'green' },
  { seq: '040', name: 'ETCH', meta: '关键工序', tag: '需复核', type: 'amber' },
  { seq: '050', name: 'EVAP', meta: '真空参数采集', tag: '2 设备', type: 'green' },
  { seq: '060', name: 'ENCAP', meta: '封装检测', tag: '2 设备', type: 'green' },
  { seq: '070', name: 'AOI', meta: '缺陷代码', tag: '全检', type: 'teal' },
  { seq: '080', name: 'BOND', meta: '模组绑定', tag: '3 设备', type: 'green' }
]

const recipeParams = [
  { name: '涂胶温度', target: '150.0', low: '145.0', high: '155.0', unit: '℃', control: '关键', type: 'red' },
  { name: '涂胶速度', target: '300', low: '280', high: '320', unit: 'mm/s', control: '关键', type: 'red' },
  { name: '膜厚', target: '2.00', low: '1.80', high: '2.20', unit: 'μm', control: '关键', type: 'red' },
  { name: '腔体压力', target: '0.80', low: '0.60', high: '0.90', unit: 'kPa', control: '预警', type: 'amber' }
]

const fallbackBoms = [
  { bomCode: 'BOM_65_V06', productCode: 'AMOLED_65', bomVersion: 'V06', status: 'ACTIVE' },
  { bomCode: 'BOM_67_V04', productCode: 'AMOLED_67', bomVersion: 'V04', status: 'ACTIVE' }
]

const fallbackBomChanges = [
  {
    changeNo: 'BCR-DEMO-001',
    productCode: 'AMOLED_65',
    sourceBomCode: 'BOM_65_V06',
    targetVersion: 'V07',
    status: 'SUBMITTED',
    ecoRiskLevel: 'MEDIUM',
    ecoApprovalStatus: 'PENDING',
    ecoApprovalTasks: [
      { taskNo: 'BEA-DEMO-001', approvalRole: 'PE', approvalStatus: 'APPROVED' },
      { taskNo: 'BEA-DEMO-002', approvalRole: 'QE', approvalStatus: 'PENDING' },
      { taskNo: 'BEA-DEMO-003', approvalRole: 'PLANNER', approvalStatus: 'PENDING' }
    ],
    reason: '替代料策略待审批',
    attachmentCount: 1,
    requestedTime: new Date().toISOString()
  }
]

const bomItems = [
  { code: 'MAT-PI-001', name: 'PI 胶', qty: '0.42', loss: '3%', control: '强制批次', type: 'red' },
  { code: 'MAT-OLED-R', name: '红光材料', qty: '0.08', loss: '5%', control: '强制批次', type: 'red' },
  { code: 'MAT-FPC-65', name: 'FPC', qty: '1', loss: '1%', control: '序列追溯', type: 'green' },
  { code: 'MAT-OCA', name: 'OCA 胶', qty: '1', loss: '2%', control: '先进先出', type: 'amber' }
]
</script>
