# Client 入驻人工迁移候选

本目录是阶段 4 从批准投影生成的人工 SQL 候选，不是执行回执。实际状态只见[当前台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)，审批与非空历史处理遵循[迁移方案](../../migration-plan.md)。

| 文件 | 适用范围 |
|---|---|
| [client-contract-empty-closure.sql](./client-contract-empty-closure.sql) | CCT 10 表调整；只允许完整引用闭包为空、实际结构与冻结源模型匹配的环境 |
| [risk-compliance-empty-closure.sql](./risk-compliance-empty-closure.sql) | Risk 3 表调整和分类表创建；只允许完整引用闭包为空的环境 |
| [platform-support-additions.sql](./platform-support-additions.sql) | 补齐现有模型的 7 表；表已存在则停止并核对，不能覆盖 |
| [migration-manifest.json](./migration-manifest.json) | 冻结源模型、当前模型和两份变更 SQL 的 SHA-256 |
| [platform-auth-local-execution.md](./platform-auth-local-execution.md) | 当前认证批次已获准的本机单表执行范围；引用既有 CREATE 及回执，其他迁移仍须分别授权 |
| [cct-local-execution.md](./cct-local-execution.md) | CCT 本机十表已获准执行的精确范围、回执及验证顺序；不能重复迁移，不包含旧补偿表 |
| [cct-compensation-local-execution.md](./cct-compensation-local-execution.md) | CCT 补偿单表已获准执行的精确范围、回执和真实 Foundation 合同验证；不能重复迁移 |

执行前按前序方案读取真实 SHOW CREATE、盘点闭包和依赖、停止写入并备份，再取得精确环境和 SQL 的执行授权。非空环境必须先完成历史行级映射；不得直接套用空闭包脚本。DDL 自动提交，不能把 SQL 事务当回滚保证，不使用 mysql --force。

下面两个命令仅生成或静态检查文件，不连接数据库；从工作区根目录执行：

    python3 requirements/20260914/client-console-注册与客户入驻/db/implementation/generate_migrations.py
    python3 requirements/20260914/client-console-注册与客户入驻/db/implementation/verify_migrations.py

生成器要求各 Owner HEAD 模型 SHA 与阶段 3 冻结源完全一致，源改变时立即失败。已执行 CCT 十表的 SQL 与历史目标模型指纹保持冻结；补偿等其他表后续变化不能覆盖它们，十表目标本身再变更时必须另建迁移。静态检查在内存重新生成并比较输出，不回写候选或历史 Manifest；再模拟 ALTER 的列增删改、列顺序、主键重命名、索引与表注释结果，逐项比较当前完整 Schema。它不替代 MySQL 语法执行和实际数据验证。

四个 CCT 评审索引采用现行长度规范的短名：uk_client_app_account_active、uk_client_app_submission、idx_client_app_requester、uk_client_business_client。索引列及唯一语义不变。Risk 原生 DSL 保留审计列置后的顺序；冻结投影的列形状不变。不得对已改变的模型重新运行上级目录的阶段 3 评审生成器。
