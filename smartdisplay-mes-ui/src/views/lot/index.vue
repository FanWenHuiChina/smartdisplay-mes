<template>
  <div>
    <!-- 查询表单 -->
    <el-card shadow="hover" style="margin-bottom: 20px">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="Lot批次号">
          <el-input v-model="queryForm.lotNo" placeholder="请输入Lot批次号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option label="READY" value="READY" />
            <el-option label="PROCESSING" value="PROCESSING" />
            <el-option label="HOLD" value="HOLD" />
            <el-option label="COMPLETED" value="COMPLETED" />
            <el-option label="REWORK" value="REWORK" />
            <el-option label="SCRAP" value="SCRAP" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery" :icon="Search">查询</el-button>
          <el-button @click="handleReset" :icon="Refresh">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Lot列表 -->
    <el-card shadow="hover">
      <el-table :data="lotList" v-loading="loading" stripe :row-class-name="tableRowClassName">
        <el-table-column prop="lotNo" label="Lot批次号" width="140" fixed />
        <el-table-column prop="productCode" label="产品编码" width="120" />
        <el-table-column prop="currentStepCode" label="当前工序" width="130" />
        <el-table-column prop="currentEquipmentCode" label="当前设备" width="130" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="holdFlag" label="Hold标志" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.holdFlag === 1" type="danger" size="small">Hold</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="560" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              @click="handleTrackIn(row)"
              :disabled="!canTrackIn(row)"
              :icon="Right"
            >
              Track In
            </el-button>
            <el-button
              type="success"
              size="small"
              @click="handleTrackOut(row)"
              :disabled="!canTrackOut(row)"
              :icon="Check"
            >
              Track Out
            </el-button>
            <el-button
              type="warning"
              size="small"
              @click="handleHold(row)"
              :disabled="!canHold(row)"
              :icon="WarningFilled"
            >
              Hold
            </el-button>
            <el-button
              type="info"
              size="small"
              @click="handleRelease(row)"
              :disabled="!canRelease(row)"
              :icon="CircleCheck"
            >
              Release
            </el-button>
            <el-button
              type="warning"
              size="small"
              @click="handleRework(row)"
              :disabled="!canRework(row)"
              :icon="RefreshLeft"
            >
              Rework
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleScrap(row)"
              :disabled="!canScrap(row)"
              :icon="Delete"
            >
              Scrap
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="fetchLotList"
        @current-change="fetchLotList"
      />
    </el-card>

    <!-- Track In弹窗 -->
    <TrackInDialog
      v-model="trackInVisible"
      :lot-data="selectedLot"
      @success="handleOperationSuccess"
    />

    <!-- Track Out弹窗 -->
    <TrackOutDialog
      v-model="trackOutVisible"
      :lot-data="selectedLot"
      @success="handleOperationSuccess"
    />

    <!-- Hold弹窗 -->
    <HoldDialog
      v-model="holdVisible"
      :lot-data="selectedLot"
      @success="handleOperationSuccess"
    />

    <!-- Release弹窗 -->
    <ReleaseDialog
      v-model="releaseVisible"
      :lot-data="selectedLot"
      @success="handleOperationSuccess"
    />

    <ReworkDialog
      v-model="reworkVisible"
      :lot-data="selectedLot"
      @success="handleOperationSuccess"
    />

    <ScrapDialog
      v-model="scrapVisible"
      :lot-data="selectedLot"
      @success="handleOperationSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Right, Check, WarningFilled, CircleCheck, RefreshLeft, Delete } from '@element-plus/icons-vue'
import { getLotList } from '@/api/lot'
import { hasButton } from '@/utils/permissions'
import TrackInDialog from './components/TrackInDialog.vue'
import TrackOutDialog from './components/TrackOutDialog.vue'
import HoldDialog from './components/HoldDialog.vue'
import ReleaseDialog from './components/ReleaseDialog.vue'
import ReworkDialog from './components/ReworkDialog.vue'
import ScrapDialog from './components/ScrapDialog.vue'

const loading = ref(false)
const lotList = ref([])
const selectedLot = ref({})
const trackInVisible = ref(false)
const trackOutVisible = ref(false)
const holdVisible = ref(false)
const releaseVisible = ref(false)
const reworkVisible = ref(false)
const scrapVisible = ref(false)

const queryForm = reactive({
  lotNo: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 获取Lot列表
const fetchLotList = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.page,
      size: pagination.size,
      ...queryForm
    }
    const data = await getLotList(params)
    // 后端返回IPage分页对象
    lotList.value = data?.records || []
    pagination.total = data?.total || 0
  } catch (error) {
    console.error('获取Lot列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  pagination.page = 1
  fetchLotList()
}

// 重置
const handleReset = () => {
  queryForm.lotNo = ''
  queryForm.status = ''
  pagination.page = 1
  fetchLotList()
}

// 状态标签类型
const getStatusTagType = (status) => {
  const typeMap = {
    'READY': '',
    'PROCESSING': 'success',
    'HOLD': 'danger',
    'COMPLETED': 'info',
    'REWORK': 'warning',
    'SCRAP': 'danger'
  }
  return typeMap[status] || ''
}

// 行样式（Hold状态高亮）
const tableRowClassName = ({ row }) => {
  return row.status === 'HOLD' ? 'hold-row' : ''
}

// 操作权限判断
const canTrackIn = (row) => {
  return hasButton('lot:track-in') && row.status === 'READY' && row.holdFlag === 0
}

const canTrackOut = (row) => {
  return hasButton('lot:track-out') && row.status === 'PROCESSING'
}

const canHold = (row) => {
  return hasButton('lot:hold') && (row.status === 'READY' || row.status === 'PROCESSING') && row.holdFlag === 0
}

const canRelease = (row) => {
  return hasButton('lot:release') && row.status === 'HOLD' && row.holdFlag === 1
}

const canRework = (row) => {
  return hasButton('lot:rework') && row.status === 'HOLD' && row.holdFlag === 1
}

const canScrap = (row) => {
  return hasButton('lot:scrap') && row.status === 'HOLD' && row.holdFlag === 1
}

// 操作处理
const handleTrackIn = (row) => {
  selectedLot.value = row
  trackInVisible.value = true
}

const handleTrackOut = (row) => {
  selectedLot.value = row
  trackOutVisible.value = true
}

const handleHold = (row) => {
  selectedLot.value = row
  holdVisible.value = true
}

const handleRelease = (row) => {
  selectedLot.value = row
  releaseVisible.value = true
}

const handleRework = (row) => {
  selectedLot.value = row
  reworkVisible.value = true
}

const handleScrap = (row) => {
  selectedLot.value = row
  scrapVisible.value = true
}

// 操作成功回调
const handleOperationSuccess = () => {
  fetchLotList()
}

onMounted(() => {
  fetchLotList()
})
</script>

<style scoped>
:deep(.hold-row) {
  background-color: #fef0f0 !important;
}

:deep(.hold-row:hover > td) {
  background-color: #fde2e2 !important;
}
</style>
