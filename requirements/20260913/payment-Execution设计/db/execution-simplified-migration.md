# 精简 Execution 的人工迁移说明

本目录当前模型切换入口；前序 `execution-state-risk-*`、`instrument-token-*` 和国家增量脚本保留为前序工件，不与本稿叠加执行。当前目标及关联改造见[字段修订](../execution-field-simplification-amendment.md)，实际执行状态只见[台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

2026-09-15 后继：[公共幂等接入](../../../../paytech/payment/docs/development/command-idempotency-differences.md)已将本文件所列创建稿与合并迁移稿中的命令表统一到公共列合同；已经完成其他字段精简的环境使用[独立命令表迁移路径](./command-idempotency-migration.md)，不要重复执行本文件的整批迁移。

## 文件与适用基线

| 文件 | 用途 |
|---|---|
| [正式全库 Schema](../../../../paytech/payment/docs/database/sql/payment-schema.sql) | 全新空 Payment 库；含配置/路由/回调/争议等全部模块 |
| [Execution 空库创建](./execution-simplified-create-empty-schema.sql) | 空库或所有目标表均不存在时，创建本期 10 张 Execution/命令/补偿表；其余模块仍 Mock + TODO |
| [只读预检](./execution-simplified-preflight.sql) | 检查当前本机旧表是否为空并导出结构；COUNT 必须逐项人工检查，不会自动阻止下一文件 |
| [本机旧空表迁移](./execution-simplified-local-empty-migration.sql) | 只适用于 127.0.0.1:13306 / mor_payment 的旧 13 表基线；9 ALTER、1 CREATE、4 DROP，结果为 10 表 |

迁移脚本覆盖旧 Instrument→Token、国家字段、状态/风控及本次字段精简。旧 `t_payment_instrument`、`t_payment_pricing_fact`、`t_payment_state_transition`、`t_payment_intent_binding` 在最后删除；保留其他表主键并同步列序、类型、默认值、注释和索引。没有业务 DML、物理 FK/CHECK，不执行其他系统 SQL。

## 已精简环境删除空 Binding

若其余表已符合当前模型、只剩 `t_payment_intent_binding`：确认精确数据库并停写、备份后，人工执行 `SELECT COUNT(*) FROM t_payment_intent_binding` 和 `SHOW CREATE TABLE t_payment_intent_binding`。仅在该表为空、当前代码已去除依赖时，使用[独立删除稿](./intent-binding-empty-migration.sql)；不存在则跳过。不要重复运行整批迁移。

非空 Binding 不适用此删除稿。必须先核对每条来源、Intent、关闭及资金事实，尤其是未绑定 Intent 的关闭记录；制定上游停写、未决创建处理及回退方案后再迁移，不能机械把 `closed_at` 转成 Intent CANCELLED。失败后保持停写，从原表结构与数据备份恢复，再考虑回退应用。

## 切换顺序

1. 核对应用版本和启用范围：当前受理链要求新版工具/捕获模式及公共幂等列；后续 Channel、通知和定价能力按各自合同完成适配后才能启用。清空前旧 Java 不兼容目标 Schema，不能与新版代码混用。
2. 停止 Payment 写入及补偿 Worker；精确确认是本机目标。对整库结构和数据做本地备份，保存 SHOW CREATE TABLE 与备份校验值，确认可恢复。备份不入仓库。
3. 运行只读预检，核对旧 13 张表、所有行数为 0、尚无 Token 表、列及索引与受检旧基线一致。任何新写入、额外目标表、未决业务/任务或不同结构均停止，重新准备迁移。
4. 按项目人工 SQL 规则取得这一具体库和文件的执行授权后，单独执行迁移文件；禁止 `--force`、跳过错误或混用旧脚本。全新空库使用创建文件，不运行旧表迁移。
5. 核对结果的 10 张表逐列/索引/注释等于当前 PDManer 子集，核对四张旧表不存在；再启动匹配代码并运行 HTTP 受理、资金/认证恢复与事件幂等回归。

MySQL DDL 不具备整包事务性。失败时停止后续语句和应用写入，记录已成功的语句；基线保持无新业务写入时，可在停机状态从已验证的整库备份恢复。不能把应用回退当成已删除字段/表的数据恢复。

非空环境不适用：必须先处理未决资金及消息、保留法律接受和旧定价引用的 Owner 证据、核对跨渠道同幂等键冲突，并为旧格式制定专门兼容/归档方案。不得删除未知操作或凭空生成风险、认证、定价成功证据。
