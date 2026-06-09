<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">报表与 AI / 良率日报、异常分析与 RAG SOP</h1>
        <p class="page-desc">AI 定位为业务辅助：基于 MES 数据和 SOP 检索生成结构化分析，不自动执行生产动作。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" @click="selectReportDate">日期 {{ reportDateLabel }}</button>
        <button class="mes-btn" @click="loadAiModelConfigs">模型配置</button>
        <button class="mes-btn" @click="loadKnowledgeDocuments">知识库</button>
        <button class="mes-btn" @click="loadKnowledgeIndexJobs">索引履历</button>
        <button v-if="canYieldReport" class="mes-btn primary" @click="generateYieldReport">生成 AI 报告</button>
      </div>
    </div>

    <div class="mes-grid cols-2">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">良率与缺陷分析</div>
          <span class="status-tag green">FPY {{ fpy }}</span>
        </div>
        <div class="mes-card__body split">
          <div class="bar-chart">
            <div
              v-for="item in bars"
              :key="item.label"
              class="bar-col"
              :class="item.type"
              :data-label="item.label"
              :style="{ height: item.height }"
            ></div>
          </div>
          <div class="cards">
            <div v-for="item in highlights" :key="item.title" class="mini-card">
              <div class="mini-top">
                <span>{{ item.title }}</span>
                <span class="status-tag" :class="item.type">{{ item.tag }}</span>
              </div>
              <div class="mini-meta">{{ item.text }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">AI 良率日报</div>
          <span class="status-tag purple">{{ activeYieldConfig.modelMode || 'SIMULATED' }}</span>
        </div>
        <div class="mes-card__body cards">
          <div v-for="item in aiSummary" :key="item.title" class="ai-box">
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-grid section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">AI 模型运行配置</div>
          <span class="status-tag blue">{{ activeModelConfigs.length }} ACTIVE</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead>
              <tr><th>配置</th><th>场景</th><th>Provider</th><th>模式</th><th>检索策略</th><th>状态</th></tr>
            </thead>
            <tbody>
              <tr v-for="config in aiModelConfigs" :key="config.configCode">
                <td>{{ config.configCode }}</td>
                <td>{{ config.useCase }}</td>
                <td>{{ config.modelProvider || config.provider }}</td>
                <td>{{ config.modelMode }}</td>
                <td>{{ config.retrievalStrategy }}</td>
                <td><span class="status-tag" :class="config.type || modelConfigType(config)">{{ config.status }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">RAG SOP 问答</div>
          <span class="status-tag" :class="ragEvidence.evidenceType">{{ ragEvidence.evidenceLevel }}</span>
        </div>
        <div class="mes-card__body">
          <div class="mes-field">
            <label>问题</label>
            <input v-model="question" class="mes-input" />
          </div>
          <div class="cards rag-answer">
            <div class="ai-box">
              <h3>回答</h3>
              <p>{{ ragAnswer }}</p>
              <div class="evidence-line">
                <span>{{ ragEvidence.retrievalStrategy }}</span>
                <span>{{ ragEvidence.evidenceCount }} 条引用</span>
                <span>最高分 {{ formatScore(ragEvidence.maxEvidenceScore) }}</span>
              </div>
            </div>
            <button v-if="canAskSop" class="mes-btn primary" @click="askSop">查询 SOP</button>
            <div v-for="source in ragSources" :key="source.chunkNo || source.chunk || source.documentName" class="mini-card">
              <div class="mini-top">
                <span>引用来源</span>
                <span class="status-tag" :class="source.evidenceLevel ? evidenceTagType(source.evidenceLevel) : 'blue'">
                  {{ source.evidenceLevel || source.documentName || source.document || 'SOP' }}
                </span>
              </div>
              <div class="mini-meta">
                片段：{{ source.chunkTitle || source.chunk || source.chunkNo || source }}
                <span v-if="source.score"> / {{ formatScore(source.score) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">知识库导入</div>
          <span class="status-tag purple">{{ knowledgeDocuments.length }} 份 / {{ indexJobs.length }} 任务</span>
        </div>
        <div class="mes-card__body">
          <div class="kb-form">
            <div class="kb-form-row">
              <div class="mes-field">
                <label>文档名称</label>
                <input v-model="documentForm.documentName" class="mes-input" />
              </div>
              <div class="mes-field">
                <label>版本</label>
                <input v-model="documentForm.docVersion" class="mes-input" />
              </div>
            </div>
            <div class="kb-form-row">
              <div class="mes-field">
                <label>类型</label>
                <select v-model="documentForm.documentType" class="mes-input">
                  <option>SOP</option>
                  <option>EQUIPMENT_MANUAL</option>
                  <option>QUALITY_STANDARD</option>
                </select>
              </div>
              <div class="mes-field">
                <label>文件</label>
                <input class="mes-input file-input" type="file" accept=".txt,.md,.markdown" @change="handleKnowledgeFile" />
              </div>
            </div>
            <div class="mes-field">
              <label>文档内容</label>
              <textarea v-model="documentForm.content" class="mes-textarea" rows="6"></textarea>
            </div>
            <button v-if="canImportKb" class="mes-btn primary" :disabled="importing" @click="importSopDocument">
              {{ importing ? '导入中' : '导入并切片' }}
            </button>
            <div v-if="canIndexKb" class="kb-actions">
              <button class="mes-btn" :disabled="indexing" @click="runKnowledgeIndex('KEYWORD_FALLBACK')">
                {{ indexing ? '索引中' : '重建关键词索引' }}
              </button>
              <button class="mes-btn" :disabled="indexing" @click="runKnowledgeIndex('HYBRID_LOCAL')">Hybrid Local</button>
              <button class="mes-btn warn" :disabled="indexing" @click="runKnowledgeIndex('PGVECTOR_READY')">标记向量待联调</button>
            </div>
          </div>

          <div class="cards kb-docs">
            <div v-for="doc in knowledgeDocuments.slice(0, 4)" :key="doc.documentNo" class="mini-card">
              <div class="mini-top">
                <span>{{ doc.documentName }}</span>
                <span class="status-tag" :class="doc.indexType || 'red'">{{ doc.indexStatus || 'NOT_INDEXED' }}</span>
              </div>
              <div class="mini-meta">
                {{ doc.documentNo }} / {{ doc.documentType }} / {{ doc.docVersion }} / {{ doc.chunkCount }}片
                <span v-if="doc.lastIndexedTime"> / 最近索引 {{ String(doc.lastIndexedTime).slice(0, 16).replace('T', ' ') }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-grid section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">知识库索引任务履历</div>
          <span class="status-tag blue">{{ indexJobs.length }} 条</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead>
              <tr><th>任务</th><th>范围</th><th>策略</th><th>模型</th><th>切片</th><th>状态</th><th>时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="job in indexJobs" :key="job.jobNo">
                <td>{{ job.jobNo }}</td>
                <td>{{ job.documentNo || 'ALL' }}</td>
                <td>{{ job.retrievalStrategy }}</td>
                <td>{{ job.embeddingModel || '-' }}</td>
                <td>{{ job.indexedChunkCount || 0 }}/{{ job.targetChunkCount || 0 }}</td>
                <td><span class="status-tag" :class="job.type || jobStatusType(job)">{{ job.status }}</span></td>
                <td>{{ job.time || String(job.createdTime || '').slice(11, 16) || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="indexJobs[0]?.boundaryNote" class="index-note">{{ indexJobs[0].boundaryNote }}</div>
        </div>
      </div>
    </div>

    <div class="mes-grid section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">AI 报告留痕</div>
          <span class="status-tag blue">{{ reportRecords.length }} 条</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead>
              <tr><th>报告</th><th>范围</th><th>生成者</th><th>模式</th><th>证据</th><th>时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="record in reportRecords" :key="record.report">
                <td>{{ record.report }}</td><td>{{ record.scope }}</td><td>{{ record.owner }}</td>
                <td>{{ record.modelMode || '-' }}</td>
                <td><span class="status-tag" :class="record.type">{{ record.status }}</span></td><td>{{ record.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  askKnowledgeBase,
  createKnowledgeIndexJob,
  createYieldReport,
  getAiModelConfigs,
  getAiReportRecords,
  getKnowledgeIndexJobs,
  getKnowledgeDocuments,
  getYieldDashboard,
  importKnowledgeDocument
} from '@/api/pilot'
import { hasButton } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'

const fpy = ref(__DEV_MOCK_FALLBACK__ ? '96.82%' : '-')
const reportDate = ref(new Date().toISOString().slice(0, 10))

const bars = ref(__DEV_MOCK_FALLBACK__ ? [
  { label: 'CLEAN', height: '72%', type: 'green' },
  { label: 'COAT', height: '58%', type: 'amber' },
  { label: 'EXPOS', height: '68%', type: 'green' },
  { label: 'ETCH', height: '64%', type: 'green' },
  { label: 'EVAP', height: '42%', type: 'red' },
  { label: 'AOI', height: '75%', type: 'green' },
  { label: 'BOND', height: '54%', type: 'amber' },
  { label: 'AGING', height: '70%', type: 'green' }
] : [])

const highlights = ref(__DEV_MOCK_FALLBACK__ ? [
  { title: '最大不良来源', tag: 'Mura 32', type: 'red', text: '集中在 Cell 段 AOI 后检出。' },
  { title: '疑似工序', tag: 'COATING', type: 'amber', text: '膜厚偏低与压力波动存在时间相关性。' },
  { title: '影响设备', tag: 'COATER_02', type: 'amber', text: '最近 24 小时压力波动 6 次。' }
] : [])

const aiSummary = ref(__DEV_MOCK_FALLBACK__ ? [
  { title: '摘要', text: '今日 G6-FLEX-LINE-01 良率低于目标 0.18 个百分点，主要波动集中在 COATING 后 AOI 检测阶段。' },
  { title: '疑似根因', text: 'COATER_02 压力波动与膜厚偏低事件时间重叠；PI-ADH-240606-A 同批次也出现 2 个 Lot 偏低。' },
  { title: '建议', text: '暂停 COATER_02 高优先级 Lot 进站，执行压力传感器点检；对 PI 胶批次追加抽检。' }
] : [])

const reportRecords = ref(__DEV_MOCK_FALLBACK__ ? [
  { report: 'YIELD-20260606-D', scope: 'G6-FLEX-LINE-01', owner: 'qa_lead', modelMode: 'SIMULATED', status: 'NONE', type: 'green', time: '14:30' },
  { report: 'EQ-COATER02-ANL', scope: 'COATER_02', owner: 'eq_eng', modelMode: 'SIMULATED', status: 'LOW', type: 'amber', time: '14:12' },
  { report: 'RAG-SOP-ASK-889', scope: 'SOP问答', owner: 'op1007', modelMode: 'SIMULATED', status: 'MEDIUM', type: 'blue', time: '13:58' }
] : [])

const question = ref(__DEV_MOCK_FALLBACK__ ? 'COATER_02 压力波动导致膜厚偏低时应该怎么排查？' : '')
const ragAnswer = ref(__DEV_MOCK_FALLBACK__ ? '优先检查压力传感器零点、供胶阀门响应、腔体密封与近期 PM 记录；若连续 2 批 Lot 膜厚偏低，应暂停进站并触发工程复核。' : '等待 SOP 问答结果')
const ragSources = ref(__DEV_MOCK_FALLBACK__ ? [{ document: 'SOP-COAT-017', chunk: '涂胶压力波动排查 / 片段 3、片段 5', evidenceLevel: 'MEDIUM', score: 0.82 }] : [])
const ragEvidence = ref(__DEV_MOCK_FALLBACK__ ? {
  retrievalStrategy: 'KEYWORD_FALLBACK',
  evidenceCount: 1,
  maxEvidenceScore: 0.82,
  evidenceLevel: 'MEDIUM',
  evidenceType: 'blue',
  insufficientEvidence: false
} : {
  retrievalStrategy: '-',
  evidenceCount: 0,
  maxEvidenceScore: 0,
  evidenceLevel: 'NONE',
  evidenceType: 'gray',
  insufficientEvidence: true
})
const knowledgeDocuments = ref([])
const aiModelConfigs = ref([])
const indexJobs = ref([])
const importing = ref(false)
const indexing = ref(false)
const canYieldReport = computed(() => hasButton('ai:yield-report'))
const canAskSop = computed(() => hasButton('ai:kb-ask'))
const canImportKb = computed(() => hasButton('ai:kb-import'))
const canIndexKb = computed(() => hasButton('ai:kb-index'))
const activeModelConfigs = computed(() => aiModelConfigs.value.filter(config => config.status === 'ACTIVE' && config.enabled !== 0))
const activeYieldConfig = computed(() => aiModelConfigs.value.find(config => config.useCase === 'YIELD_DAILY') || {})
const reportDateLabel = computed(() => reportDate.value.slice(5))
const documentForm = ref(__DEV_MOCK_FALLBACK__ ? {
  documentName: '涂胶膜厚异常处置SOP',
  documentType: 'SOP',
  docVersion: 'V1.0',
  content: '一、触发条件\n涂胶膜厚超出Recipe上下限、连续两批Lot出现同类膜厚异常、或COATER设备压力波动影响当前Lot时，必须触发质量复判。\n\n二、处置步骤\n先Hold受影响Lot，记录设备、Recipe、物料批次和过程参数快照；QE完成MRB复判后选择放行、返工或报废。\n\n三、引用要求\n关闭异常前必须填写根因、处置结论和复判人，AI仅提供SOP依据，不自动执行生产动作。'
} : {
  documentName: '',
  documentType: 'SOP',
  docVersion: '',
  content: ''
})

function loadYieldFallback(data) {
  const defectTop = data?.defectTopN || []
  const alarmTop = data?.equipmentAlarmTopN || []
  if (defectTop.length || alarmTop.length) {
    highlights.value = [
      { title: '最大不良来源', tag: `${defectTop[0]?.defectName || 'Mura'} ${defectTop[0]?.qty || 0}`, type: 'red', text: '来自 MES 良率看板接口的缺陷 TopN。' },
      { title: '疑似工序', tag: 'COATING', type: 'amber', text: '膜厚、真空和涂胶参数需要联合排查。' },
      { title: '影响设备', tag: alarmTop[0]?.equipmentCode || 'EVAP_01', type: 'amber', text: `设备异常 TopN 次数 ${alarmTop[0]?.qty || 0}。` }
    ]
  }
}

function applyReport(report) {
  const output = report?.output || {}
  const recommendations = Array.isArray(output.recommendations) ? output.recommendations.join('；') : output.recommendations
  aiSummary.value = [
    { title: '摘要', text: output.summary || 'AI 报告已生成。' },
    { title: '疑似根因', text: output.suspectedRootCause || '当前证据仍需人工复核。' },
    { title: '建议', text: recommendations || '建议质量、工艺、设备三方联合确认后再执行处置。' }
  ]
  reportRecords.value = [
    {
      report: report.reportNo,
      scope: 'G6-FLEX-LINE-01',
      owner: 'system',
      modelMode: report.modelMode || 'SIMULATED',
      status: report.evidenceLevel || 'NONE',
      type: report.modelMode === 'SHADOW' ? 'amber' : 'green',
      time: String(report.createdTime || '').slice(11, 16) || '当前'
    },
    ...reportRecords.value
  ]
}

async function loadYieldDashboard() {
  try {
    const data = await getYieldDashboard()
    loadYieldFallback(data)
    await loadAiModelConfigs()
    await loadKnowledgeDocuments()
    await loadKnowledgeIndexJobs()
    await loadAiReportRecords()
  } catch (error) {
    warnDevFallback('良率看板接口不可用', error)
  }
}

async function loadAiModelConfigs() {
  try {
    const rows = await getAiModelConfigs()
    if (Array.isArray(rows)) aiModelConfigs.value = rows
  } catch (error) {
    warnDevFallback('AI模型配置接口不可用', error)
  }
}

async function loadKnowledgeDocuments() {
  try {
    const docs = await getKnowledgeDocuments()
    if (Array.isArray(docs)) knowledgeDocuments.value = docs
  } catch (error) {
    console.warn('知识库文档列表不可用', error)
  }
}

async function loadKnowledgeIndexJobs() {
  try {
    const jobs = await getKnowledgeIndexJobs()
    if (Array.isArray(jobs)) indexJobs.value = jobs
  } catch (error) {
    console.warn('知识库索引履历不可用', error)
  }
}

function applyAiReportRecords(rows) {
  reportRecords.value = rows.map(row => ({
    report: row.reportNo || row.report,
    scope: row.scope || row.bizNo || row.bizType || '-',
    owner: row.owner || row.createdBy || 'system',
    modelMode: row.modelMode || '-',
    status: row.status || row.evidenceLevel || row.recordStatus || 'SUCCESS',
    type: row.type || evidenceTagType(row.evidenceLevel),
    time: row.time || String(row.createdTime || '').slice(11, 16) || '-'
  }))
}

async function loadAiReportRecords() {
  try {
    const rows = await getAiReportRecords()
    if (Array.isArray(rows) && rows.length) applyAiReportRecords(rows)
  } catch (error) {
    warnDevFallback('AI报告留痕接口不可用', error)
  }
}

function handleKnowledgeFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  documentForm.value.documentName = file.name.replace(/\.(md|markdown|txt)$/i, '')
  const reader = new FileReader()
  reader.onload = () => {
    documentForm.value.content = String(reader.result || '')
  }
  reader.readAsText(file, 'UTF-8')
}

async function importSopDocument() {
  if (!canImportKb.value) {
    ElMessage.warning('当前角色无权导入知识库')
    return
  }
  try {
    importing.value = true
    const result = await importKnowledgeDocument(documentForm.value)
    ElMessage.success(`导入成功，生成 ${result.chunkCount || 0} 个切片`)
    question.value = `${documentForm.value.documentName} 的处置要求是什么？`
    await loadKnowledgeDocuments()
    await loadKnowledgeIndexJobs()
  } catch (error) {
    console.warn('知识库导入失败', error)
  } finally {
    importing.value = false
  }
}

async function runKnowledgeIndex(retrievalStrategy) {
  if (!canIndexKb.value) {
    ElMessage.warning('当前角色无权重建知识库索引')
    return
  }
  try {
    indexing.value = true
    const job = await createKnowledgeIndexJob({ retrievalStrategy })
    ElMessage.success(`索引任务完成：${job.indexedChunkCount || 0}/${job.targetChunkCount || 0} 个切片`)
    await loadKnowledgeDocuments()
    await loadKnowledgeIndexJobs()
  } catch (error) {
    console.warn('知识库索引任务失败', error)
  } finally {
    indexing.value = false
  }
}

async function generateYieldReport() {
  if (!canYieldReport.value) {
    ElMessage.warning('当前角色无权生成 AI 良率日报')
    return
  }
  try {
    const report = await createYieldReport({ lineCode: 'G6-FLEX-LINE-01', reportDate: reportDate.value })
    applyReport(report)
    await loadAiReportRecords()
    ElMessage.success('AI 良率日报已生成')
  } catch (error) {
    console.warn('AI 良率日报生成失败', error)
  }
}

function selectReportDate() {
  const current = new Date(`${reportDate.value}T00:00:00`)
  current.setDate(current.getDate() - 1)
  const earliest = new Date()
  earliest.setDate(earliest.getDate() - 7)
  if (current < earliest) {
    reportDate.value = new Date().toISOString().slice(0, 10)
  } else {
    reportDate.value = current.toISOString().slice(0, 10)
  }
  ElMessage.success(`AI 报告日期已切换为 ${reportDate.value}`)
}

async function askSop() {
  if (!canAskSop.value) {
    ElMessage.warning('当前角色无权发起 SOP 问答')
    return
  }
  try {
    const data = await askKnowledgeBase({ question: question.value })
    ragAnswer.value = data.answer
    ragSources.value = Array.isArray(data.sources) ? data.sources : []
    ragEvidence.value = {
      retrievalStrategy: data.retrievalStrategy || 'KEYWORD_FALLBACK',
      evidenceCount: data.evidenceCount || 0,
      maxEvidenceScore: data.maxEvidenceScore || data.confidence || 0,
      evidenceLevel: data.evidenceLevel || 'INSUFFICIENT',
      evidenceType: data.evidenceType || evidenceTagType(data.evidenceLevel),
      insufficientEvidence: Boolean(data.insufficientEvidence)
    }
    reportRecords.value = [
      {
        report: data.reportNo || `RAG-${Date.now()}`,
        scope: 'SOP问答',
        owner: 'op1007',
        modelMode: data.modelMode || 'SIMULATED',
        status: ragEvidence.value.insufficientEvidence ? '依据不足' : ragEvidence.value.evidenceLevel,
        type: ragEvidence.value.evidenceType,
        time: '当前'
      },
      ...reportRecords.value
    ]
    await loadAiReportRecords()
  } catch (error) {
    warnDevFallback('SOP 问答接口不可用', error)
  }
}

function evidenceTagType(level) {
  if (level === 'HIGH') return 'green'
  if (level === 'MEDIUM') return 'blue'
  if (level === 'LOW') return 'amber'
  return 'red'
}

function modelConfigType(config) {
  if (config.enabled === 0) return 'gray'
  return config.status === 'ACTIVE' ? 'green' : 'amber'
}

function jobStatusType(job) {
  if (job?.status === 'FAILED') return 'red'
  if (job?.status === 'RUNNING') return 'blue'
  return job?.retrievalStrategy === 'PGVECTOR_READY' ? 'amber' : 'green'
}

function formatScore(value) {
  return Number(value || 0).toFixed(2)
}

onMounted(loadYieldDashboard)
</script>

<style scoped>
.rag-answer {
  margin-top: 10px;
}

.evidence-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  color: var(--mes-muted);
  font-size: 12px;
}

.kb-form {
  display: grid;
  gap: 12px;
}

.kb-form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mes-textarea {
  width: 100%;
  resize: vertical;
  min-height: 128px;
  border: 1px solid var(--mes-line);
  border-radius: 7px;
  padding: 10px 12px;
  color: var(--mes-text);
  background: #fff;
  font: inherit;
  line-height: 1.5;
}

.file-input {
  padding-top: 8px;
}

.kb-docs {
  margin-top: 14px;
}

.kb-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.index-note {
  margin-top: 10px;
  color: var(--mes-muted);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 760px) {
  .kb-form-row {
    grid-template-columns: 1fr;
  }
}
</style>
