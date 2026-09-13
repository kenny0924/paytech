# RCO-17 本机结构准备

状态：已获用户授权并完成本机应用；此文件保留执行范围与证据，不作为其他环境授权。

- 目标仅 local-environment 的 mor-local-mysql-1，宿主端口 127.0.0.1:13306，数据库 mor_risk；内部 MySQL 3306。
- 只读核对当前 74 张表，四张 t_decision_flow* 目标表均不存在；新模型与完整 Schema 已同步，原 74 个实体逐项保持不变，模型/Schema 29 项检查通过。
- 执行文件为 [ddl.sql](./ddl.sql)，新增流程、版本、节点、连线共 4 表/44 字段/11 索引；无旧表改动、无旧数据回填、无初始化 DML。
- 执行前仅备份本机现有 Schema 与目标表不存在的核对结果，写入 ~/Code-MoR-backups/ 下本次独立目录；凭据不进入文件或日志。没有受修改的旧数据，无需重复导出全库业务数据。
- 执行后核对新增结构和完整中文注释。测试经公开 HTTP 使用自己创建的合成数据并按业务身份清理，不连接 test。
- 本机运行凭据可从已运行本机容器环境读取并仅注入测试子进程，不读取或修改 .env.local，不回显任何值。
- 不包含 test、部署、共享制品发布或提交。

## 2026-09-12 执行记录

用户明确答复“允许备份 Schema 后应用本机 mor_risk”。执行前 Schema 与授权目标核对证据保存在 `/Users/kenny/Code-MoR-backups/rco17-local-schema-20260912-150648/`。已手工应用本目录 ddl.sql，随后核对新增 4 表、44 字段、11 索引及完整字段注释，旧表与旧数据未改变。未应用 Risk DML，没有连接 test。当前研发及验证状态统一见 [CURRENT_IMPLEMENTATION.md](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md)。
