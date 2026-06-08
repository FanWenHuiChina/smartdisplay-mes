<template>
  <el-dialog
    v-model="visible"
    title="Release Lot - 放行"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="Lot批次号">
        <el-input v-model="form.lotNo" disabled />
      </el-form-item>

      <el-form-item label="处置结果" prop="disposition">
        <el-select v-model="form.disposition" placeholder="请选择处置结果" style="width: 100%">
          <el-option label="rework - 返工" value="rework" />
          <el-option label="scrap - 报废" value="scrap" />
          <el-option label="ship - 放行出货" value="ship" />
          <el-option label="continue - 继续流转" value="continue" />
        </el-select>
        <div style="color: var(--mes-weak); font-size: 12px; margin-top: 4px">
          选择异常处理后的处置方式
        </div>
      </el-form-item>

      <el-form-item label="Release操作人">
        <el-input v-model="form.releaseBy" placeholder="请输入操作人" />
      </el-form-item>

      <el-alert
        title="Release机制说明"
        type="success"
        :closable="false"
      >
        <template #default>
          <div style="font-size: 13px; line-height: 1.8">
            • Release后Lot状态恢复为READY，holdFlag=0<br />
            • 更新lot_hold_record表，记录releaseBy和releaseTime<br />
            • Hold记录的status字段从HOLD变更为RELEASED<br />
            • Release后Lot可以重新Track In继续流转
          </div>
        </template>
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="success" :loading="loading" @click="handleSubmit">
        确认Release
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { releaseLot } from '@/api/lot'

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
  disposition: 'continue',
  releaseBy: 'Engineer01'
})

const rules = {
  disposition: [{ required: true, message: '请选择处置结果', trigger: 'change' }]
}

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.lotData) {
    form.lotNo = props.lotData.lotNo
    form.disposition = 'continue'
    form.releaseBy = 'Engineer01'
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
      disposition: form.disposition,
      releaseBy: form.releaseBy
    }

    await releaseLot(form.lotNo, params)

    ElMessage.success('Release成功，Lot已恢复流转')
    emit('success')
    handleClose()
  } catch (error) {
    if (error.errors) {
      return
    }
    console.error('Release失败:', error)
  } finally {
    loading.value = false
  }
}
</script>
