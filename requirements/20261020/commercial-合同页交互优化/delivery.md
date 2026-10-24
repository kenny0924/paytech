# commercial 合同页交互优化：确认记录

## 阶段安排（提议，待确认）

| 阶段 | 安排 |
|---|---|
| 1–3 需求、现状、方案 | 合并评审稿：[requirement.md](./requirement.md)、[technique.md](./technique.md)、Pencil 画板「商务 · 11 / 12 / 13」 |
| 4–5 研发与本地验收 | 合并 |
| 6–7 test 发布与验收 | 合并，执行前单独授权 |
| 8 提交与归档 | 本地提交（不推送），单独授权 |

## 记录

- 2026-10-20：用户反馈合同页交互不流畅、无引导；诊断与方向（A–F）获用户认可，确认本期只改前端、在 opsux 会话当前版本上叠加；已通知 code-mor-b2 暂停改 CommercialAgreements.tsx / AgreementDetail.tsx / AgreementModals.tsx。登记需求，阶段 1–3 评审稿与画板制作中。

- 2026-10-20：讨论中发现开通项维度与价目模型问题，整理为设计稿 [design-discuss/commercial-capability-pricing/design.md](../../../design-discuss/commercial-capability-pricing/design.md)。本需求「产品与开通」页签与开通弹窗（画板 11b / 11c）待该设计定稿后按新模型重画；其余 A、C、D、E、F 不受影响，等待用户确认开工。

- 2026-10-21：用户在「开通与定价模型调整」验收反馈中选择把「订阅产品 + 开通项」合并为「产品与开通」页签（按产品分区块），已在该需求 F6 提前实现；本需求 B 中「产品与开通」页签与开通弹窗部分不再重复实施，开工时以 F6 的实现为基础只做页签顺序 / 默认页签 / 完成标记。
