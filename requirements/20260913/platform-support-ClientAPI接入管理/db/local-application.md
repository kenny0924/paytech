# 阶段 4 本机数据库准备

目标为已有独立开发验证库 `mor_platform_config_validation`，Docker context `desktop-linux`、容器 `mor-local-mysql-1`、宿主 `127.0.0.1:13306`。开发回归显式覆盖 PLATFORM_SUPPORT_TEST_DB_URL 指向此库；凭据从本机私有配置读取，不写入本文件。

执行前只读核对：本库已有 24 表，包含两张既有 Credential/Grant 表及 Route；本次新 t_api_scope 尚不存在。执行前再次核对两新索引不存在、当前旧字段和完整 Schema 一致。

原申请只有 [ddl.sql](./ddl.sql)：新建 t_api_scope（7 字段、1 主键、1 唯一索引），t_api_credential_scope 和 t_gateway_route 各加 1 个查询索引；当时未获执行授权。后续核对发现，真实 HTTP 测试复用的 OperationsFixtureMocker.open 会严格比较完整初始化目录，因此仅补 DDL 仍不能开始业务回归；不降低这个原有门禁。

修订申请增加 [local-registration.sql](./local-registration.sql)：从唯一完整初始化文件逐行抽取本期 127 条新增注册，用于该独立库的测试基线。4 权限为 ACTIVE，新菜单/Route 为 DISABLED，4 条角色关联为 REVOKED；没有客户、凭证、Scope 业务种子，不授予现有角色生效权限。使用原初始化约定的保留 actor=1，不冒充登录账号。旧行及 Route version 保留全新初始化基线，已有三个新 Query 子项加入目录；该文件不是有在线消费者环境的部署增量，不能用于 test 或其他数据库。

执行前确认没有应用或测试正在消费此库、无 Operations 账号/凭证/活动密钥，核对 127 个具体 ID/自然键均缺失。2026-09-13 本轮只读观察新四权限、Operations 账号和凭证数均为 0；两旧 Route 为 ACTIVE/version=1。若前置不再成立，停止并重新准备具体差异。该修订同时申请 DDL 与本机基线 DML；原先仅 DDL 的问询不授权这部分新增范围。

已将批准的 Manifest/PDManer 和正式 Schema 同步到相同字段/索引；DDL 候选的索引注释改为模型生成的标准说明，不改变字段或索引结构。授权后只备份两张受影响旧表的 Schema 及本期注册键的前置计数至 `~/Code-MoR-backups/platform-client-api-local-20260913/`，确认文件有效。先手工执行 DDL 并核对新增表/索引，再在同一连接中执行 local-registration.sql、核对 127 行和禁用状态后明确 COMMIT；文件没有自动 COMMIT。DDL 自动提交；失败按实际完成项核对，不盲目重跑或自动 DROP。

没有旧业务数据被迁移，回退应用可保留新表和索引。开发测试只创建并清理自身合成场景数据，包括目录、权限和凭证；不清空全库、不执行业务初始化 DML，不使用真实客户或真实凭证。

执行记录：用户在新目录用途及取值的说明后回复“可以，没问题了”，承接修订后的完整本机申请。已完成前置核对、两旧表 Schema 备份、DDL 和 127 条本机注册提交；文件 SHA、逐项数量与事务记录见 [本机执行证据](../assets/stage4/local-database-application.json)。该证据对应上述独立开发库，不涉及 test、部署、制品发布或提交。
