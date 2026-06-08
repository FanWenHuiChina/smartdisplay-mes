<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <div class="login-logo">MES</div>
          <div>
            <h2>SmartDisplay MES</h2>
            <p>新型显示制造执行系统</p>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            style="width: 100%"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="test-accounts">
        <p>测试账号：</p>
        <div class="account-grid">
          <span v-for="account in testAccounts" :key="account.username">
            {{ account.role }}：{{ account.username }} / 123456
          </span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const testAccounts = [
  { role: '管理员', username: 'admin' },
  { role: '计划员', username: 'planner' },
  { role: '操作员', username: 'operator' },
  { role: '质量工程师', username: 'qe' },
  { role: '工艺工程师', username: 'pe' },
  { role: '设备工程师', username: 'ee' }
]

const handleLogin = async () => {
  try {
    await formRef.value.validate()

    loading.value = true
    const data = await login(form)

    // 保存Token和用户信息到localStorage
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('realName', data.realName)
    localStorage.setItem('role', data.role)
    localStorage.setItem('permissions', JSON.stringify(data.permissions || {}))

    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    if (error.errors) {
      return
    }
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--mes-page);
  padding: 24px;
}

.login-card {
  width: 420px;
  border: 1px solid var(--mes-line);
  border-radius: 8px;
  box-shadow: var(--mes-shadow);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: left;
}

.login-logo {
  width: 38px;
  height: 38px;
  border-radius: 7px;
  border: 1px solid var(--mes-line);
  background: var(--mes-soft);
  display: grid;
  place-items: center;
  color: var(--mes-ink);
  font-weight: 700;
  font-size: 12px;
}

.card-header h2 {
  margin: 0;
  color: var(--mes-text);
  font-size: 20px;
  line-height: 1.2;
  font-weight: 700;
}

.card-header p {
  margin: 5px 0 0;
  color: var(--mes-sub);
  font-size: 13px;
}

.test-accounts {
  margin-top: 20px;
  padding: 12px;
  background-color: var(--mes-soft);
  border: 1px solid var(--mes-line-soft);
  border-radius: 7px;
  color: var(--mes-sub);
  font-size: 12px;
  line-height: 1.8;
}

.test-accounts p {
  margin: 0 0 8px;
  color: var(--mes-sub);
  font-size: 13px;
  font-weight: 600;
}

.account-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 12px;
}

.account-grid span {
  min-width: 0;
  white-space: nowrap;
}

@media (max-width: 520px) {
  .account-grid {
    grid-template-columns: 1fr;
  }
}
</style>
