# 本机 Platform 独立验收库准备

状态：用户已明确授权，2026-09-12 已备份并执行。详见[执行记录](../../../assets/gateway-acceptance/local-sql-application.json)和[验证后数据库复核](../../../assets/gateway-acceptance/final-database-check.json)。下列顺序保留为本次执行依据，不可重复执行 CREATE DATABASE。

当前 `mor_platform` 存有 2 个 Operations 账号、1 个 ACTIVE 签名 Key、测试日期的 1 条审计链，以及 4 条 Version=2 的旧路由。现有综合测试明确要求只有初始化目录。补缺失目录仍不能满足此门禁，不能把已有版本重置或删除账号来凑测试基线。

本方案使用同一个仓库 `local-environment` MySQL（127.0.0.1:13306），人工准备 `mor_platform_rco16_validation`，保持原库完整。没有新容器或应用自动建表，也不涉及 test 环境。测试继续使用真实 Repository、HTTP 和本机 Redis。

本次按以下顺序执行：

1. 用本机管理员只读复核 Compose 项目、端口、库名不存在以及 [manifest.json](./manifest.json) 中 3 个 SQL Hash；如果库已存在则停止，不覆盖。
2. 将原库目录结构、统计及本机账号授权元数据的备份保存在 `~/Code-MoR-backups/` 私有目录，并记录 Hash；不导出密码、私钥或业务明文。
3. 执行 [prepare.sql](./prepare.sql)。随后明确选择新库，依次手工应用既有权威 [Schema](../../../../../../platform-support-service/docs/database/sql/platform-support-schema.sql)（24 表）及 [初始化 DML](../../../../../../platform-support-service/docs/database/sql/platform-support-initial-data.sql)（683 条初始化插入及 1 次管理员角色版本递增）。不得再次复制出第二份 Schema/初始化文件。
4. 运行时临时指定 `PLATFORM_SUPPORT_TEST_DB_URL=jdbc:mysql://127.0.0.1:13306/mor_platform_rco16_validation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`，凭据只通过已有私有环境变量传入。
5. 先运行 Gateway 完整快照/动态目标、初始化目录/权限及导航相关用例，再进行必要的 Platform 综合回归。测试只创建和清理自有夹具，不建表或执行初始化 SQL。
6. 对比新库测试前后完整目录，确认自有业务夹具已清理；复核原库未改变。新库保留作为验收证据，不擅自删除。任何失败都先停在新库，不修改原库。

新库初始化包含已有及本期目录。15 条尚未发布的新 Route、11 个 Resource 仍为 DISABLED，字典接口不会因此在公网启用。本准备不改变 RCO-16 的 14 API / 2 业务表 / 182 注册条目范围。

阶段 5 发现待发布角色绑定与禁用入口之间的冲突，详见[已确认修正](../../../pending-registration-permissions.md)。用户随后单独确认，已备份后应用 [correct-pending-permissions.sql](./correct-pending-permissions.sql)：7 条绑定改为 REVOKED/version=2，管理员 version=3；[执行证据](../../../assets/gateway-acceptance/permission-correction-application.json)。原建库执行的 Hash 保留为历史，当前完整初始化同步了该修正，多一次管理员版本递增（共两次），不能重跑初始化代替补丁。
