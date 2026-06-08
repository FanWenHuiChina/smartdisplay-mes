<template>
  <el-dialog
    v-model="visible"
    title="Track Out - Lot出站"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="Lot批次号">
        <el-input v-model="form.lotNo" disabled />
      </el-form-item>

      <el-form-item label="加工结果" prop="result">
        <el-radio-group v-model="form.result">
          <el-radio label="OK">
            <el-tag type="success">合格 (OK)</el-tag>
          </el-radio>
          <el-radio label="NG">
            <el-tag type="danger">不合格 (NG)</el-tag>
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="加工参数(JSON)">
        <el-input
          v-model="form.processParams"
          type="textarea"
          :rows="4"
          placeholder='{"thickness":"2.0","speed":"1500","temperature":"150"}'
        />
        <div style="color: var(--mes-weak); font-size: 12px; margin-top: 4px">
          提示：输入JSON格式的加工参数，如温度、速度、厚度等
        </div>
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          placeholder="请输入备注信息"
        />
      </el-form-item>

      <el-alert
        v-if="form.result === 'NG'"
        title="NG结果自动触发Hold"
        type="warning"
        :closable="false"
      >
        <template #default>
          <div style="font-size: 13px">
            Track Out结果为NG时，系统将自动Hold该Lot，阻止继续流转。<br />
            需要质量工程师处理异常后，通过Release操作放行。
          </div>
        </template>
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        确认Track Out
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { trackOut } from '@/api/lot'

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
  result: 'OK',
  processParams: '{"thickness":"2.0","speed":"1500","temperature":"150"}',
  remark: ''
})

const rules = {
  result: [{ required: true, message: '请选择加工结果', trigger: 'change' }]
}

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.lotData) {
    form.lotNo = props.lotData.lotNo
    form.result = 'OK'
    form.processParams = '{"thickness":"2.0","speed":"1500","temperature":"150"}'
    form.remark = ''
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

    // NG结果二次确认
    if (form.result === 'NG') {
      await ElMessageBox.confirm(
        'NG结果将自动触发Hold，Lot将无法继续流转。确认提交吗？',
        '确认操作',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    }

    loading.value = true
    const params = {
      result: form.result,
      processParams: form.processParams,
      remark: form.remark
    }

    await trackOut(form.lotNo, params)

    if (form.result === 'NG') {
      ElMessage.warning('Track Out成功，Lot已自动Hold')
    } else {
      ElMessage.success('Track Out成功')
    }

    emit('success')
    handleClose()
  } catch (error) {
    if (error === 'cancel') {
      // 用户取消确认
      return
    }
    if (error.errors) {
      // 表单校验失败
      return
    }
    console.error('Track Out失败:', error)
  } finally {
    loading.value = false
  }
}
</script>
