# 阶段 5 数据迁移演练核对（本机 `*_v2e2e`）

> 任务卡 `tasks/P1-stage5.md` B 节；技术方案第 3.4 节第 1–3 步。只在本机 MySQL 容器 `mor-local-mysql-1` 上执行，root 口令只经环境变量传入。
> 脚本：`verification/stage5_migrate.sh`（子命令 `precheck / backup / ddl / import / verify / droptrial / autodb-check / autodb-ddl`）。
> 执行时间均为 2026-10-21（UTC），每条变更语句的库名与时间见 `verification/stage5-sql-log.txt`。

## 1. 执行前检查（`precheck`）

| 项 | 结果 |
|---|---|
| `mor_platform_v2e2e` 表数 | 57 |
| `mor_channel_connector_v2e2e` 表数 / 已有 10 表 / `change_detail` 列 | 18 / 0 / 无 |
| `channel_connector_app` 库级授权 | `GRANT SELECT, INSERT, UPDATE, DELETE ON mor_channel_connector_v2e2e.*`（库级，新表自动覆盖，无需追加授权） |
| Platform `RUNNING` 验证运行 / 待执行 `INTEGRATION_VERIFY_RUN` 补偿 | 0 / 0 |
| 写入冻结 | 基线（A2–A4）03:19:37Z 跑完后，不再经 Platform 写外部 API 配置；导入后核对 Platform 10 表的最大 `create_time` 仍为 03:19:37Z，行数未变（见第 4 节） |

## 2. 备份（`backup`，03:21:38Z）

目录：`local-environment/e2e/backup/20261021T032138Z-ccu-stage5/`（目录 700、文件 600；含沙箱验证账号凭据明文，与原表同口径，不入库）。

| 文件 | 内容 | sha256 前 16 位 |
|---|---|---|
| `mor_platform_v2e2e-10-tables.sql` | 10 张表结构 + 数据（`--single-transaction --set-gtid-purged=OFF --no-tablespaces --skip-triggers`），CREATE 10、INSERT 10 | `5d61abe81a7a3a36` |
| `mor_channel_connector_v2e2e-t_channel_config_change.sql` | 迁移前 `t_channel_config_change` 结构 + 数据（8 行，A1 控制台登记产生） | `043eb918640a52db` |
| `mor_platform_v2e2e-10-tables-data.sql` | 第 3 步导入用的纯数据导出（同时作为导入原件保留） | `99582dd8a4df029b` |

## 3. 结构与数据

| 步骤 | 库 | 时间（UTC） | 命令 / SQL |
|---|---|---|---|
| 结构 | `mor_channel_connector_v2e2e` | 03:21:43 | `USE mor_channel_connector_v2e2e;` + worktree `paytech/channel-connector/docs/database/sql/changes/2026-10-20-protocol-tables.sql`（ALTER `t_channel_config_change` 增加 `change_detail` 并更新注释；CREATE 10 表）。执行后 28 张表 |
| 数据 | `mor_channel_connector_v2e2e` | 03:21:49 | `mysqldump --no-create-info --skip-triggers --complete-insert --single-transaction --set-gtid-purged=OFF --no-tablespaces mor_platform_v2e2e <10 表>` → `mysql mor_channel_connector_v2e2e`（INSERT 10 条，无 CREATE） |

说明：本机 MySQL 8.4 开启 GTID，`--set-gtid-purged=OFF` 避免导出文件写入 `SET @@GLOBAL.GTID_PURGED`；`--single-transaction` 取一致快照、`--no-tablespaces` 免 PROCESS 权限提示。三项不改变导出的数据内容，是卡片命令之外仅有的追加选项。

## 4. 核对（`verify`）

逐表行数与 `CHECKSUM TABLE`：

| 表 | 源行数 | 目标行数 | 源 CHECKSUM | 目标 CHECKSUM | 结论 |
|---|---:|---:|---:|---:|---|
| t_standard_api | 4 | 4 | 4170434948 | 4170434948 | 一致 |
| t_api_field | 163 | 163 | 253238846 | 253238846 | 一致 |
| t_integration | 1 | 1 | 64085513 | 64085513 | 一致 |
| t_integration_api | 4 | 4 | 3249706529 | 3249706529 | 一致 |
| t_integration_script | 2 | 2 | 1583555527 | 1583555527 | 一致 |
| t_integration_standard_api | 4 | 4 | 1394814679 | 1394814679 | 一致 |
| t_integration_verify_account | 2 | 2 | 3127081584 | 3127081584 | 一致 |
| t_integration_verify_case | 2 | 2 | 777876853 | 777876853 | 一致 |
| t_integration_verify_run | 2 | 2 | 3267451535 | 3267451535 | 一致 |
| t_integration_verify_run_step | 2 | 2 | 1158447473 | 1158447473 | 一致 |

逐行 SHA-256：范围扩大到 10 表的**全部** JSON 列与脚本源码列（15 列），每列按主键排序后整列比较，两边逐行一致：

| 表 | 列 | 行数 | 一致行 |
|---|---|---:|---:|
| t_integration | endpoints | 1 | 1 |
| t_integration_api | definition_json | 4 | 4 |
| t_integration_script | parameter_definition | 2 | 2 |
| t_integration_script | source_code | 2 | 2 |
| t_integration_standard_api | flow_definition_json | 4 | 4 |
| t_integration_verify_account | parameters / credentials | 2 / 2 | 2 / 2 |
| t_integration_verify_case | steps_json | 2 | 2 |
| t_integration_verify_run | case_snapshot_json / config_versions_json / endpoints_json | 2 / 2 / 2 | 2 / 2 / 2 |
| t_integration_verify_run_step | resolved_input_json / result_json / assertions_json / trace_json | 2 ×4 | 2 ×4 |

卡片点名的三类列逐行摘要（SHA-256 前 16 位，两库相同）：

| 列 | 主键 | 编码 | SHA-256 |
|---|---|---|---|
| definition_json | 238184964888203264 | ORDER_AND_PAY | f83f07566e88862d |
| definition_json | 238184965215358976 | ORDER_QUERY | 48a5f62a4c4b2797 |
| definition_json | 238185012170592256 | REFUND | 341275f2a9d93bc3 |
| definition_json | 238185012518719488 | REFUND_QUERY | 95aea94f2f686fe5 |
| flow_definition_json | 238184966398152704 | PAY_QUERY | 14de12811b0d4fd3 |
| flow_definition_json | 238185010866163712 | PAY | 456c150938eb2d07 |
| flow_definition_json | 238185059633336320 | REFUND | d37263dbe81bf20d |
| flow_definition_json | 238185110841593856 | REFUND_QUERY | 7cd072348affced3 |
| source_code | 238184963827044352 | PAYERMAX_SIGN | 4c8daa504be11b32 |
| source_code | 238184964292612096 | PAYERMAX_VERIFY | c9c31711f0d16ec6 |

应用层核对：新版本启动后、任何写入之前，经 ops-bff 取全部 34 个外部 API 管理 GET（`stage5-after-readonly-snapshot.json`），与基线快照逐字段比较（去掉时间与请求 ID，**ID 不去掉**）：0 处差异。

## 5. Platform 原表保留与删表语法试跑（`droptrial`）

- `mor_platform_v2e2e` 的 10 张表**未删除**（新版本运行后仍为迁移时的行数，最大 `create_time` 03:19:37Z，说明迁移后没有写入落到 Platform）。
- 试跑库 `mor_ccu_stage5_droptrial`：03:21:59 建库 → 03:22:00 用增量 DDL 的 10 条 CREATE TABLE 建空表（10 张）→ 执行 `db/platform-support-service/drop-integration.sql`（`USE` 临时库，注释中的 `mor_platform` 替换为临时库名；文件本身不带库名）→ 0 张表 → `DROP DATABASE`，无残留。10 条 DROP 语法与依赖顺序均可执行。

## 6. 本机 auto 测试库 `mor_channel_connector`（`autodb-check` / `autodb-ddl`）

- 执行前：18 张表，0 张目标表，无 `change_detail`；18 张表的表名、列名、类型、可空、默认值与迁移前的 `mor_channel_connector_v2e2e` 一致（排除 10 表与 `change_detail` 后 `diff` 为空），即只缺 10 张表与 `change_detail`。
- 03:22:08 执行同一增量 DDL，结果 28 张表；未导入任何数据。执行前确认没有正在运行的 Surefire 进程。

## 7. 回退

- 数据：`TRUNCATE` `mor_channel_connector_v2e2e` 的 10 张表（Platform 原表未动）。注意新版本运行期间写入的 `S5_AFTER_ACCT`、`S5_AFTER_CASE`、运行 `238502870792601600` 及其 `t_channel_config_change` 记录只在目标库，回退即丢弃。
- 结构（如需）：按增量 DDL 文件头的回退语句 DROP 10 表并回退 `change_detail`；或用本节备份 `mor_channel_connector_v2e2e-t_channel_config_change.sql` 恢复该表。
- 应用：见 `stage5-report.md` 第 6 节。
