<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">Recipe 管理 / 参数版本与发布校验</h1>
        <p class="page-desc">维护产品、工序、设备绑定的 Recipe 版本，Track In 和 Track Out 均依赖这里的生效参数快照。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" :disabled="loading" @click="fetchRecipeList">{{ loading ? '刷新中' : '刷新' }}</button>
        <button class="mes-btn" :disabled="!selectedRecipe" @click="openRecipeDetail(selectedRecipe)">参数详情</button>
        <button
          v-if="canPublishRecipe"
          class="mes-btn primary"
          :disabled="publishing || !firstPublishableRecipe"
          @click="handlePublish(firstPublishableRecipe)"
        >
          {{ publishing ? '发布中' : '发布版本' }}
        </button>
      </div>
    </div>

    <div class="mes-grid cols-4">
      <div v-for="metric in recipeMetrics" :key="metric.label" class="mes-card metric-card">
        <div class="metric-label"><span>{{ metric.label }}</span><span>{{ metric.note }}</span></div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-meta"><span>{{ metric.left }}</span><span>{{ metric.right }}</span></div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">Recipe 版本池</div>
        <span class="status-tag blue">{{ pagination.total }} 条</span>
      </div>
      <div class="mes-card__body">
        <div class="mes-filters">
          <div class="mes-field">
            <label>产品</label>
            <input v-model.trim="queryForm.productCode" class="mes-input" placeholder="AMOLED_65" @keyup.enter="handleQuery" />
          </div>
          <div class="mes-field">
            <label>工序</label>
            <select v-model="queryForm.stepCode" class="mes-select">
              <option value="">全部工序</option>
              <option v-for="step in stepOptions" :key="step" :value="step">{{ step }}</option>
            </select>
          </div>
          <div class="mes-field">
            <label>设备</label>
            <select v-model="queryForm.equipmentCode" class="mes-select">
              <option value="">全部设备</option>
              <option v-for="equipment in equipmentOptions" :key="equipment" :value="equipment">{{ equipment }}</option>
            </select>
          </div>
          <div class="mes-field">
            <label>状态</label>
            <select v-model="queryForm.status" class="mes-select">
              <option value="">全部状态</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="DRAFT">DRAFT</option>
              <option value="INACTIVE">INACTIVE</option>
            </select>
          </div>
          <button class="mes-btn primary" :disabled="loading" @click="handleQuery">
            {{ loading ? '查询中' : '查询' }}
          </button>
        </div>

        <table class="mes-table recipe-table">
          <thead>
            <tr>
              <th>Recipe</th>
              <th>名称</th>
              <th>产品</th>
              <th>工序</th>
              <th>设备</th>
              <th>版本</th>
              <th>状态</th>
              <th>说明</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="recipe in recipeList"
              :key="recipe.id"
              :class="{ selected: selectedRecipe?.id === recipe.id }"
              @click="selectedRecipe = recipe"
            >
              <td>{{ recipe.recipeCode }}</td>
              <td>{{ recipe.recipeName || '-' }}</td>
              <td>{{ recipe.productCode }}</td>
              <td>{{ recipe.stepCode }}</td>
              <td>{{ recipe.equipmentCode }}</td>
              <td>{{ recipe.recipeVersion || recipe.version || '-' }}</td>
              <td><span class="status-tag" :class="statusType(recipe.status)">{{ recipe.status || '-' }}</span></td>
              <td>{{ recipe.description || '-' }}</td>
              <td>
                <div class="row-actions">
                  <button class="mes-btn tiny" @click.stop="openRecipeDetail(recipe)">参数</button>
                  <button
                    v-if="canPublishRecipe"
                    class="mes-btn tiny primary"
                    :disabled="publishing || recipe.status === 'ACTIVE'"
                    @click.stop="handlePublish(recipe)"
                  >
                    发布
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!recipeList.length">
              <td colspan="9">没有符合条件的 Recipe</td>
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
            @size-change="fetchRecipeList"
            @current-change="fetchRecipeList"
          />
        </div>
      </div>
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="`Recipe 参数详情 - ${detailRecipe?.recipeCode || '-'}`"
      size="560px"
    >
      <div v-if="detailRecipe" class="recipe-detail">
        <div class="detail-list">
          <div class="detail-row"><b>Recipe</b><span>{{ detailRecipe.recipeCode }}</span></div>
          <div class="detail-row"><b>产品/工序</b><span>{{ detailRecipe.productCode }} / {{ detailRecipe.stepCode }}</span></div>
          <div class="detail-row"><b>设备</b><span>{{ detailRecipe.equipmentCode }}</span></div>
          <div class="detail-row"><b>版本状态</b><span>{{ detailRecipe.recipeVersion || detailRecipe.version }} / {{ detailRecipe.status }}</span></div>
          <div class="detail-row"><b>说明</b><span>{{ detailRecipe.description || '-' }}</span></div>
        </div>

        <div class="mes-card section-gap">
          <div class="mes-card__head">
            <div class="mes-card__title">参数上下限</div>
            <span class="status-tag red">{{ keyParamCount }} 关键参数</span>
          </div>
          <div class="mes-card__body">
            <table class="mes-table param-table">
              <thead>
                <tr><th>参数</th><th>目标</th><th>下限</th><th>上限</th><th>单位</th><th>控制</th></tr>
              </thead>
              <tbody>
                <tr v-for="param in recipeParams" :key="param.paramCode || param.paramName">
                  <td>{{ param.paramName }}</td>
                  <td>{{ param.targetValue }}</td>
                  <td>{{ param.lowerLimit }}</td>
                  <td>{{ param.upperLimit }}</td>
                  <td>{{ param.unit || '-' }}</td>
                  <td>
                    <span class="status-tag" :class="Number(param.isKeyParam) === 1 ? 'red' : 'gray'">
                      {{ Number(param.isKeyParam) === 1 ? '关键' : '普通' }}
                    </span>
                  </td>
                </tr>
                <tr v-if="!recipeParams.length">
                  <td colspan="6">当前 Recipe 暂无参数明细</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="check-cell blue section-gap">
          <strong>执行约束</strong>
          <span>ACTIVE Recipe 会在 Track In 校验产品、工序和设备匹配；Track Out 参数超限时会触发质量 Hold。</span>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRecipeDetail, getRecipeList, publishRecipe } from '@/api/recipe'
import { hasButton } from '@/utils/permissions'

const loading = ref(false)
const paramsLoading = ref(false)
const publishing = ref(false)
const recipeList = ref([])
const selectedRecipe = ref(null)
const detailRecipe = ref(null)
const recipeParams = ref([])
const drawerVisible = ref(false)

const queryForm = reactive({
  productCode: '',
  stepCode: '',
  equipmentCode: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const canPublishRecipe = computed(() => hasButton('recipe:publish'))
const firstPublishableRecipe = computed(() => recipeList.value.find(recipe => recipe.status !== 'ACTIVE'))
const stepOptions = computed(() => [...new Set(recipeList.value.map(recipe => recipe.stepCode).filter(Boolean))])
const equipmentOptions = computed(() => [...new Set(recipeList.value.map(recipe => recipe.equipmentCode).filter(Boolean))])
const keyParamCount = computed(() => recipeParams.value.filter(param => Number(param.isKeyParam) === 1).length)
const recipeMetrics = computed(() => {
  const total = recipeList.value.length
  const active = recipeList.value.filter(recipe => recipe.status === 'ACTIVE').length
  const draft = recipeList.value.filter(recipe => recipe.status === 'DRAFT').length
  const inactive = recipeList.value.filter(recipe => recipe.status === 'INACTIVE').length
  return [
    { label: 'Recipe 总数', value: total, note: '当前筛选', left: '产品+工序+设备', right: '版本池' },
    { label: '生效版本', value: active, note: 'ACTIVE', left: 'Track In 可用', right: '可追溯' },
    { label: '待发布', value: draft, note: 'DRAFT', left: '需工艺发布', right: '需审计' },
    { label: '停用版本', value: inactive, note: 'INACTIVE', left: '只读保留', right: '历史追溯' }
  ]
})

async function fetchRecipeList() {
  loading.value = true
  try {
    const params = {
      current: pagination.page,
      size: pagination.size
    }
    Object.entries(queryForm).forEach(([key, value]) => {
      if (value) params[key] = value
    })
    const data = await getRecipeList(params)
    recipeList.value = data?.records || []
    pagination.total = data?.total || 0
    if (!selectedRecipe.value || !recipeList.value.some(recipe => recipe.id === selectedRecipe.value?.id)) {
      selectedRecipe.value = recipeList.value[0] || null
    }
  } catch (error) {
    console.error('获取 Recipe 列表失败:', error)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.page = 1
  fetchRecipeList()
}

async function openRecipeDetail(recipe) {
  if (!recipe) {
    ElMessage.warning('请选择 Recipe')
    return
  }
  selectedRecipe.value = recipe
  detailRecipe.value = recipe
  recipeParams.value = []
  drawerVisible.value = true
  paramsLoading.value = true
  try {
    const data = await getRecipeDetail(recipe.id)
    detailRecipe.value = data || recipe
    recipeParams.value = data?.params || []
  } catch (error) {
    console.error('获取 Recipe 参数失败:', error)
  } finally {
    paramsLoading.value = false
  }
}

async function handlePublish(recipe) {
  if (!recipe) {
    ElMessage.warning('当前没有可发布的 Recipe')
    return
  }
  if (!canPublishRecipe.value) {
    ElMessage.warning('当前角色无权发布 Recipe')
    return
  }
  publishing.value = true
  try {
    await publishRecipe(recipe.id)
    ElMessage.success(`${recipe.recipeCode} 已发布`)
    await fetchRecipeList()
  } catch (error) {
    console.error('Recipe 发布失败:', error)
  } finally {
    publishing.value = false
  }
}

function statusType(status) {
  if (status === 'ACTIVE') return 'green'
  if (status === 'DRAFT') return 'blue'
  if (status === 'INACTIVE') return 'gray'
  return 'amber'
}

onMounted(fetchRecipeList)
</script>

<style scoped>
.recipe-table th:nth-child(8),
.recipe-table td:nth-child(8) {
  width: 220px;
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

.recipe-detail {
  display: grid;
  gap: 12px;
}

.mes-table tbody tr {
  cursor: pointer;
}

.mes-table tbody tr.selected td {
  background: var(--mes-paper-muted);
  box-shadow: inset 0 1px 0 var(--mes-line-soft), inset 0 -1px 0 var(--mes-line-soft);
}
</style>
