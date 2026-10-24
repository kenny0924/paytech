# commercial：商业关系快照与真实解析接入 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-22—23 | 来源 | commercial 二期执行链路的前置分析发现 acquiring 未接真实解析、资格检查无来源；用户决定拆分为前置 + 二期两个需求，并先自上而下讨论商户对接模式，形成 [2026-10-23 决定草案](../../../agent-docs/decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md)（D1—D13，五轮评审 R1—R21） | 已确认 |
| 2026-10-23 | 来源 | 用户确认 Q5：本轮只实现 C1 / C2，模型按五种模式一次设计；并要求阶段 1 增加“逐系统变动点”分析 | 已确认 |
| 2026-10-23 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R6、规则 B1—B7、逐系统变动点（第 7 节）、验收 AC-01—AC-12、待确认 Q1—Q6 | 待确认 |
| 2026-10-23 | 1 自查 | 对照决定草案 U1 / 9.5 自查：补 7.1 `t_party_role` 变动点（R1 原已承诺）；模型开关统一为 commercial 一处（`commercial.relationship-snapshot.enabled`），acquiring 只冻结返回版本，删去原 `acquiring.relationship-model.enabled`（B5 / 7.3 / 7.4 / AC-08 同步） | 已改稿 |

## 当前阶段与下一确认点

阶段 1 需求评审，待用户确认需求、范围、逐系统变动点、验收标准与 Q1—Q6 后进入阶段 2（现状差异与影响分析）。

## 实现 / 验证 / 部署状态

未开始；台账见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 发布前置记录（阶段 6 使用）

- 2026-10-23 工作流体验优化已上 mor-test（tag wfux-20261023，源 be688db7 + 10 个数据查询未提交文件）：platform 76587ed4…、ops-bff c9a70b8d…、ops-web a8bb58d7…；覆盖文件 requirements/20261022/platform-support-工作流体验优化/release/compose-candidate.yaml。为这三个服务的现行基线。
- 2026-10-23 code-mor-ae 随后替换 ops-web 为 4861cd0d…（源 main 069dca04 + 10 个数据查询未提交文件，回滚目标 a8bb58d7）。已完成且健康，窗口结束。ops-web 现行基线 = 4861cd0d；本需求的 ops-web 候选须基于 main 069dca04 或更新提交，并继续带上那 10 个数据查询未提交文件。
- 2026-10-23 code-mor-db（多语言管理，块 135）发布窗口进行中：约 1 小时内依次替换 platform / ops-bff / client-bff / ops-web / client-web，执行 mor_platform DDL，并把路由 239087906591948800 的策略由 CARD_BIN_CSV 改指 OPERATIONS_BFF_IMPORT（路由 id / 900s / CSV 格式检查不变，回滚恢复 CARD_BIN_CSV）。路由改指已于 08:56Z 完成并通过 BIN 导入回归（版本 6 VALID、8/8），无需跟进。窗口已结束，最终基线：platform 48032999…（i18n-20261023b）、ops-bff e4d913e0…（i18n-20261023a）、client-bff 9033b8fa…（i18n-20261023a）、ops-web fc3abaa7…（i18n-20261023b，另带 code-mor-55 的 10 个数据查询未提交文件）、client-web 280a2f38…（i18n-20261023a）；源 main ffdf872e + 多语言管理改动，已提交 main ed361c26 + 28939303（未推送）。这五个服务的候选须建在 main ≥ 28939303 之上；ops-web 仍需带 code-mor-55 的 10 个数据查询未提交文件，直到其提交。注意 code-mor-78 随后会再发一次 ops-web（加 37173818 的 F1 修复），届时 ops-web 以其镜像为准。此前的 wfux / 4861cd0d 基线作废。
- 2026-10-23 code-mor-db 发布“运营控制台多语言迁移”（需求 20261023）：Platform 多语言数据（operations-console 默认语言 en、19 个命名空间、约 8.5k key、发布 #3），已上线：ops-bff 3751638e…、ops-web d228e3c8…（源 main 9f210b5c + 集成目录 /Users/kenny/Desktop/Workspace/Code-MoR-opsi18n 未提交改动，提交号待其通知）。本需求的 ops-bff / ops-web 候选须包含其改动。ops-web 源码禁止中文字面量（全量守卫测试）：新文案先加 `src/i18n/en/<ns>.json` 与 `src/i18n/zh-CN/<ns>.json`，再经“多语言管理”导入发布（开发约定第 10 节、`operations-console-web/docs/i18n-migration-guide.md`）；控制台默认英文。另：Foundation SnowflakeIdGenerator 同毫秒序列 4096 用尽即抛 ID_GENERATION_ERROR，种子 / 回填单次批量勿超 4096 个 ID。
- 2026-10-23 07:45—07:49Z code-mor-78（billing 支付事实自动登记）已替换 mor-test 的五个容器：billing 53450948…、account 17831407…、ledger 3819ff28…（源 main 22ede435 + billing-b）；payment cf9097ea…、acquiring 27c36f4e…（源 main d3d39e40 + billing-b，含 card BIN / 3DS）。acquiring / payment 现行基线以此为准。billing-b 已提交 main 98210e0b（未推送）：本需求的 acquiring 候选须建在 98210e0b 或更新提交之上；PaymentSucceeded v2 在 cardLevel 之后新增 channelTransactionRefId / channelReferenceType，消费者为 acquiring、card，OrderPaymentSucceeded 已注册。
