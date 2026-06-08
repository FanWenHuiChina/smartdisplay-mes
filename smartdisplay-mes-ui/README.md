# SmartDisplay MES UI

Vue 3 + Vite + Element Plus 前端工作台，用于 SmartDisplay MES 生产级试点演示和验收。当前视觉基线为参考 Codex app 的浅色工作台：中性灰背景、轻边框、低阴影、低饱和按钮，业务状态色仅用于状态标签和风险提示。

## 常用命令

```bash
npm install
npm run verify:frontend-contract
npm run build
npm run verify:production-bundle
npm run e2e:browser
npm run dev
```

## 验收脚本

- `verify:frontend-contract`：静态检查路由、登录守卫、请求拦截、`/api/v1` API 封装、RBAC 菜单/按钮权限、关键页面接线和生产 mock fallback 禁用约束。
- `verify:production-bundle`：扫描生产构建产物，确认典型 mock/fallback 样例 Lot、工单、设备、Recipe、SOP、COA 编号不进入默认生产包。
- `e2e:browser`：使用本机 Chrome/Edge + CDP 运行真实浏览器 E2E，默认访问 `http://127.0.0.1:8888`。运行前需要 Docker Compose 或等价前后端服务已启动。

最新通过报告：`../docs/SmartDisplay-MES-browser-e2e-20260608-045147.md`。

## Mock Fallback

开发样例 fallback 默认仅在开发环境启用；生产构建默认关闭。生产环境如需临时离线演示，需要显式设置：

```bash
VITE_ENABLE_MOCK_FALLBACK=true npm run build
```

生产试点验收默认不启用该开关。
