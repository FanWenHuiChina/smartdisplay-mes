<template>
  <el-dialog
    v-model="visible"
    title="Hold Lot - 暂停流转"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="Lot批次号">
        <el-input v-model="form.lotNo" disabled />
      </el-form-item>

      <el-form-item label="Hold原因" prop="holdReason">
        <el-input
          v-model="form.holdReason"
          type="textarea"
          :rows="3"
          placeholder="请输入Hold原因"
        />
      </el-form-item>

      <el-form-item label="Hold类型" prop="holdType">
        <el-select v-model="form.holdType" placeholder="请选择Hold类型" style="width: 100%">
          <el-option label="QUALITY - 质量异常" value="QUALITY" />
          <el-option label="EQUIPMENT - 设备故障" value="EQUIPMENT" />
          <el-option label="MATERIAL - 物料问题" value="MATERIAL" />
          <el-option label="ENGINEERING - 工程变更" value="ENGINEERING" />
        </el-select>
      </el-form-item>

      <el-form-item label="Hold操作人">
        <el-input v-model="form.holdBy" placeholder="请输入操作人" />
      </el-form-item>

      <el-alert
        title="Hold机制说明"
        type="warning"
        :closable="false"
      >
        <template #default>
          <div style="font-size: 13px; line-height: 1.8">
            • Hold后Lot状态变为HOLD，holdFlag=1<br />
            • Track In校验会阻止Hold状态的Lot进站<br />
            • 需要通过Release操作才能恢复流转<br />
            • Hold记录会完整保存到lot_hold_record表
          </div>
        </template>
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="warning" :loading="loading" @click="handleSubmit">
        确认Hold
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { holdLot } from '@/api/lot'

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

const form = reactive({
  lotNo: '',
  holdReason: '',
  holdType: 'QUALITY',
  holdBy: 'QC01'
})

const rules = {
  holdReason: [{ required: true, message: '请输入Hold原因', trigger: 'blur' }],
  holdType: [{ required: true, message: '请选择Hold类型', trigger: 'change' }]
}

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.lotData) {
    form.lotNo = props.lotData.lotNo
    form.holdReason = ''
    form.holdType = 'QUALITY'
    form.holdBy = 'QC01'
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

    await ElMessageBox.confirm(
      `确认Hold Lot ${form.lotNo}？Hold后该Lot将无法继续流转。`,
      '确认操作',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    loading.value = true
    const params = {
      holdReason: form.holdReason,
      holdType: form.holdType,
      holdBy: form.holdBy
    }

    await holdLot(form.lotNo, params)

    ElMessage.success('Hold成功')
    emit('success')
    handleClose()
  } catch (error) {
    if (error === 'cancel') {
      return
    }
    if (error.errors) {
      return
    }
    console.error('Hold失败:', error)
  } finally {
    loading.value = false
  }
}
</script>
