<template>
  <el-dialog
    v-model="visible"
    title="Scrap Lot"
    width="620px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="150px">
      <el-form-item label="Lot No">
        <el-input v-model="form.lotNo" disabled />
      </el-form-item>

      <el-form-item label="Reason" prop="reason">
        <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="MRB scrap reason" />
      </el-form-item>

      <el-form-item label="Responsibility" prop="responsibilityModule">
        <el-select v-model="form.responsibilityModule" placeholder="Select module" style="width: 100%">
          <el-option label="QUALITY" value="QUALITY" />
          <el-option label="PROCESS" value="PROCESS" />
          <el-option label="EQUIPMENT" value="EQUIPMENT" />
          <el-option label="MATERIAL" value="MATERIAL" />
          <el-option label="PLANNING" value="PLANNING" />
        </el-select>
      </el-form-item>

      <el-form-item label="Approver" prop="approver">
        <el-input v-model="form.approver" placeholder="MRB approver" />
      </el-form-item>

      <el-form-item label="Operator" prop="operator">
        <el-input v-model="form.operator" placeholder="Operator" />
      </el-form-item>

      <el-form-item :label="`Type ${expectedConfirmText}`" prop="confirmText">
        <el-input v-model="form.confirmText" :placeholder="expectedConfirmText" />
      </el-form-item>

      <el-alert
        title="Scrap is irreversible in the pilot flow. The backend requires second confirmation and full audit fields."
        type="error"
        :closable="false"
      />
    </el-form>

    <template #footer>
      <el-button @click="handleClose">Cancel</el-button>
      <el-button type="danger" :loading="loading" @click="handleSubmit">
        Confirm Scrap
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { scrapLot } from '@/api/lot'

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
  reason: '',
  responsibilityModule: 'QUALITY',
  approver: '',
  operator: '',
  confirmText: ''
})

const expectedConfirmText = computed(() => `SCRAP:${form.lotNo || props.lotData?.lotNo || ''}`)

const rules = {
  reason: [{ required: true, message: 'Enter scrap reason', trigger: 'blur' }],
  responsibilityModule: [{ required: true, message: 'Select responsibility module', trigger: 'change' }],
  approver: [{ required: true, message: 'Enter approver', trigger: 'blur' }],
  operator: [{ required: true, message: 'Enter operator', trigger: 'blur' }],
  confirmText: [
    { required: true, message: 'Enter confirmation text', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== expectedConfirmText.value) {
          callback(new Error(`Type ${expectedConfirmText.value}`))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function resetForm() {
  form.lotNo = props.lotData?.lotNo || ''
  form.reason = ''
  form.responsibilityModule = 'QUALITY'
  form.approver = localStorage.getItem('username') || 'qe'
  form.operator = localStorage.getItem('username') || 'qe'
  form.confirmText = ''
  formRef.value?.clearValidate()
}

function handleClose() {
  visible.value = false
  resetForm()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
    loading.value = true
    await scrapLot(form.lotNo, {
      scrapConfirmed: true,
      confirmText: form.confirmText,
      reason: form.reason,
      responsibilityModule: form.responsibilityModule,
      approver: form.approver,
      operator: form.operator
    })
    ElMessage.success('Scrap submitted')
    emit('success')
    handleClose()
  } catch (error) {
    if (error?.errors) return
    console.warn('Scrap failed', error)
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, value => {
  visible.value = value
  if (value) resetForm()
})

watch(visible, value => {
  emit('update:modelValue', value)
})
</script>
