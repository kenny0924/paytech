# V2 骨架与模块搬迁 交付记录

## 阶段确认

| 日期 | 阶段 | 结果 | 确认 |
|---|---|---|---|
| 2026-10-17 | 阶段 1 · 需求评审稿 | 范围、搬迁顺序、验收标准已起草；Q1–Q4 已由用户选项确认（paytech/ 根、refactor-main 集成分支、卡域内模块、规范草案只对 paytech/ 生效） | 用户确认（“提交到 refactor-main，然后进入阶段 2”） |
| 2026-10-17 | 阶段 2 · 现状差异与影响分析 | 六份只读盘点（analysis/）与汇总 [technique.md](./technique.md)：逐系统模块 / 表归属 / 跨 core SQL·事务·调用 / 契约 / 测试基线，拆库可行性分级，S1–S10 待决设计点，跨系统风险排序 | 用户据此决定：每个逻辑服务独立部署（D09 修订）、账户/会计与支付/卡域两对在阶段 0 重做事务逻辑并彻底拆开（D36 修订）。阶段 2 结论其余部分待用户确认 |
| 2026-10-17 | 阶段 3 · 方案、接口与数据设计 | [design/](./design/) 12 份：总览与一句话规模、骨架与模块约定、ArchUnit 规则、服务与表归属、账户与会计分库（S1）、卡域独立票据（S3）、CCT 两段式解析（S4/S5）、Risk 拆分（S2/S8）、paytech-messaging（D24/D34）、路径耦合修复、测试基线与清理、研发计划（14 步、任务卡、门禁） | C1–C7 已由用户逐项确认并回写；待用户确认整体方案后进入阶段 4 |
| 2026-10-17 | 阶段 4 · 研发（进行中） | 已合入 refactor-main：00 骨架、01 Foundation（paytech-messaging、paytech-internal-auth）、02a Platform（含 D41 `t_currency_spec`）、02b Gateway、02c BFF、02d Vault、03 acquiring + consumer、04 client-master + commercial（两段式解析）、05 payment + channel-connector、07 Finance 五服务、09 数据服务骨架、10 迁移执行器；合入 03 后整 Reactor `clean verify` 6011 个测试 0 失败；每步独立 worktree + Opus agent，主会话门禁（报告、改动范围、独立复跑）；合入后整 Reactor `clean verify` 4642 个测试 0 失败；`build.sh` 改为单 Reactor 打包。2026-10-18：06（Risk + compliance）、08（消费方切新信封、旧 Topic 停发）、12（card 独立：一次性票据、两段创建、PaymentSucceeded v2）、11a（billing Maker-Checker 与 EconomicFactRecorded，本地 mor_billing 增量 DDL 已由用户授权执行）已合入。发现跨模块测试隔离缺陷：producer 测试真实发布的事件落在其它服务持久分区队列，消费方测试启动即消费残留，污染 t_consumed_event（card 37/38 失败，acquiring 早先的大量失败同源）；以 12x 修复（监听器启动前清空本服务队列，Foundation 可复用件）。12x、11b（账户与会计分库，含 MANUAL / COST 调整事实的规则包与 account 双消费）已合入；全部搬迁步到齐后整 Reactor `clean verify` 8218 个测试 0 失败（42 模块，19:49）。进行中：13 收口（旧根、链接重写、compose / build.sh 服务拆分、状态台账）；其后阶段 5 全链路验证。2026-10-17 晚宿主磁盘写满导致 Docker / Redis / MySQL 中断一次，恢复过程见 tasks 报告与主会话记录。报告见 `tasks/*-report.md` | 进行中 |

## 前置决定

D09、D11、D22、D24、D25、D26、D31、D33、D34、D35、D36，见[决定草案](../../../agent-docs/decisions/2026-10-17-target-architecture-v2.md)。

## 数据库变更

不涉及结构变更；表归属变化在阶段 3 以归属表记录，不生成 SQL。

## 遗留项

- 阶段 2 发现的三条前提修正：Direct Pay 无实现（能力标签改为已设计）；消费者 Vault Token 按订单而非按 Customer 保存且 Protect 为 Mock；Commerce 在 CreateCheckout 一次性冻结、Schema 仍描述已删除的准备阶段。
- 阶段 0 内 Finance account/ledger 与 Payment card/payment 只分模块不分库（见 technique.md 第 5 节）。

- 推送、整体合并到 main、规范修订各自单独授权；重构期间不合回 main。
