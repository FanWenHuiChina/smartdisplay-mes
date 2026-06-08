# SmartDisplay MES 可运行演示脚本复验

更新时间：2026-06-08

## 结论

本轮把原有文档型演示脚本升级为真实 API 可复演脚本：`tools/run-pilot-demo-script.ps1`。脚本支持 `Short` 和 `Full` 两种模式，并会输出 Markdown/JSON 证据报告，便于现场演示、验收复盘和后续回归。

同时修正了 `tools/run-real-db-api-flow.ps1` 的两个验收风险：Flyway 最新版本不再硬编码历史值，设备选择不再只看设备能力，而是同时校验产品、工序、设备的有效 Recipe。

## 运行方式

```powershell
powershell -ExecutionPolicy Bypass -File tools\run-pilot-demo-script.ps1 -Mode Short
powershell -ExecutionPolicy Bypass -File tools\run-pilot-demo-script.ps1 -Mode Full
powershell -ExecutionPolicy Bypass -File tools\run-real-db-api-flow.ps1
```

前置条件：

- Docker Compose 中 PostgreSQL、后端、前端已启动。
- API 地址默认 `http://localhost:8080/api`。
- 默认账号为 `admin / 123456`。

## 覆盖范围

| 模式 | 覆盖能力 | 最新报告 |
| --- | --- | --- |
| Short | 登录、权限快照、生产总览、良率看板、工单创建/释放、Lot Track In/Out、Lot 全链路追溯、AI 良率日报 | `docs/SmartDisplay-MES-demo-script-short-20260608-221801.md/json` |
| Full | 自动 Hold、质量异常证据、Release、Rework、Scrap、WMS 齐套与库存事务、载具绑定/解绑与追溯、EAP 网关健康检查与消息入站、Hybrid Local 知识库索引、RAG SOP 问答、AI 设备异常分析、审计回看 | `docs/SmartDisplay-MES-demo-script-full-20260608-221801.md/json` |
| Real DB API Flow | PostgreSQL/Flyway 校验、工单释放、Track In/Out、NG 自动 Hold、Release、追溯、看板、AI 报告、审计落库 | `docs/SmartDisplay-MES-real-db-api-flow-20260608-221738.md` |

## 本轮验证

| 验证项 | 结果 | 说明 |
| --- | --- | --- |
| `run-pilot-demo-script.ps1` 语法检查 | 通过 | PowerShell Parser 可解析；脚本内部执行文案保持 ASCII，避免 Windows PowerShell 无 BOM 编码误读 |
| `run-real-db-api-flow.ps1` 语法检查 | 通过 | Flyway 版本检查改为读取迁移文件上限 |
| Short 演示链路 | 通过 | 生成 `SmartDisplay-MES-demo-script-short-20260608-221801.md/json` |
| Full 演示链路 | 通过 | 生成 `SmartDisplay-MES-demo-script-full-20260608-221801.md/json` |
| 真实数据库 API 闭环 | 通过 | 生成 `SmartDisplay-MES-real-db-api-flow-20260608-221738.md` |

## 修正点

- Flyway 验收不再固定期望 `1.38`，而是从 `smartdisplay-mes-api/src/main/resources/db/migration` 自动解析当前最高迁移版本。
- 演示脚本和真实数据库闭环脚本的设备选择逻辑改为优先选择“在线设备 + 工序能力 + 当前产品/工序/设备有效 Recipe”的设备。
- 如果没有在线候选设备，脚本会退回到有效 Recipe 绑定设备，避免只因设备状态排序或数据库返回顺序导致演示误失败。
- 可复演演示脚本生成的报告包含关键对象、检查项和失败原因，适合作为 5 分钟/15 分钟演示后的验收证据。
