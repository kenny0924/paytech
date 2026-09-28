# Payment 公共命令幂等表人工迁移

本文件对应用户 2026-09-15 确认的[公共包接入约定](../../../../payment-orchestration-service/docs/development/command-idempotency-differences.md)。只维护待执行工件，实际数据库与验证状态见[台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 选择一条执行路径

| 实际基线 | 使用文件 |
|---|---|
| 全新空 Payment 库 | [正式全库 Schema](../../../../payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql) |
| 本期 Execution 目标表全部不存在 | [Execution 创建稿](execution-simplified-create-empty-schema.sql)，已包含公共命令表 |
| 原本机旧 13 张表仍为空，尚未执行整批字段精简 | 按[整批迁移说明](execution-simplified-migration.md)执行其合并迁移稿；其中命令表 ALTER 已同步本次目标 |
| 其他表已经对齐，命令表仍为旧 11 列且为空 | [独立命令表迁移稿](command-idempotency-empty-migration.sql) |

各路径互斥。合并迁移与独立命令表 ALTER 不得重复执行。任何基线不同或表非空，均不能使用空表迁移稿。

## 执行前核对

1. 停止 Payment 写入及相关 Worker；精确核对目标库，备份结构与数据并确认恢复方式。不能沿用旧的“空库”检查结果。
2. 只读执行 `SELECT COUNT(*) FROM t_command_idempotency;` 与 `SHOW CREATE TABLE t_command_idempotency;`，确认 0 行，并与下列旧结构一致；整批迁移还须核对其余所有目标表。
3. 旧结构应为：`command_idempotency_id BIGINT` 主键；`caller_scope VARCHAR(128)`、`operation_type VARCHAR(64)`、`idempotency_key VARCHAR(128)`、`request_hash CHAR(64)`、`command_status VARCHAR(64)`、`create_time/update_time DATETIME(6)` 非空；`resource_type VARCHAR(64)`、`resource_id BIGINT`、`response_code VARCHAR(64)` 可空。唯一键为 `(caller_scope, operation_type, idempotency_key)`，字段原继承 `utf8mb4_0900_ai_ci`。
4. 按项目人工 SQL 流程核准具体目标和文件后执行；不使用 `--force`，任何错误立即停止。应用启动不会执行此 SQL。

独立脚本仅 ALTER 原表：状态改名 `process_status`；补齐公共包读取的全部技术列；key 扩展为 255；scope/key/resource_no/response_location 使用 ascii_bin；保留主键、唯一身份与所有业务表。没有回填 DML、FK/CHECK、恢复扫描索引或新增业务表。

## 非空基线

必须先按 `operation_type` 和原状态盘点，另行制定数据迁移。保留原 scope、key、Hash、资源 ID 与回执依据；未知 Hash 版本不得统一填 1，PROCESSING 不得直接标成 COMPLETED，也不能凭空授予新 Lease 恢复旧操作。字符集切换须核对非 ASCII、大小写和空格等原比较语义。公共列合同不能靠填空响应或伪造请求快照绕过。

## 切换与验证

逐列核对类型、长度、可空性、默认值、排序规则、注释和索引与当前 PDManer 一致，再启用匹配版本代码。公共包必须在同一 Payment 数据源和事务管理器下工作；业务事务失败时 Claim 一起回滚。

真实 HTTP/数据库回归应覆盖新受理、同键同参重放、同键改 Token 的优先 409、并发 Claim、只读零写入、原 Attempt 补绑定、CAS/租约失败回滚，以及 Attempt 状态变化后返回当前响应。编译和离线模型检查不代替这些回归。

MySQL DDL 不具备整包原子性。失败后保持停写，记录已完成语句并从已验证备份恢复到原基线；不能只回退应用而继续使用不匹配的命令表。
