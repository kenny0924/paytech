# 精简修订：本机结构应用准备

状态：用户在三表具体授权说明后回复“可以”；2026-09-12 已备份并应用至本机，真实 Risk 开发回归已完成；详见修订开发检查。

目标只限 `local-environment` 的 `127.0.0.1:13306/mor_risk`。修改 `t_rule_set_version`、`t_rule_operand`、`t_rule_action_parameter`：新增 4 列，删除 2 列及 2 索引，不增删表。原四张流程表不变；不涉及 test、发布、部署或提交。

2026-09-12 研发期间重新运行 [precheck.sql](./precheck.sql)，本机规则版本、Context 操作数、Context 动作参数、旧协议版本、旧输入字段、旧顺序绑定、旧发布均为 0，三项异常均为 0。因此本次本机没有历史行需回填；历史兼容另以隔离测试场景验证，不能把空库结构应用当成历史迁移验证。

执行前再次核对目标、上述空历史、三表原结构及没有正在运行的 Risk 写入方；如数据变化，停止本空库应用流程。先将三表原 Schema 导出至 `~/Code-MoR-backups/rco17-simplification-local-<timestamp>/`，保存列/索引元数据与预检结果；不导出或输出凭据。

人工执行顺序：

1. 应用 [ddl.sql](./ddl.sql) 的四列扩展。
2. 同一连接运行 [dml.sql](./dml.sql) 与 [postcheck.sql](./postcheck.sql)，核对全部为 0 后显式 `COMMIT`；失败则 `ROLLBACK` 并停止。
3. 再核对历史仍为空，应用 [finalize-ddl.sql](./finalize-ddl.sql)。空库没有旧规范字节需要对比；这一豁免仅适用本次重新核实为空的本机库。
4. 逐列核对最终三表的类型、默认、可空性、顺序、注释与索引匹配 canonical Schema；运行本机 Risk 开发回归。

DDL 不可事务回滚。应用中断时保留备份及已执行步骤，不自动重试或重复加列；根据 `information_schema` 复核后恢复或继续。开始写入新规则后，不允许仅回退旧应用或将字段代码伪造为旧字段 ID；恢复应使用获批范围内的备份与对应应用版本。

本机回归使用本仓库固定 MySQL/Redis，不连接共享 test。原 [执行说明](./README.md) 仍管理有历史数据环境的停写、回填、规范字节/Hash 对比和删列门禁。

## 执行证据

- 目标重新核对为 Docker `mor-local-mysql-1`，映射仅 `127.0.0.1:13306`，数据库 `mor_risk`。执行前无其他该库会话和 Risk JVM；三表全行数及七项历史计数为 0，三项来源异常为 0。
- 备份目录：`/Users/kenny/Code-MoR-backups/rco17-simplification-local-20260912T142556Z`。包含三表原 Schema、变更前后列/索引元数据、原 SQL、预检、回填后置结果及每阶段完成标记；不含凭据。
- 扩展四列后，在同一连接执行 DML 和 postcheck，9 项结果全部为 0，再显式 COMMIT。再次确认空历史后执行 finalize DDL；没有数据行被回填。
- 三表最终 **44 个字段**的类型、顺序、可空性、默认值及全部索引结构与 canonical 模型一致，新增四列注释一致。核对发现 **39 个既有列注释**与 canonical 注释不同，均与备份中的原注释逐字相同，主要为先前已有的乱码；本次未扩大修改旧注释。具体字段见备份 `schema-verification.json`。不宣称三表旧注释也已对齐。
- 首个真实 HTTP 用例已通过：连续新建/保存、旧版本退休、冻结及成功回放，新规则创建旧 Candidate 返回业务校验失败，三张旧编排表无新增。全量回归结果另记 [修订开发检查](../../../simplification-development-checks.md)。

仅本机执行；test 仍未应用本修订。空历史不代替有历史数据环境的迁移与 Hash 验证。
