# 阶段 5 本机真实链路准备

目标仅 `desktop-linux / mor-local-mysql-1 / 127.0.0.1:13306 / mor_platform_config_validation`。本方案不属于阶段 6 的正式 15 行目录启用；test 不变。

本期 8 Route / 3 Resource 当前全为 DISABLED/version=1，原 PLATFORM_ADMIN 三条关联仍 REVOKED。真实 Gateway 快照与菜单不能访问禁用项，因此本机验收需要临时启用精确的 11 行。仅作为本次隔离测试夹具覆盖 status，版本保持1；结束恢复原值，不发布永久访问配置。

- 执行文件：[enable.sql](./enable.sql)，恢复文件：[restore.sql](./restore.sql)。均单连接、检查8/3影响行后由调用方COMMIT；失败ROLLBACK。
- 执行前快照：[before.json](./before.json)。仅含11行ID/状态/版本和原授权/账号聚合数；完整目录备份可复用阶段4的本机备份，11行原值未变。
- Platform 启动器先核对原始完整目录并准备自身合成夹具，再由人工步骤执行11行临时启用；应用本身不运行这些SQL。
- 只给本次合成 Operations 账号的自有角色分配本期权限，用于管理、租户只读、成员只读与拒绝场景；不改变原PLATFORM_ADMIN或其他旧角色授权。
- 合成业务数据包含CLIENT/PARTNER、同CUSTOMER账号跨租户成员、不同有效状态的角色/权限来源及一条可验证Client凭证。所有ID由夹具追踪，正常退出清理自身账号、业务数据、会话、审计和命令。
- Web生产构建→本机入口代理→真实Gateway→真实BFF→真实Platform；监听均为Loopback随机端口。开发Vault/签名适配器和本机SimpleDiscovery沿用已确认测试机制，不替代任何本期业务响应。
- 验收结束先停止入口/Gateway/BFF，Platform等待审计结束后清理自身夹具；最后人工执行restore.sql并核对目录恢复、原管理员关联仍撤销及无合成数据残留。异常退出先核对归属，不扩大删除或整表恢复。

单独确认来源：本需求[数据方案第5节](../../data-design.md#5-目录登记启用及示例)及[上次本机应用授权](../local-application.md)将启用留作后续确认。提出申请时用户已确认阶段5，但上次105行SQL授权明确“不启用”，因此单独申请精确的临时11行状态变更及合成验收数据范围。

## 执行接续记录

用户在本目录精确申请后回复“可以”，批准本机临时启用及合成验收。2026-09-14 手工连接 142841 核对 8/3 后启用并 COMMIT；验收完成后，连接 142898 核对 8/3 后恢复并 COMMIT。11 行最终 DISABLED/version=1，原管理员 3 条关联仍 REVOKED，自有夹具已清理。具体证据见 [SQL 回执](../../assets/stage5/local-activation.json)、[清理核对](../../assets/stage5/local-postcheck.json)和[验收报告](../../acceptance.md)。
