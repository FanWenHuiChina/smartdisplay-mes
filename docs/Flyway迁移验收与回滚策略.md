# Flyway迁移验收与回滚策略

更新时间：2026-06-08

## 验收目标

- 确认 `smartdisplay-mes-api/src/main/resources/db/migration` 下迁移文件命名、版本号、顺序和文件内容符合试点交付要求。
- 在容器或生产试点环境执行变更前，先完成静态验收、构建验收和数据库备份确认。
- 回滚以“备份恢复 + 前向修复迁移”为主，不直接修改已执行的历史迁移脚本。

## 本地静态验收

```powershell
powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1
```

脚本会检查：

- 文件名必须满足 `V版本__描述.sql`。
- 版本号不能重复。
- 当前 `V1.x` 迁移版本必须连续。
- 迁移文件不能为空。

## 构建验收

```powershell
cd smartdisplay-mes-api
mvn.cmd -DskipTests -Dspring-boot.repackage.skip=true package
```

通过标准：

- Maven 编译成功。
- `target/classes/db/migration` 内包含最新迁移文件。
- Flyway 配置保持 `validate-on-migrate: true`。

## 全新库迁移演练

```powershell
powershell -ExecutionPolicy Bypass -File tools\run-flyway-rehearsal.ps1 -StartupTimeoutSec 180
```

脚本会执行：

- 先运行迁移静态验收，确认 `V1.1-V1.41` 文件命名、版本号和连续性。
- 重新打后端 exec jar，避免镜像或 jar 缺少最新迁移脚本。
- 启动临时 PostgreSQL 容器，从全新空库运行 Spring Boot + Flyway 自动迁移。
- 校验 `flyway_schema_history` 最新版本、应用启动状态、public 表数量、种子用户和 Route Step 种子数据。
- 使用 `pg_dump/pg_restore` 恢复到同容器内的新库，确认恢复库的最新 Flyway 版本和表数量一致。

2026-06-08 正式演练结果：

- 最新版本：`V1.38 Add Material Location Task Workflow`
- 迁移文件：38 个
- public 表：52 张
- 种子用户：7 个
- Route Step：16 条
- 恢复库最新版本：`V1.38`
- 报告：`docs/SmartDisplay-MES-flyway-rehearsal-20260608-052419.md`

后续 V1.40 供应商准入与8D整改迁移、V1.41 供应商准入周期复审任务迁移已通过静态验收；V1.39 BOM/ECO 会签迁移已通过静态验收和 Docker 容器数据库复验。

## 试点环境执行前检查

- 确认变更单包含迁移目的、影响表、回滚方案和验证 SQL。
- 确认 PostgreSQL 已完成备份，备份文件可恢复演练。
- 确认后端镜像版本、前端镜像版本和数据库迁移版本一一对应。
- 确认窗口期内没有进行中的 Lot 关键事务，避免业务动作与结构变更交叉。

## 回滚策略

1. 若迁移尚未执行：停止发布，撤回本次应用包和镜像。
2. 若迁移已执行但业务未放量：停止应用服务，恢复发布前数据库备份，再回退应用镜像。
3. 若迁移已执行且已有新数据写入：禁止手工改历史脚本；新增 `V下一版本__Fix_*.sql` 做前向修复，并保留数据修复审计。
4. 若仅为索引、字段注释或可空字段问题：优先使用前向修复迁移，避免整库恢复带来数据丢失。

## 生产变更审批清单

- 迁移脚本已通过静态验收。
- 后端构建已通过。
- 全新库迁移演练和备份恢复校验已通过。
- 关键 API 冒烟测试已通过。
- 数据库备份和恢复步骤已确认。
- 回滚责任人、验证责任人和业务确认人已明确。
