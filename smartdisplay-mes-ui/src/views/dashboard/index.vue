<template>
  <div>
    <!-- Lot状态分布卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background-color: var(--mes-soft-2)">
              <el-icon :size="32"><Box /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-title">READY</div>
              <div class="stat-value">{{ lotStats.ready }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background-color: var(--mes-soft-2)">
              <el-icon :size="32"><Loading /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-title">PROCESSING</div>
              <div class="stat-value">{{ lotStats.processing }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background-color: var(--mes-soft-2)">
              <el-icon :size="32"><WarningFilled /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-title">HOLD</div>
              <div class="stat-value">{{ lotStats.hold }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background-color: var(--mes-soft-2)">
              <el-icon :size="32"><CircleCheck /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-title">COMPLETED</div>
              <div class="stat-value">{{ lotStats.completed }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 设备状态和良率趋势 -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div style="font-weight: 500">设备状态</div>
          </template>
          <div class="equipment-list">
            <div v-for="eq in equipmentList" :key="eq.code" class="equipment-item">
              <div class="equipment-info">
                <div class="equipment-name">{{ eq.name }}</div>
                <div class="equipment-code">{{ eq.code }}</div>
              </div>
              <el-tag :type="getEquipmentTagType(eq.status)">{{ eq.status }}</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div style="font-weight: 500">近7日良率趋势</div>
          </template>
          <div ref="chartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'

const lotStats = ref({
  ready: 0,
  processing: 0,
  hold: 0,
  completed: 0
})

const equipmentList = ref([
  { code: 'COATER_01', name: '涂布机01', status: 'IDLE' },
  { code: 'EXPOSE_01', name: '曝光机01', status: 'RUNNING' },
  { code: 'DEVELOP_01', name: '显影机01', status: 'IDLE' },
  { code: 'ETCH_01', name: '蚀刻机01', status: 'RUNNING' },
  { code: 'EVAP_01', name: '蒸镀机01', status: 'ALARM' }
])

const chartRef = ref(null)

const getEquipmentTagType = (status) => {
  const typeMap = {
    'IDLE': 'success',
    'RUNNING': 'warning',
    'ALARM': 'danger',
    'DOWN': 'info'
  }
  return typeMap[status] || 'info'
}

const initChart = () => {
  const chart = echarts.init(chartRef.value)
  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['06-01', '06-02', '06-03', '06-04', '06-05', '06-06', '06-07']
    },
    yAxis: {
      type: 'value',
      min: 90,
      max: 100,
      axisLabel: {
        formatter: '{value}%'
      }
    },
    series: [
      {
        name: '良率',
        type: 'line',
        data: [95.2, 96.1, 94.8, 97.3, 96.5, 95.9, 96.8],
        smooth: true,
        lineStyle: {
          color: '#292925',
          width: 2
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(41, 41, 37, 0.16)' },
              { offset: 1, color: 'rgba(41, 41, 37, 0.03)' }
            ]
          }
        }
      }
    ]
  }
  chart.setOption(option)
}

// 模拟获取Lot统计数据
const fetchLotStats = () => {
  // TODO: 后续改为真实API调用
  lotStats.value = {
    ready: 3,
    processing: 2,
    hold: 2,
    completed: 3
  }
}

onMounted(() => {
  fetchLotStats()
  initChart()
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mes-ink);
  margin-right: 16px;
}

.stat-content {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  color: var(--mes-weak);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--mes-text);
}

.equipment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 300px;
  overflow-y: auto;
}

.equipment-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background-color: var(--mes-paper-muted);
  border-radius: 4px;
}

.equipment-info {
  flex: 1;
}

.equipment-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--mes-text);
  margin-bottom: 4px;
}

.equipment-code {
  font-size: 12px;
  color: var(--mes-weak);
}
</style>
