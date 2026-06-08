<template>
  <el-dialog
    v-model="visible"
    title="Rework Lot"
    width="620px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
      <el-form-item label="Lot No">
        <el-input v-model="form.lotNo" disabled />
      </el-form-item>

      <el-form-item label="Rework Route" prop="reworkRouteCode">
        <el-select v-model="form.reworkRouteCode" placeholder="Select active route" style="width: 100%" @change="handleRouteChange">
          <el-option
            v-for="route in routes"
            :key="route.routeCode"
            :label="`${route.routeCode} / ${route.productCode || '-'}`"
            :value="route.routeCode"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="Start Step" prop="reworkStepCode">
        <el-select v-model="form.reworkStepCode" placeholder="Select rework start step" style="width: 100%">
          <el-option
            v-for="step in routeSteps"
            :key="step"
            :label="step"
            :value="step"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="Reason" prop="reason">
        <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="MRB rework reason" />
      </el-form-item>

      <el-form-item label="Operator" prop="operator">
        <el-input v-model="form.operator" placeholder="Operator" />
      </el-form-item>

      <el-alert
        title="Rework requires an active route and a rework-allowed start step. The backend will reject invalid route or step combinations."
        type="warning"
        :closable="false"
      />
    </el-form>

    <template #footer>
      <el-button @click="handleClose">Cancel</el-button>
      <el-button type="warning" :loading="loading" @click="handleSubmit">
        Confirm Rework
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { reworkLot } from '@/api/lot'
import { getRoutes } from '@/api/pilot'

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
const routes = ref([])

const form = reactive({
  lotNo: '',
  reworkRouteCode: '',
  reworkStepCode: '',
  reason: '',
  operator: ''
})

const rules = {
  reworkRouteCode: [{ required: true, message: 'Select rework route', trigger: 'change' }],
  reworkStepCode: [{ required: true, message: 'Select rework start step', trigger: 'change' }],
  reason: [{ required: true, message: 'Enter rework reason', trigger: 'blur' }],
  operator: [{ required: true, message: 'Enter operator', trigger: 'blur' }]
}

const selectedRoute = computed(() => routes.value.find(route => route.routeCode === form.reworkRouteCode))
const routeSteps = computed(() => selectedRoute.value?.steps || [])

async function loadRoutes() {
  try {
    routes.value = await getRoutes() || []
    const matched = routes.value.find(route => route.productCode === props.lotData?.productCode) || routes.value[0]
    if (matched) {
      form.reworkRouteCode = matched.routeCode
      form.reworkStepCode = props.lotData?.currentStepCode && matched.steps?.includes(props.lotData.currentStepCode)
        ? props.lotData.currentStepCode
        : matched.steps?.[0] || ''
    }
  } catch (error) {
    routes.value = []
    console.warn('Load routes failed', error)
  }
}

function resetForm() {
  form.lotNo = props.lotData?.lotNo || ''
  form.reworkRouteCode = ''
  form.reworkStepCode = ''
  form.reason = ''
  form.operator = localStorage.getItem('username') || 'qe'
  formRef.value?.clearValidate()
}

function handleRouteChange() {
  form.reworkStepCode = routeSteps.value.includes(form.reworkStepCode) ? form.reworkStepCode : routeSteps.value[0] || ''
}

function handleClose() {
  visible.value = false
  resetForm()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
    loading.value = true
    await reworkLot(form.lotNo, {
      reworkRouteCode: form.reworkRouteCode,
      reworkStepCode: form.reworkStepCode,
      reason: form.reason,
      operator: form.operator
    })
    ElMessage.success('Rework submitted')
    emit('success')
    handleClose()
  } catch (error) {
    if (error?.errors) return
    console.warn('Rework failed', error)
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, async (value) => {
  visible.value = value
  if (value) {
    resetForm()
    await loadRoutes()
  }
})

watch(visible, value => {
  emit('update:modelValue', value)
})
</script>
