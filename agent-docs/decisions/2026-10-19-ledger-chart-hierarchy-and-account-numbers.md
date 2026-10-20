# ledger 科目分级、科目维护与可读账户号决定

- Decision status: Confirmed（2026-10-19 用户确认阶段 1、2 并授权连续完成到 mor-test 部署；E1 允许、E2 本期不做「发起试算」）
- Implementation status: Implemented, committed locally（2026-10-20，未推送）
- Deployment status: Deployed to mor-test（2026-10-20，tag `chart-20261020`，见需求目录 `release/release.md`）
- Decision date: 2026-10-19
- 需求：[requirements/20261019/ledger-会计科目分级维护](../../requirements/20261019/ledger-会计科目分级维护/requirement.md)，技术方案见同目录 `technique.md` 第 2 节。
- 用户决定：科目按正常财务系统的方式分级维护（IFRS 五类、4-2-2-2 编号）；科目变更不审批、直接生效并留痕；科目可停用；科目余额与试算平衡按级次汇总；财务人员可读的账户号纳入本期，只用于显示与搜索。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

`t_chart_account` 已有 `parent_account_id` 与 `posting_allowed`，但 STANDARD 科目表 29 个科目全部平铺、全部可记账，代码不读上级字段，也没有科目维护入口。科目代码（`CLIENT_PAYABLE` 等）是 ledger 规则行、account 账户类型目录、`AccountPosted` 与控制台共用的业务键，没有财务意义上的编号。账户只有雪花 ID，财务人员无法直接识别。

## 2. 决定

1. **科目编号与系统代码并存。** `t_chart_account` 新增 `account_no`：4-2-2-2 数字编号，一级 4 位、每下一级加 2 位、最多 4 级；一级首位按会计要素 1 资产、2 负债、3 权益、4 收入、5 费用；下级编号必须以上级编号开头。级次由编号长度推出，不另存。`account_code` 保持为程序引用的稳定键，含义与取值不变。
2. **树结构规则。** 继续只用 `parent_account_id` 表达直接父子；父子同表、无环、父科目 `posting_allowed = 0`、子科目类别与父科目相同（余额方向可以不同，以容纳备抵科目）。汇总时在查询中向上累加，不建闭包表。
3. **采用后冻结范围扩大到编号。** 科目表被任一账簿采用后，既有科目的 `account_no`、上级、`account_code`、类别、余额方向、`posting_allowed` 全部冻结；仍允许追加科目、修改名称、停用与启用。
4. **科目启停（修订 [FINANCE_DATABASE.md](../database/FINANCE_DATABASE.md) §4.1 与 [FINANCE_PHYSICAL_DECISIONS.md](../../paytech/ledger/docs/database/FINANCE_PHYSICAL_DECISIONS.md) §4.11 中"科目没有状态"的表述）。** `t_chart_account` 新增 `account_status`（`ACTIVE` / `INACTIVE`）。`posting_allowed` 仍是永久结构属性，不承担启停。停用后不能新建账户、不能被新规则版本引用，已有账户照常记账与查询。被当前生效规则版本、损益结转规则或 account 账户类型目录引用的科目不能停用；父科目只有在下级全部停用后才能停用；启用下级时上级必须为启用状态。
5. **维护不审批、直接生效、留痕。** 科目表与财务主体一样属于基础资料，不进入双人复核清单。每次新增、修改、停用、启用写一条不可变的变更记录（操作人、时间、变更前后的编号、上级、名称、状态）。同一科目表的维护与账簿采用使用同一把科目表资源锁串行。
6. **级次汇总在查询时计算。** 试算平衡继续只持久化末级科目行，内容哈希与关账审批不变；科目余额与试算平衡明细在查询结果中附带上级、级次与汇总行。
7. **可读账户号。** ledger `t_ledger_account` 与 account `t_owner_account` 各新增 `account_no`，在账户创建的同一本地事务中生成，此后不变、各自表内唯一：
   - ledger：`{主体}-{币种}-{科目编号}-{序号}`，序号按"账簿 + 科目"从 1 递增、至少 4 位，例如 `AE001-USD-22010101-0001`；
   - account：`{主体}-{币种}-{科目编号}-S{序号}`，序号按"主体 + 币种 + 科目代码"从 1 递增、至少 6 位，例如 `AE001-USD-22010101-S000001`；`S` 表示明细账账户，与总账账户区分；
   - 主体段：Operating Entity 用 `entityNo`；Finance Entity 在 ledger 用财务主体代码，在 account 用 `FE` 加主体主键（account 运行时不读 ledger）。
   账户号只用于显示与搜索，不进入事件载荷，不作为跨系统关联键，不作为对外账户号。
8. **account 取得科目编号的方式（补充 [2026-10-18 决定](./2026-10-18-account-ledger-subledger-control-account.md)第 7 条）。** account-api 的 Owner 账户类型目录增加科目编号这一静态属性；ledger 测试核对它与 STANDARD 科目表一致。account 运行时仍不读 ledger。

## 3. 影响

- ledger 新增科目维护与变更记录接口、科目余额查询，扩展科目、账户、分录与试算平衡的查询结果；account 扩展账户查询结果与筛选；Operations BFF / Web 新增科目树维护、试算平衡与科目余额页签、账户号显示与搜索；Platform 登记对应路由。
- STANDARD 科目表重建为三级树（2026-10-20 用户变更，原定两级）：新增 19 个一级汇总科目（4 位）与 26 个二级汇总科目（6 位），29 个既有科目 ID 与代码不变，挂为三级末级（8 位）并补编号，共 74 个；科目清单见需求技术方案 §2.1。执行前提是没有账簿采用 STANDARD（mor-test 演示数据先清理，用户 2026-10-19 已同意）。
- reconciliation、payout、tax 只在注释中引用科目代码，不受影响。
