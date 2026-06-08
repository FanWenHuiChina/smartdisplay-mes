# SmartDisplay MES CI 门禁说明

更新时间：2026-06-08

## 目标

本轮新增 GitHub Actions 工作流 `.github/workflows/ci.yml`，把当前本地交付验证沉淀为远程仓库门禁。目标是让每次推送和 Pull Request 都能自动验证后端、前端、迁移脚本和交付脚本的基本健康状态，降低“本地能跑、远程不可复验”的风险。

## 触发条件

- `push` 到 `main`
- 针对 `main` 的 `pull_request`

同一分支的重复运行会自动取消旧任务，避免频繁推送时浪费 runner。

## Job 划分

| Job | 运行环境 | 验证内容 |
| --- | --- | --- |
| `backend` | `windows-latest` | `mvn -B test`、`mvn -B -DskipTests "-Dspring-boot.repackage.skip=true" package` |
| `frontend` | `windows-latest` | `npm ci`、`npm run verify:frontend-contract`、`npm run build`、`npm run verify:production-bundle` |
| `delivery-gates` | `windows-latest` | PowerShell 脚本语法检查、Flyway 迁移静态校验、Docker Compose 配置解析 |

## 不覆盖范围

- CI 不启动完整 Docker Compose 业务环境，真实数据库 API 闭环仍由 `tools/run-real-db-api-flow.ps1` 在可访问 Docker 的试点环境中执行。
- CI 不执行浏览器 E2E，原因是当前 E2E 依赖本机浏览器/CDP 环境；该验证继续保留为交付前本地/试点环境步骤。
- CI 不调用外部 AI 模型，也不连接真实 ERP/EAP/QMS/WMS。

## 与现有交付验证的关系

CI 是基础门禁，不替代生产级验收。完整交付前仍需执行：

```powershell
powershell -ExecutionPolicy Bypass -File tools\run-pilot-demo-script.ps1 -Mode Short
powershell -ExecutionPolicy Bypass -File tools\run-pilot-demo-script.ps1 -Mode Full
powershell -ExecutionPolicy Bypass -File tools\run-real-db-api-flow.ps1
```

CI 通过代表代码层、构建层、迁移文件和交付脚本语法具备基础可复验性；业务闭环是否满足试点上线标准，仍以 Docker 运行环境中的演示脚本、真实数据库闭环脚本、浏览器 E2E 和人工验收清单为准。
