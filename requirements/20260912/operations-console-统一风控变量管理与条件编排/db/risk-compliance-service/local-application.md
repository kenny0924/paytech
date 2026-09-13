# 本机增量应用准备

目标仅为 Docker context `desktop-linux`、`mor-local-mysql-1`、`127.0.0.1:13306/mor_risk`；不涉及 `mor-test` 的 23306 或其他数据库。

2026-09-12 已只读核对：两张变量表不存在；已有表没有 variable_code/currency_variable_code；t_rule_operand、t_rule_action_parameter、t_decision_flow_edge 各为 0 行。执行前再次核对目标和结构，若事实变化先检查差异，不盲目重跑。

模型和完整 Schema 已同步本期 2 表 / 41 字段，4 旧表共新增 5 列、修改 4 列注释，新增 11 索引（含 2 主键、2 唯一索引）。精确字符串使用 NO PAD 的 utf8mb4_0900_bin，防止成员尾部空格被唯一索引误判相同。

获准后的顺序：

1. 在 `~/Code-MoR-backups/rco19-local-<timestamp>/` 保存 mor_risk Schema 和上述三张明细表及 t_rule_set_version 的逻辑备份；凭据仅由容器环境读取，不写入命令输出。
2. 人工执行本目录 [ddl.sql](./ddl.sql)，再执行 [dml.sql](./dml.sql)。新增 17 个变量和 9 个成员，不更新历史规则/流程，不改授权。
3. 核对新增列、索引、注释和 26 行目录，执行本期 Java 开发回归。Java 测试不创建或迁移表。

MySQL DDL 不具有整体事务回滚保证，应用失败时停止并核对已经执行的语句。若需撤销新增结构，先核对尚无本期配置写入，再单独评审逆向 SQL；不得直接重建库或覆盖原表。test 的注册、部署及示例新版本迁移继续留在后续阶段。

## 2026-09-13 执行记录

用户收到上述精确本机 SQL 清单后回复“可以”，授权备份后应用本轮增量。已完成；执行前再次核对目标、无竞争业务语句、两新表/五新列均不存在，四个受影响历史表均为 0 行。

备份目录：`/Users/kenny/Code-MoR-backups/rco19-local-20260913T040036Z/`。Schema 与受影响数据分别备份，执行的 DDL/DML 原文件字节未改；备份大小、SHA-256、SQL SHA-256、执行时间及后置结构/种子计数见[应用证据](../../assets/stage4/local-sql-application.json)。无需再次执行本增量。

开发回归后的 17 个变量/9 个成员保留，测试变量为 0；四个原表仍为 0 行。真实 MySQL/Redis 回归及清理结果见[本机回归证据](../../assets/stage4/local-regression.json)。本记录仅针对本机 Risk，Platform 注册 SQL、test、部署、迁移及提交均未在本次执行。
