# Processor 统一到 Foundation 模板（AbstractProcessor / AbstractLockProcessor）并以 R14 固化

- 日期：2026-10-18；来源：用户审阅 V2 代码发现多处 Processor 未按 [2026-09-16 决定](../../../agent-docs/decisions/2026-09-16-payment-based-coding-conventions.md) 第 8 条继承 Foundation 模板。
- 现状（2026-10-18 统计 `paytech/` 主代码）：342 个 `*Processor`，319 个已继承；核心服务未继承 9 个：client-master 5（`ClientDraftProcessor`、`ClientPeopleProcessor`、`ClientDocumentProcessor`、`ClientFormOptionsProcessor`、`ClientWorkspaceProcessor`），platform-support 4（`GatewayConfigurationManagementProcessor`、`ReviewFileDownloadProcessor`、`ReadOnboardingFileProcessor`、`DownloadOnboardingFileProcessor`，后两个经 `AbstractOnboardingFileAccessProcessor` 未挂到模板）；acquiring `AbstractGatewayProcessor` 为无子类死代码。两个 BFF 的 11 个聚合 Processor **本期不处理**（用户另有安排）。
- 目标：核心服务（`paytech/<service>/paytech-<service>-core` 的 `service/processor` 包）的全部 `*Processor` 直接或间接继承 `AbstractProcessor`；需要资源锁的用 `AbstractLockProcessor` + `LockCommand`，并保持原互斥域；新增 ArchUnit 规则 R14 固化，范围排除 BFF 与 Foundation。
- 约束：不改 API 路径、错误码、HTTP 状态与业务规则；一入口一 Processor（`GatewayConfigurationManagementProcessor` 的多方法要拆）；锁迁移对照 `ResourceLockKeyResolver` 的 `prefix:key` 保持 Key 与等待 / 租约；测试先行，禁止 Mock 替换生产链。
