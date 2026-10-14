# Risk 入驻 4b：本机结构迁移执行范围（待授权）

**状态：已执行（2026-10-13）。** 用户在 4b 确认中授权执行本范围；执行前复核指纹、空表、无其他连接并保存结构备份，执行退出码 0（仅 1681 显示宽度弃用警告），执行后 11 张相关表与正式 Schema 逐列、逐索引一致，见[执行回执](../../assets/stage4/risk-onboarding-table-execution-20261013.json)。其他环境仍需重新核对与授权。依据：[C14 修订稿第 7 节](../../submission-contract-amendment.md#7-范围与实施顺序)、Risk 正式 Schema `risk-compliance-service/docs/database/sql/risk-compliance-schema.sql`。

## 精确目标

- Docker `mor-local-mysql-1`，`127.0.0.1:13306/mor_risk`，账号 `risk_app`。
- 执行文件：[risk-onboarding-alter.sql](./risk-onboarding-alter.sql)，SHA-256 `eb8c7dcbc4bfc1bf91549704d751264a1be6158cea4f9d049f9c30a2cf093b11`；由 [generate-risk-onboarding-alter.py](./generate-risk-onboarding-alter.py) 从正式 Schema 生成，`python3 -I generate-risk-onboarding-alter.py --check` 复核一致。
- 内容（3 条 ALTER + 1 条 CREATE，无 DML）：
  - `t_due_diligence_case`：新增 `source_submission_id`、`public_note`、`resolution_note`、`return_section`；`source_submission_version`、`source_submission_hash`、`status` 仅同步注释；`uk_dd_case_source` 改为包含 `source_submission_id`，使退回后重提能建新任务。
  - `t_dd_case_subject`：新增 `source_relation_id`；`source_profile_version_no` 改为可空（新 Client 不伪造版本）。
  - `t_qualification_decision`：`decision_version` 改为可空。
  - 新建 `t_dd_case_classification`（运营确认的行业/MCC，后续审核批准门禁使用）。

## 2026-10-13 只读核对结果

- `mor_risk` 共 80 表；上述 10 张相关表存在，`t_dd_case_classification` 不存在。
- `t_due_diligence_case`、`t_dd_case_subject`、`t_qualification_decision`、`t_dd_party_snapshot`、`t_dd_evidence_ref`、`t_dd_requirement`、`t_dd_case_dependency` 均为 0 行。
- 其余 7 张相关表的列与索引已与正式 Schema 一致，不在本次范围。
- 现有 `uk_dd_case_source` 为 `(source_namespace, source_application_id, role_type, role_id, qualification_type)`。

## 执行前与执行后

- 执行前：重新确认 SQL 指纹、相关表仍为 0 行、无其他 Risk 进程写入；保存 4 张表的 `SHOW CREATE TABLE` 结构备份。任何差异即停止。
- 执行后：逐列（类型、可空、默认、注释、顺序）与索引对照正式 Schema；其余表结构不变。
- DDL 自动提交；部分失败保留现场，不自动重跑。

## 回滚

```sql
DROP TABLE `t_dd_case_classification`;
ALTER TABLE `t_qualification_decision` MODIFY COLUMN `decision_version` INT NOT NULL;
ALTER TABLE `t_dd_case_subject` DROP COLUMN `source_relation_id`, MODIFY COLUMN `source_profile_version_no` BIGINT NOT NULL;
ALTER TABLE `t_due_diligence_case` DROP INDEX `uk_dd_case_source`,
  ADD UNIQUE KEY `uk_dd_case_source` (`source_namespace`,`source_application_id`,`role_type`,`role_id`,`qualification_type`),
  DROP COLUMN `return_section`, DROP COLUMN `resolution_note`, DROP COLUMN `public_note`, DROP COLUMN `source_submission_id`;
```

回滚时注释按执行前的结构备份恢复。只在相关表仍为空时可直接回滚；已有 4b 测试数据时先清理测试数据。
