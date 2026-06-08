# SmartDisplay MES SN 绑定追溯增量

## 背景

上一版多入口追溯已经支持 `AUTO/LOT/SN/ORDER/EQUIPMENT/MATERIAL_BATCH/DEFECT_CODE`，但 SN 入口仍然主要依赖 `LOT...-SN...` 字符串格式反推 Lot。该方式可以演示，但不是生产级追溯，因为系统没有真实记录“SN 属于哪个 Lot、工单、产品、产线”的绑定事实。

## 本轮落地

- 新增 Flyway 迁移 `V1.42__Add_Production_Serial_Numbers.sql`，建立 `prod_sn` 生产 SN 绑定表。
- `prod_sn` 记录 `sn`、`lot_no`、`order_no`、`product_code`、`line_code`、`sequence_no`、`grade`、`status`、`bind_time`、创建人与时间。
- 种子迁移会根据现有 `prod_lot.qty` 为每个 Lot 生成完整 SN，保持 `LOT202406001-SN001` 等旧探活编号可用。
- 工单释放时，每个新 Lot 会按 Lot 数量生成对应 SN，释放审计快照新增 `createdSnCount`。
- `traceLot` 返回 `serialNumbers` 和 `serialNumberSummary`，默认返回前 100 个 SN，并提供总数、首个 SN、是否截断。
- `traceSn` 优先查询 `prod_sn`，未命中时才按旧 `LOT...-SN...` 格式做兼容解析。
- `traceSearch` 的 SN 候选也改为绑定表优先，并在 `impactSummary` 和 `relatedDimensions` 中补充 SN 数量和 SN 维度。
- 前端追溯页展示 SN 数量、首个 SN 和 SN 绑定证据卡。
- 前端契约校验新增追溯页 SN 证据检查，防止后续退化回纯格式解析展示。

## 验收点

- 工单释放生成 Lot 时，生成的 SN 数量必须等于新建 Lot 数量之和。
- 通过 `/api/v1/trace/sn/{sn}` 查询真实 SN 时，返回的 `sn` 信息必须来自 `prod_sn`。
- 通过 `/api/v1/trace/search?type=SN&keyword=...` 查询时，`query.resolvedType` 应为 `SN`，`selectedLotNo` 来自 SN 绑定。
- Lot 追溯必须包含 `serialNumbers` 和 `serialNumberSummary`。
- 前端追溯页必须显示 SN 数量和 SN 绑定证据。

## 边界说明

- 旧格式 `LOT...-SN...` 兼容解析仍保留，用于历史演示数据和探活脚本平滑过渡。
- 首版仍是单基地、单产线试点，不做多基地 SN 编码规则中心和真实 ERP/MES 编码服务联调。
- 该增量不声称复刻任何企业内部 MES，只按显示行业通用生产追溯模型补齐生产对象绑定关系。
