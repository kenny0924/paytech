# 本机开发回归准备方案

目标仅为 `local-environment / 127.0.0.1:13306/mor_platform_config_validation`；原 `mor_platform`、`mor_platform_rco16_validation` 不修改。本库用完后也不自动删除。

1. 确认库名尚不存在、现有 `platform_app` 仅有本机用途，备份相关 Schema/授权元数据至 `~/Code-MoR-backups/`，不导出人员或凭据业务数据。
2. 人工执行 [prepare.sql](./prepare.sql)，新建库并赋予现有本机账号该库的数据读写权限。
3. 向新库应用唯一 [Schema](../../../../../../paytech/platform-support/docs/database/sql/platform-support-schema.sql)（24 张现有表）与 [完整初始化](../../../../../../paytech/platform-support/docs/database/sql/platform-support-initial-data.sql)（1141 行初始化数据）。Schema 没有因本需求增加或修改表/字段；新入口与按钮停用，管理员新增管理授权为 REVOKED。
4. 开发测试显式指定 `PLATFORM_SUPPORT_TEST_DB_URL=jdbc:mysql://127.0.0.1:13306/mor_platform_config_validation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`；凭据只从既有本机私有环境读取，不输出。
5. 测试自行准备、清理隔离 Fixture；不在应用启动时迁移/播种，不向旧验证库补数据或删除历史。

选择独立库的背景：首次尝试时，旧独立验证库已包含 RCO-17 目录，而旧测试 Oracle 仍停留在 RCO-16，前置检查因此阻止进入接口。新回归库与更新后的固定目录 Oracle 使用同一已评审业务基线；对 Oracle 的实现独立性继续保留字面量合同测试。该历史阻塞的后续处理和实际回归结果只在当前台账记录。

本方案的授权记录见 [delivery.md](../../../delivery.md)，实际应用与回归状态见[当前台账](../../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-config-management-20260913)。不涉及 test、发布、部署或提交；已存在的库不能再次执行 prepare.sql。
