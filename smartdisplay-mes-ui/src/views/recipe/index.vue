<template>
  <div>
    <!-- 查询表单 -->
    <el-card shadow="hover" style="margin-bottom: 20px">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="产品编码">
          <el-select v-model="queryForm.productCode" placeholder="全部产品" clearable style="width: 150px">
            <el-option label="AMOLED_65" value="AMOLED_65" />
            <el-option label="AMOLED_67" value="AMOLED_67" />
          </el-select>
        </el-form-item>
        <el-form-item label="工序编码">
          <el-select v-model="queryForm.stepCode" placeholder="全部工序" clearable style="width: 150px">
            <el-option label="COATING" value="COATING" />
            <el-option label="EVAPORATION" value="EVAPORATION" />
            <el-option label="ETCH" value="ETCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备编码">
          <el-select v-model="queryForm.equipmentCode" placeholder="全部设备" clearable style="width: 150px">
            <el-option label="COATER_01" value="COATER_01" />
            <el-option label="COATER_02" value="COATER_02" />
            <el-option label="EVAP_01" value="EVAP_01" />
            <el-option label="ETCH_01" value="ETCH_01" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery" :icon="Search">查询</el-button>
          <el-button @click="handleReset" :icon="Refresh">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Recipe列表 -->
    <el-card shadow="hover">
      <el-table :data="recipeList" v-loading="loading" stripe>
        <el-table-column prop="recipeCode" label="Recipe编码" width="150" />
        <el-table-column prop="recipeName" label="Recipe名称" width="200" />
        <el-table-column prop="productCode" label="产品编码" width="130" />
        <el-table-column prop="stepCode" label="工序编码" width="130" />
        <el-table-column prop="equipmentCode" label="设备编码" width="130" />
        <el-table-column prop="recipeVersion" label="版本" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleViewParams(row)" :icon="View">
              查看参数
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 参数详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`Recipe参数详情 - ${selectedRecipe.recipeCode}`"
      size="50%"
    >
      <div v-if="selectedRecipe.recipeCode">
        <el-descriptions :column="2" border style="margin-bottom: 20px">
          <el-descriptions-item label="Recipe编码">{{ selectedRecipe.recipeCode }}</el-descriptions-item>
          <el-descriptions-item label="Recipe名称">{{ selectedRecipe.recipeName }}</el-descriptions-item>
          <el-descriptions-item label="产品编码">{{ selectedRecipe.productCode }}</el-descriptions-item>
          <el-descriptions-item label="工序编码">{{ selectedRecipe.stepCode }}</el-descriptions-item>
          <el-descriptions-item label="设备编码">{{ selectedRecipe.equipmentCode }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ selectedRecipe.recipeVersion }}</el-descriptions-item>
          <el-descriptions-item label="状态" :span="2">
            <el-tag :type="selectedRecipe.status === 'ACTIVE' ? 'success' : 'info'">
              {{ selectedRecipe.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">
            {{ selectedRecipe.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">
          <span style="font-weight: bold">Recipe参数列表</span>
        </el-divider>

        <el-table :data="recipeParams" v-loading="paramsLoading" border>
          <el-table-column prop="paramName" label="参数名称" width="150" />
          <el-table-column prop="targetValue" label="目标值" width="100" align="right">
            <template #default="{ row }">
              <span style="font-weight: 600; color: var(--mes-ink)">{{ row.targetValue }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="upperLimit" label="上限" width="100" align="right">
            <template #default="{ row }">
              <span style="color: var(--mes-red)">{{ row.upperLimit }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lowerLimit" label="下限" width="100" align="right">
            <template #default="{ row }">
              <span style="color: var(--mes-amber)">{{ row.lowerLimit }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column prop="paramType" label="参数类型" width="120" />
          <el-table-column prop="isKeyParam" label="关键参数" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.isKeyParam === 1" type="danger" size="small">关键</el-tag>
              <el-tag v-else type="info" size="small">普通</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-alert
          title="Recipe参数说明"
          type="info"
          :closable="false"
          style="margin-top: 20px"
        >
          <template #default>
            <div style="font-size: 13px; line-height: 1.8">
              • <span style="color: var(--mes-ink); font-weight: 600">目标值</span>：设备加工的标准参数<br />
              • <span style="color: var(--mes-red); font-weight: 600">上限/下限</span>：参数合格范围<br />
              • <span style="color: var(--mes-red); font-weight: 600">关键参数</span>：超出范围直接判定NG<br />
              • Recipe版本管理：同一产品/工序/设备可有多个版本，只有ACTIVE版本生效
            </div>
          </template>
        </el-alert>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import { getRecipeList, getRecipeDetail } from '@/api/recipe'

const loading = ref(false)
const paramsLoading = ref(false)
const recipeList = ref([])
const drawerVisible = ref(false)
const selectedRecipe = ref({})
const recipeParams = ref([])

const queryForm = reactive({
  productCode: '',
  stepCode: '',
  equipmentCode: ''
})

// 获取Recipe列表
const fetchRecipeList = async () => {
  loading.value = true
  try {
    const params = {
      current: 1,
      size: 100,
      ...queryForm
    }
    const data = await getRecipeList(params)
    // 后端返回IPage分页对象，取records数组
    recipeList.value = data?.records || []
  } catch (error) {
    console.error('获取Recipe列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  fetchRecipeList()
}

// 重置
const handleReset = () => {
  queryForm.productCode = ''
  queryForm.stepCode = ''
  queryForm.equipmentCode = ''
  fetchRecipeList()
}

// 查看参数详情
const handleViewParams = async (row) => {
  selectedRecipe.value = row
  drawerVisible.value = true

  paramsLoading.value = true
  try {
    const data = await getRecipeDetail(row.id)
    recipeParams.value = data.params || []
  } catch (error) {
    console.error('获取Recipe参数失败:', error)
  } finally {
    paramsLoading.value = false
  }
}

onMounted(() => {
  fetchRecipeList()
})
</script>
