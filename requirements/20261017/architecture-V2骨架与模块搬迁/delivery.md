# V2 骨架与模块搬迁 交付记录

## 阶段确认

| 日期 | 阶段 | 结果 | 确认 |
|---|---|---|---|
| 2026-10-17 | 阶段 1 · 需求评审稿 | 范围、搬迁顺序、验收标准已起草；Q1–Q4 已由用户选项确认（paytech/ 根、refactor-main 集成分支、卡域内模块、规范草案只对 paytech/ 生效） | 用户确认（“提交到 refactor-main，然后进入阶段 2”） |
| 2026-10-17 | 阶段 2 · 现状差异与影响分析 | 六份只读盘点（analysis/）与汇总 [technique.md](./technique.md)：逐系统模块 / 表归属 / 跨 core SQL·事务·调用 / 契约 / 测试基线，拆库可行性分级，S1–S10 待决设计点，跨系统风险排序 | 用户据此决定：每个逻辑服务独立部署（D09 修订）、账户/会计与支付/卡域两对在阶段 0 重做事务逻辑并彻底拆开（D36 修订）。阶段 2 结论其余部分待用户确认 |

## 前置决定

D09、D11、D22、D24、D25、D26、D31、D33、D34、D35、D36，见[决定草案](../../../agent-docs/decisions/2026-10-17-target-architecture-v2.md)。

## 数据库变更

不涉及结构变更；表归属变化在阶段 3 以归属表记录，不生成 SQL。

## 遗留项

- 阶段 2 发现的三条前提修正：Direct Pay 无实现（能力标签改为已设计）；消费者 Vault Token 按订单而非按 Customer 保存且 Protect 为 Mock；Commerce 在 CreateCheckout 一次性冻结、Schema 仍描述已删除的准备阶段。
- 阶段 0 内 Finance account/ledger 与 Payment card/payment 只分模块不分库（见 technique.md 第 5 节）。

- 推送、整体合并到 main、规范修订各自单独授权；重构期间不合回 main。
