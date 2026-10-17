# Client Console 已交付功能收尾 — 需求

> 状态：2026-10-17 阶段 1 用户确认（“可以”：Q2—Q4 按建议；Q1 收件地址待提供，F-01 延后）。确认记录见 [delivery.md](./delivery.md)。

## 1. 来源与背景

- 来源：用户 2026-10-17 跳过订单查询后，“继续”选择推荐项 A：收尾已交付功能。
- 已交付的注册与入驻、租户与工作区、接入自助、团队成员与邀请都已部署 mor-test，但各自留有验证缺口或已知偏差（见各交付记录的遗留项）：
  - [团队成员与邀请](../client-console-团队成员与邀请/delivery.md)：真实收件箱收到邀请邮件、点开链接接受，从未验证（test SES 在沙箱，`@example.test` 被拒）。
  - [接入自助](../../20261016/client-console-接入自助/release/release-20261016.md)：开发者浏览器脚本 `developer_browser.mjs` 写死本机地址，test 未做浏览器验收。
  - [租户与工作区](../../20261016/client-console-租户与工作区/delivery.md)：CCT `t_party_profile.approved_time`（设计含义：资料首次批准并永久锁定的时间）从未写入；工作区首页“审核通过”日期取最近一份 APPROVED 申请的 `update_time`，申请记录之后任何更新都会改变这个日期。
  - [注册与客户入驻](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)：“换类型重新申请”会新建 Client，旧的 PENDING Client 与 PENDING 工作区一直留着（台账写“留到做工作区列表时清理”）。
- 已核对不再需要处理：旧脚本 `browser_acceptance.mjs` 的登录等待标题已是“我的工作区与申请”。

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 工作区所有者 / 管理员 | 发出的邀请真的能送到同事邮箱，同事点邮件里的链接就能加入 |
| 已批准的客户 | 工作区首页显示的“审核通过”日期稳定、准确 |
| 交付与运维 | 已上线功能在 test 上有完整的浏览器验收证据 |

## 3. 本期范围

| 编号 | 内容 | 说明 |
|---|---|---|
| F-01 | 真实邀请邮件补测 | 在 test 向一个用户在 SES 已验证的收件地址发邀请：邮件送达、模板内容正确、点开邮件链接后完成接受。只做验证，预期不改代码；发现问题按缺陷修复 |
| F-02 | 开发者浏览器验收上 test | `developer_browser.mjs` 增加 `E2E_TARGET=test`（与 `workspace_browser.mjs` 的做法相同），在 test 跑一次 |
| F-03 | 写入资料批准时间 | 入驻申请批准时，CCT 在同一本地事务写 `t_party_profile.approved_time`（只写一次，之后不变）；工作区摘要的 `approvedAt` 改取该字段；已批准的存量 Client 按其批准记录回填 |

## 4. 本期不做

- 订单、退款等业务页面（用户已跳过）。
- 清理“换类型重新申请”留下的旧 PENDING Client / 工作区（见 Q2）。
- 改变 test SES 的沙箱状态或发件配置（属环境配置，由用户在 AWS 处理）。
- 其他已知限制：工作区暂停时发起邀请返回 401、20 条待接受上限不加锁、即时邮件失败原因只进指标、锁键前缀不一致（D7）。

## 5. 验收标准

### 功能验收

| 编号 | 场景 | 期望 |
|---|---|---|
| AC-01 | test 向已验证地址发邀请 | 接口返回 `emailDelivery=SENT`；收件箱收到中文邀请邮件，正文只含说明与链接，不含工作区名称和发起人邮箱原值 |
| AC-02 | 点开邮件链接 | 打开 `/invitations/accept`，地址栏不保留 Secret；登录受邀邮箱后接受成功，“我的工作区”出现该工作区 |
| AC-03 | test 开发者浏览器验收 | `E2E_TARGET=test developer_browser.mjs` 全部步骤通过，本机行为不变 |
| AC-04 | 新批准的申请 | 批准后 `t_party_profile.approved_time` 非空，等于批准结论的时间；工作区首页“审核通过”显示该日期 |
| AC-05 | 批准后申请记录再被更新 | 首页日期不变 |
| AC-06 | 存量已批准客户 | 回填后每个已批准客户的 `approved_time` 非空；已经非空的不覆盖；回填脚本可重复执行 |
| AC-07 | 资料锁定语义 | `approved_time` 非空后资料仍不可再编辑（与现有“APPROVED 永久只读”一致），被拒后重新申请的流程不受影响 |

### 视觉 / 交互验收

本期没有页面改动；AC-02、AC-03 的浏览器步骤沿用已交付页面。

## 6. 已确认事实与假设

- 已确认（代码与表结构）：`t_party_profile.approved_time` 已存在（`client-contract-service/docs/database/sql/client-contract-schema.sql:24`），注释为“资料首次批准并永久锁定的 UTC 时间；未批准为空”；生产代码只在“被拒后重新申请”转移草稿资料时读取 `approved_time IS NULL`（`ClientDraftMapper.xml:41`），没有任何地方写入。工作区摘要的 `approvedAt` 取 `MAX(update_time)`（`ClientApplicationMapper.xml:42-49`）。`developer_browser.mjs:19` 写死 `https://127.0.0.1:18443`。
- 已确认（环境）：test SES 账户仍在沙箱，只能发往已验证身份；SES 模拟地址已验证 `SENT`。
- 假设：F-03 无 DDL（列已存在），有一份 CCT 回填 DML；需要在 test `mor_cct` 执行。

## 7. 待决问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | F-01 用哪个收件地址？ | 请提供一个你在 SES（ap-southeast-1）已验证、并且你能打开的邮箱地址。我只用它收这一封邀请，邀请接受后从验收数据里记录脱敏值，不写入仓库 |
| Q2 | 换类型留下的旧 PENDING Client / 工作区要不要清理？ | 本期不做：工作区列表已排除 PENDING 工作区，客户看不到；清理会改动 CCT 与 Platform 的生命周期状态，没有当前消费者需要它，按必要性门禁暂不新增 |
| Q3 | F-03 存量回填用哪个时间？ | 取该 Client 最早一份 APPROVED 申请的 `update_time`（即当时的批准时间，之后未再更新的情况下与结论时间一致），并在回填脚本中说明这是近似值 |
| Q4 | 阶段安排 | 三项都小，建议合并：阶段 1 → 2+3 合并（差异与设计一次评审）→ 4+5 合并（研发与本机验证）→ 6+7（test 发布与验收）→ 8；每个合并点仍停下等你确认 |

## 8. 适用阶段

见 Q4。F-03 有 CCT 代码与 DML，需要 test 部署 CCT 与 Client BFF（如字段来源变化影响 BFF 则一并）；F-01、F-02 只做验证与脚本。
