import axios from 'axios'
import { ElMessage } from 'element-plus'

const AUTH_KEYS = ['token', 'username', 'realName', 'role', 'permissions']
let redirectingToLogin = false

const clearAuthState = () => {
  AUTH_KEYS.forEach(key => localStorage.removeItem(key))
}

const handleAuthError = (code, message) => {
  if (code === 401) {
    clearAuthState()
    if (!redirectingToLogin && window.location.pathname !== '/login') {
      redirectingToLogin = true
      ElMessage.error(message || '登录已过期，请重新登录')
      window.location.assign('/login')
      return
    }
  }

  if (code === 403) {
    ElMessage.error(message || '当前角色无权执行该操作')
    return
  }

  ElMessage.error(message || '操作失败')
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// 请求拦截
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 确保POST/PUT请求的Content-Type正确
    if (config.method === 'post' || config.method === 'put') {
      config.headers['Content-Type'] = 'application/json;charset=UTF-8'
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截
request.interceptors.response.use(
  response => {
    const { code, message, data } = response.data
    if (code !== 200) {
      handleAuthError(code, message)
      return Promise.reject(new Error(message))
    }
    return data
  },
  error => {
    const code = error.response?.data?.code || error.response?.status
    const message = error.response?.data?.message || error.message
    if (code === 401 || code === 403) {
      handleAuthError(code, message)
    } else {
      ElMessage.error(message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
