<template>
  <el-dialog
    v-model="visible"
    title="Track In - Lot进站"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="Lot批次号">
        <el-input v-model="form.lotNo" disabled />
      </el-form-item>

      <el-form-item label="工序编码" prop="stepCode">
        <el-select v-model="form.stepCode" placeholder="请选择工序" style="width: 100%">
          <el-option
            v-for="step in processSteps"
            :key="step.stepCode"
            :label="`${step.stepCode} - ${step.stepName}`"
            :value="step.stepCode"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="设备编码" prop="equipmentCode">
        <el-select v-model="form.equipmentCode" placeholder="请选择设备" style="width: 100%">
          <el-option
            v-for="equip in equipments"
            :key="equip.equipmentCode"
            :label="`${equip.equipmentCode} - ${equip.equipmentName}`"
            :value="equip.equipmentCode"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="操作员">
        <el-input v-model="form.operator" placeholder="请输入操作员" />
      </el-form-item>

      <el-alert
        title="Track In 8层校验"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
      >
        <template #default>
          <div style="font-size: 13px; line-height: 1.8">
            1. Lot状态校验（必须READY或REWORK）<br />
            2. Route防跳站校验（匹配当前待执行工序）<br />
            3. 设备状态校验（IDLE/RUNNING）<br />
            4. 设备能力校验（设备支持该工序）<br />
            5. Recipe校验（Recipe存在且ACTIVE）<br />
            6. Hold状态校验（Hold=0）<br />
            7. 班次校验（产线处于ACTIVE班次）<br />
            8. 物料齐套校验（关键物料可用）
          </div>
        </template>
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        确认Track In
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { trackIn } from '@/api/lot'
import { getProcessSteps, getEquipments } from '@/api/pilot'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  lotData: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(false)
const loading = ref(false)
const formRef = ref(null)

// 动态选项数据
const processSteps = ref([])
const equipments = ref([])

const form = reactive({
  lotNo: '',
  stepCode: '',
  equipmentCode: '',
  operator: 'Admin'
})

const rules = {
  stepCode: [{ required: true, message: '请选择工序', trigger: 'change' }],
  equipmentCode: [{ required: true, message: '请选择设备', trigger: 'change' }]
}

// 加载工序和设备选项
const loadOptions = async () => {
  try {
    const [steps, equips] = await Promise.all([
      getProcessSteps(),
      getEquipments()
    ])
    processSteps.value = steps || []
    equipments.value = equips || []
  } catch (error) {
    console.error('加载选项失败:', error)
  }
}

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.lotData) {
    form.lotNo = props.lotData.lotNo
    form.stepCode = props.lotData.currentStepCode || ''
    form.equipmentCode = ''
    form.operator = 'Admin'
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const handleClose = () => {
  visible.value = false
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()

    loading.value = true
    const params = {
      stepCode: form.stepCode,
      equipmentCode: form.equipmentCode,
      operator: form.operator
    }

    await trackIn(form.lotNo, params)

    ElMessage.success('Track In成功')
    emit('success')
    handleClose()
  } catch (error) {
    if (error.errors) {
      // 表单校验失败
      return
    }
    console.error('Track In失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadOptions()
})
</script>
