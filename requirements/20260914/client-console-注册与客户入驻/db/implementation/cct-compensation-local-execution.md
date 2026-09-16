# CCT 补偿表：本机单表执行记录

用户已对本文件的精确范围回复“可以”。2026-09-15 本机单条 ALTER 已成功执行并完成结构核验；[执行回执](../../assets/stage4/cct-compensation-execution-20260915.json)记录源/目标、完整列属性、结构备份、零警告及维护锁释放。现行状态见[实现台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。本文件保留已执行范围，不是可重复运行入口。

- 精确目标：Docker `desktop-linux` / Compose `mor-local` / `mor-local-mysql-1`，`127.0.0.1:13306/mor_cct.t_compensation_event`。
- 执行文件：[cct-compensation-empty.sql](./cct-compensation-empty.sql)，只有一个 `ALTER TABLE`；SHA-256 为 `219fd9a4fd5f3f5f9fceb4f63c113e993f4405095673cfb5a28d104979447944`。
- 删除六个旧字段：`execution_type`、`source_type`、`source_id`、`source_version`、`schema_version`、`payload_hash`；增加 `sub_event_type`。其余列按现行 Foundation 合同调整类型、默认值、可空性和注释，最终十五列。
- 唯一身份为 `(event_key,event_type,sub_event_type)`；增加独立就绪和租约扫描索引。Payload 可空、最长 4 KiB UTF-8，仅作最小 JSON Object；状态默认 INIT、次数默认 0、租约锁版本默认 1。
- 其余 37 张表不迁移，不修改 `t_command_idempotency`，不创建恢复审计表，不执行初始化 DML、Foundation 测试 DDL或其他 Owner SQL。此前十表 SQL 保持原指纹。

[只读预检](../../verification/preflight-cct-compensation.py)已核验实际二十列源结构、索引、注释、字符集、排序规则和额外列属性，38 张 CCT 表为空，无其他 CCT 连接或 18101 监听；结构备份目录 0700、文件 0600。证据见[预检回执](../../assets/stage4/cct-compensation-preflight-20260915.json)。该核验只覆盖本机和核验时刻；不是执行授权。

本次执行前已重新核对指纹、源结构、空表、连接与监听，取得本机维护协调锁并保存新的 SHOW CREATE 备份。任何差异必须停止。DDL 自动提交，不使用 `--force`；部分失败时保留现场与回执，不自动 DROP、回滚或重新执行。其他环境仍需重新核对和精确授权，不能沿用本机授权。

执行后十五列的类型、默认值、可空性、注释、字符集、排序规则及生成默认值属性均与模型一致，主键/唯一键/访问索引共四项匹配；其余 37 表 SHOW CREATE 不变，38 表仍为空。新表上真实 Foundation 注册器/扫描器/Claim 的五项合同测试通过，CCT 全量 `clean verify` 40/40 通过。单表迁移和技术合同测试不代表开通 Handler、Platform PLT-I17 或草稿接口已完成。

可重复的静态生成检查：

    node requirements/20260914/client-console-注册与客户入驻/db/implementation/generate-cct-compensation.mjs --check
    node --test scripts/pdmaner/client-compensation-model.test.mjs scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-onboarding-model.test.mjs

[源模型投影](./cct-compensation-source.json)、[源 SQL 参考](./cct-compensation-source.sql)仅供比对，不能执行。完整 Schema 也不是该本机迁移入口。

生成器已依据执行回执冻结本条 SQL 和当时的目标模型指纹。目标表再次变化必须另建迁移；旧只读预检会因本机不再符合二十列源而停止，这是预期保护，不能回写旧源或重跑 ALTER。
