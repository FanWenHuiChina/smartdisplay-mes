# Week 3-4 前端开发计划

## 背景

- Lot后端模块已在Week 2提前完成
- 纯API无法直观演示，面试需要可视化界面
- 国内MES行业主流：Vue 3 + Element Plus

---

## 技术栈

```json
{
  "vue": "^3.4",
  "vite": "^5.2",
  "element-plus": "^2.7",
  "axios": "^1.7",
  "echarts": "^5.5",
  "pinia": "^2.1",
  "vue-router": "^4.3",
  "@element-plus/icons-vue": "^2.3"
}
```

---

## 项目结构

```
smartdisplay-mes-ui/
├── public/
├── src/
│   ├── api/                    # Axios接口封装
│   │   ├── request.js          # Axios实例配置
│   │   ├── lot.js              # Lot接口
│   │   └── recipe.js           # Recipe接口
│   ├── assets/                 # 静态资源
│   ├── components/             # 公共组件
│   │   ├── LotStatusTag.vue    # Lot状态标签
│   │   └── EquipmentCard.vue   # 设备状态卡片
│   ├── router/
│   │   └── index.js            # 路由配置
│   ├── stores/                 # Pinia状态管理
│   │   ├── user.js
│   │   └── lot.js
│   ├── views/
│   │   ├── layout/
│   │   │   └── MainLayout.vue  # 主布局（侧边栏+顶栏）
│   │   ├── dashboard/
│   │   │   └── index.vue       # 首页看板
│   │   ├── lot/
│   │   │   ├── index.vue       # Lot列表页
│   │   │   └── components/
│   │   │       ├── TrackInDialog.vue
│   │   │       ├── TrackOutDialog.vue
│   │   │       ├── HoldDialog.vue
│   │   │       └── ReleaseDialog.vue
│   │   └── recipe/
│   │       ├── index.vue       # Recipe列表页
│   │       └── components/
│   │           └── ParamDrawer.vue  # 参数详情抽屉
│   ├── App.vue
│   └── main.js
├── index.html
├── vite.config.js
└── package.json
```

---

## Week 3 任务清单

### Day 1：项目初始化
- [ ] `npm create vite@latest smartdisplay-mes-ui -- --template vue`
- [ ] 安装依赖：Element Plus、Axios、Vue Router、Pinia、ECharts
- [ ] 配置vite.config.js代理（/api -> http://localhost:8080）
- [ ] 配置Element Plus自动导入

### Day 2：路由和布局
- [ ] MainLayout主布局
  - 左侧菜单：Dashboard / Lot管理 / Recipe管理
  - 顶部导航栏（系统名称、用户信息）
- [ ] 配置路由
  ```js
  const routes = [
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: Dashboard },
        { path: 'lot', component: LotList },
        { path: 'recipe', component: RecipeList }
      ]
    }
  ]
  ```

### Day 3-4：Dashboard首页
- [ ] Lot状态分布卡片（4张卡片）
  - READY数量（蓝色）
  - PROCESSING数量（绿色）
  - HOLD数量（红色）
  - COMPLETED数量（灰色）
  - 接口：`GET /api/lots?status=READY` 统计count
- [ ] 设备状态卡片（5张卡片）
  - 设备编码 + 名称
  - 状态色块（IDLE绿色 / RUNNING黄色 / ALARM红色）
  - 接口：`GET /api/equipment`
- [ ] 良率趋势图（ECharts折线图）
  - X轴：近7日日期
  - Y轴：良率百分比
  - 数据：模拟数据（后续Week 6接入真实API）

### Day 5：Lot列表页
- [ ] 表格展示
  - 列：lotNo / productCode / currentStep / currentEquipment / status / qty / 操作
  - 状态用Element Tag展示（HOLD红色高亮）
- [ ] 查询表单
  - lotNo模糊查询
  - status下拉筛选
- [ ] 操作按钮（4个）
  - Track In（status=READY时可用）
  - Track Out（status=PROCESSING时可用）
  - Hold（status=READY或PROCESSING时可用）
  - Release（status=HOLD时可用）

---

## Week 4 任务清单

### Day 1-2：Track In/Out弹窗
- [ ] **TrackInDialog.vue**
  ```vue
  <el-form :model="form">
    <el-form-item label="Lot批次号">
      <el-input v-model="form.lotNo" disabled />
    </el-form-item>
    <el-form-item label="工序编码" required>
      <el-select v-model="form.stepCode">
        <el-option label="COATING" value="COATING" />
        <el-option label="EXPOSE" value="EXPOSE" />
        <!-- 后续从后端加载 -->
      </el-select>
    </el-form-item>
    <el-form-item label="设备编码" required>
      <el-select v-model="form.equipmentCode">
        <el-option label="COATER_01" value="COATER_01" />
        <!-- 后续从后端加载 -->
      </el-select>
    </el-form-item>
    <el-form-item label="操作员">
      <el-input v-model="form.operator" />
    </el-form-item>
  </el-form>
  ```
  - 提交：`POST /api/lots/{lotNo}/track-in`
  - 成功后刷新列表，ElMessage提示

- [ ] **TrackOutDialog.vue**
  ```vue
  <el-form :model="form">
    <el-form-item label="加工结果" required>
      <el-radio-group v-model="form.result">
        <el-radio label="OK">合格</el-radio>
        <el-radio label="NG">不合格</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="加工参数(JSON)">
      <el-input type="textarea" v-model="form.processParams"
        placeholder='{"thickness":"2.0","speed":"1500"}' />
    </el-form-item>
    <el-form-item label="备注">
      <el-input v-model="form.remark" />
    </el-form-item>
  </el-form>
  ```
  - 提交：`POST /api/lots/{lotNo}/track-out`
  - NG结果提示"Track Out成功，Lot已自动Hold"

### Day 3：Hold/Release弹窗
- [ ] **HoldDialog.vue**
  ```vue
  <el-form :model="form">
    <el-form-item label="Hold原因" required>
      <el-input v-model="form.holdReason" />
    </el-form-item>
    <el-form-item label="Hold类型">
      <el-select v-model="form.holdType">
        <el-option label="质量异常" value="QUALITY" />
        <el-option label="设备故障" value="EQUIPMENT" />
        <el-option label="物料问题" value="MATERIAL" />
        <el-option label="工程变更" value="ENGINEERING" />
      </el-select>
    </el-form-item>
    <el-form-item label="Hold操作人">
      <el-input v-model="form.holdBy" />
    </el-form-item>
  </el-form>
  ```

- [ ] **ReleaseDialog.vue**
  ```vue
  <el-form :model="form">
    <el-form-item label="处置结果" required>
      <el-input v-model="form.disposition"
        placeholder="rework / scrap / ship" />
    </el-form-item>
    <el-form-item label="Release操作人">
      <el-input v-model="form.releaseBy" />
    </el-form-item>
  </el-form>
  ```

### Day 4：Recipe管理页
- [ ] Recipe列表表格
  - 列：recipeCode / productCode / stepCode / equipmentCode / version / status
  - status用Tag展示（ACTIVE绿色 / INACTIVE灰色）
- [ ] 查询表单
  - productCode / stepCode / equipmentCode 联动查询
- [ ] 参数详情按钮
  - 点击打开抽屉，展示Recipe所有参数

- [ ] **ParamDrawer.vue**
  ```vue
  <el-descriptions :column="2" border>
    <el-descriptions-item label="参数名">温度</el-descriptions-item>
    <el-descriptions-item label="目标值">180.0</el-descriptions-item>
    <el-descriptions-item label="上限">185.0</el-descriptions-item>
    <el-descriptions-item label="下限">175.0</el-descriptions-item>
    <el-descriptions-item label="单位">℃</el-descriptions-item>
  </el-descriptions>
  ```
  - 接口：`GET /api/recipes/{id}`

### Day 5：联调测试
- [ ] 完整流程测试
  1. Dashboard看到READY=2
  2. 进入Lot页，选LOT202406001
  3. Track In → 成功，刷新后status=PROCESSING
  4. Track Out(NG) → 成功，刷新后status=HOLD且行高亮红色
  5. Release → 成功，刷新后status=READY
  6. 再次Track In → 成功
- [ ] 错误处理测试
  - Track In时Lot已Hold → 提示"Lot状态不允许进站"
  - Recipe不存在 → 提示"Recipe不存在"
- [ ] 补充测试数据
  - 至少10条Lot（不同状态分布）
  - 至少5条Recipe

---

## API接口封装示例

### src/api/request.js
```js
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 响应拦截
request.interceptors.response.use(
  response => {
    const { code, message } = response.data
    if (code !== 200) {
      ElMessage.error(message || '操作失败')
      return Promise.reject(new Error(message))
    }
    return response.data
  },
  error => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
```

### src/api/lot.js
```js
import request from './request'

export const getLotList = (params) => {
  return request.get('/lots', { params })
}

export const trackIn = (lotNo, data) => {
  return request.post(`/lots/${lotNo}/track-in`, data)
}

export const trackOut = (lotNo, data) => {
  return request.post(`/lots/${lotNo}/track-out`, data)
}

export const holdLot = (lotNo, data) => {
  return request.post(`/lots/${lotNo}/hold`, data)
}

export const releaseLot = (lotNo, data) => {
  return request.post(`/lots/${lotNo}/release`, data)
}
```

---

## Vite代理配置

### vite.config.js
```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

---

## 验收标准

### Week 3结束
- ✅ Dashboard展示Lot状态卡片（4个）、设备状态卡片（5个）、良率趋势图
- ✅ Lot列表页展示所有Lot，操作按钮根据状态动态启用/禁用
- ✅ Track In/Out弹窗UI完成，可提交（无需测试成功）

### Week 4结束
- ✅ 完整流程可在界面操作：Track In → Track Out(NG) → 自动Hold → Release → 再次Track In
- ✅ Hold状态的Lot行高亮红色
- ✅ Recipe列表页展示所有Recipe，参数详情抽屉可打开
- ✅ 所有接口错误有ElMessage提示

---

## 演示准备

### 测试数据要求
```sql
-- 至少10条Lot，状态分布：
-- READY: 3条
-- PROCESSING: 2条
-- HOLD: 2条
-- COMPLETED: 3条

-- 至少5条Recipe，状态分布：
-- ACTIVE: 4条
-- INACTIVE: 1条

-- 至少5条设备，状态分布：
-- IDLE: 2台
-- RUNNING: 2台
-- ALARM: 1台
```

### 演示脚本（3分钟）
1. 打开http://localhost:5173，展示Dashboard（10秒）
2. 进入Lot管理，Track In操作（30秒）
3. Track Out(NG)，观察自动Hold（30秒）
4. Release放行（20秒）
5. 进入Recipe管理，查看参数详情（20秒）
6. 总结："前后端完整的MES核心流程，6层校验、自动Hold、可视化操作"（10秒）

---

## 常见问题

**Q1: Element Plus组件库太大？**
A: 使用自动导入插件`unplugin-vue-components`，按需加载

**Q2: ECharts图表不显示？**
A: 确保容器有明确高度，`<div style="height: 300px">`

**Q3: 跨域问题？**
A: 检查vite.config.js proxy配置，确保target指向后端地址

**Q4: 接口404？**
A: 检查后端是否启动在8080端口，路径是否有/api前缀

---

## 下一步（Week 5）

- 质量管理模块后端开发
- 前端补充质检页面
- Dashboard接入真实良率数据
