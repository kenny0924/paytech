# account 明细账与 ledger 总账控制科目决定

- Decision status: Confirmed
- Implementation status: Implemented, not committed（2026-10-19 阶段 7，需求 [requirements/20261019/finance-account明细账与ledger控制科目重构](../../requirements/20261019/finance-account明细账与ledger控制科目重构/requirement.md)；代码与文档尚未提交）
- Deployment status: Deployed to mor-test（2026-10-19 10:54Z，镜像 tag `subledger-20261019`，SQL 已执行，旧 `ledger.billing.*` 已删除，验收 58/58、31/31、34/34；详见需求目录 `release/release.md` 第 6 节与[当前台账](../CURRENT_IMPLEMENTATION.md)）
- Decision date: 2026-10-18
- 用户决定：account 与 ledger 改为「明细账在上游、总账控制科目在下游」。用户理由是记账与余额变动应只推导一次；Client / Partner 维度与余额桶属于账户系统，不应在会计系统重复维护。讨论中用户逐项确认方案 3、控制科目方案、银行现金额度归 account、六项运行约束与五项待确认事项（“按建议”）。
- 详细设计：[account 与 ledger 模型划分（方案 3）](../../design-discuss/finance-account-ledger-v3/design.md)，闭环规则见其第 8.5–8.10 节，配套图 [finance-account-ledger-v3.pen](../../design-discuss/finance-account-ledger-v3/finance-account-ledger-v3.pen)。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

第 11b 步按 [D05、D16、D37](./2026-10-17-target-architecture-v2.md) 与 [design/04](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/04-finance-account-ledger-split.md) 拆分 account 与 ledger：两者各自消费 billing 的 `EconomicFactRecorded`，各自推导 Owner 借贷，日终比对金额。由此带来：

- 两套映射规则（account `FactBalanceMappingRule` 与 ledger 规则包 Owner 行）必须保持语义一致；
- 差异只能事后由日终核对与 `t_balance_discrepancy` 发现；
- account 同步调用 ledger 取账簿与分录合计，形成双向依赖；
- Client / Partner × 余额桶在 ledger `t_ledger_account` 与 account 各维护一份，桶移动还需回传 ledger 补记分录；
- 出款与缴税共用的银行现金额度原本锁 ledger 的 BANK_CASH 账户行计算，11b 删除余额列后已无落脚点。

## 2. 决定

1. **链路单向：billing → account → ledger。** `EconomicFactRecorded` 只由 account 消费；account 入账后发布 `AccountPosted`，ledger 只消费 `AccountPosted`，非 Owner 明细在事务外经 billing-api 读取。account 不再调用 ledger。
2. **account 是明细账。** 拥有 Client / Partner 账户主数据（开户、Owner、币种、科目代码、余额桶、`ACTIVE / FROZEN / CLOSED` 状态）、统一流水与实时余额，三者同一事务。经济事实 → Owner 借贷的映射只在 account（先用代码）。
3. **ledger 只记控制科目。** Owner 科目每账簿每科目一个控制账户，Owner 取账簿主体本身、`balance_bucket = NONE`；`t_ledger_account.owner_type` 不再允许 `CLIENT`、`LOCAL_PARTNER`。分录行以 `account_entry_id` 指回 account 流水供下钻。渠道账户、税务机关、银行存款科目维度保留在 ledger。
4. **余额桶只在 account。** 桶移动（成熟释放、出款锁定、银行现金占用与释放）不产生总账分录；`BalanceMoved` 删除。
5. **余额归属原则：会实时卡住业务命令的余额在 account，其余所有余额都在 ledger 并由分录推导。** account 管 Client / Partner 应付各桶、保证金、追偿应收与银行现金可用额度；财务主体收入与应收、控制科目总额、经营主体收入与费用等均在 ledger。平台利润由收入、费用科目推导，期末经损益结转固化，account 中没有利润账户。
6. **银行现金可用额度归 account。** `owner_type = CASH_ACCOUNT`，每个银行账户一个账户，以 AVAILABLE / LOCKED 两个桶表达可承诺与已占用；`UNKNOWN` 不释放。ledger 按银行账户保留「银行存款」科目账户用于报表与对账。银行账户主数据仍归 reconciliation。
7. **科目与账户类型。** 科目的存在、名称、类别与正常方向以 ledger 科目表为准；account 在 account-api 中维护 Owner 账户类型目录（科目代码、正常方向、允许的桶与 Owner 类型），ledger 初始化与测试时校验一致。account 按需开户，运行时不读 ledger 科目。（2026-10-19 补充：目录另增科目分级编号这一静态属性，用于生成可读账户号，ledger 测试核对它与 STANDARD 科目表一致，见 [科目分级与可读账户号决定](./2026-10-19-ledger-chart-hierarchy-and-account-numbers.md)第 2 节第 8 条。）
8. **经济事实唯一来源不变。** 渠道结算到账、出款完成、缴税完成等改变余额的金额，由 reconciliation、payout、tax 调用 billing 登记经济事实后走同一链路；不影响 Owner 的事实也经过 account，生成 0 条流水的入账批次，ledger 只有一个上游。
9. **一致性由构造保证，删除金额核对。** 删除 `t_balance_discrepancy`、日终核对任务与 ledger `account-totals` 接口。关账步骤 `RECONCILE_ACCOUNT_BALANCES` 改为 `CHECK_ACCOUNT_POSTING_PROGRESS`，在一个步骤内做两段检查，任一段未追平即失败关闭：billing → account（已批准事实全部入账），account → ledger（各分片 `posting_sequence` 已追平）。`t_trial_balance_exception.resolution_type` 删除 `PROJECTION_REBUILD`。
10. **余额重建以 account 流水为唯一来源。** 重建按本库 `t_account_entry` 重算；billing 重放只用于核对事实入账是否齐全。
11. **运行约束。** 入账序号在主体 + 币种内串行，`shard_no` 预留并进入唯一键与事件合同，当前固定 0；ledger 处理失败时自动重试、超限告警并阻断关账，修复后从原批次继续，不允许跳过；payout、tax 占用额度按执行 ID 幂等，并以本地补偿事件保护「已锁定但执行未建成」；ledger 现有按 Client / Partner 开立的账户与分录只有本机与测试数据，不做历史迁移，按新模型清空重建。
12. **闭环规则（2026-10-18 逻辑验证后补充）。**
    - 到期转可用由 account 定时任务按事实的 `balance_available_time` 触发桶移动，不经 billing。
    - 退款扣减顺序 AVAILABLE → PENDING，不动 LOCKED 与保证金，不足记追偿应收；争议败诉仅在 `outcome=LOST`、`principal_bearer=CLIENT`、`security_deposit_use_allowed=1` 时先用保证金，其余同退款。扣减结果写入流水，ledger 照抄。
    - 账户状态只约束命令，绝不拒绝经济事实；`CLOSED` 账户收到事实照常入账并告警。
    - 关账两段检查按期间覆盖全部相关事实与全部入账流，不只看账簿自身主体。
    - 分录日期取事实业务时间；所属期间已关账时记入其后最早的开放期间，过账日期取该期间开始日并记告警，原业务日期经经济事实追溯（2026-10-19 阶段 4 修订：试算与损益结转按过账日期取数，保留原日期会使迟到分录永不结转）。
    - 经营主体开始交易前必须已建 ledger 账簿；银行现金期初与非业务资金变动经 billing `MANUAL_ADJUSTMENT` 登记。
13. **模型维护。** `account.pdma.json` 已脱离 Finance 生成器直接维护；`ledger.pdma.json` 实施时同样脱离生成器。

## 3. 取代范围

| 被取代的内容 | 取代方式 |
|---|---|
| [目标架构 V2 决定](./2026-10-17-target-architecture-v2.md) D05「同一事件驱动、周期核对，差异进差错工单」 | 改为 account 消费 billing、ledger 消费 account 入账；一致性由构造保证，关账前两段进度检查 |
| 同上 D16「账户与会计只消费计费的不可变经济事实」 | 改为账户消费计费事实、会计消费账户入账；会计事务外读取计费明细 |
| 同上 D37「会计消费同一事实自动过账」及「账户……不消费会计过账事件」 | 前半句取代；后半句不变（account 仍不消费会计事件） |
| [design/04](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/04-finance-account-ledger-split.md) §1 第 4 条、§3.2、§4 投影重建行、§5、§6 关账行、§7 | 由本决定与详细设计取代；§3.1、§4 其余行与 11b 实现记录保留为历史 |
| [`FINANCE_DATABASE.md`](../database/FINANCE_DATABASE.md) §4.1 `account` 的余额投影与 Client / Partner × 桶开户、§4.4 成熟与锁定的 Journal 表达、§4.7 第 3 步 `RECONCILE_ACCOUNT_BALANCES`、§4.10 `BalanceChanged`、§6.3 BANK_CASH 账户行门禁、§6.9 合同余额成熟与 Payout 锁定、§7 第 4、32 条 | 余额桶、Owner 明细、银行现金额度与桶移动以本决定为准；科目、账簿、Journal、试算、关账等其余内容不变 |

`AGENTS.md` 中出款锁定 `AVAILABLE → LOCKED`、按同一 `BANK_CASH` 账户串行占用出款额度、保证金与追偿科目等表述仍成立，执行方改为 account，不改正文。

## 4. 已知代价

- 单个主体 + 币种内的入账串行分配序号，大主体吞吐受限；需要时按账户扩展分片。
- ledger 依赖 account：account 不可用或某批次卡住时，ledger 对应主体整体延后，关账失败关闭。
- ledger 中看不到客户与余额桶，需经 `account_entry_id` 下钻 account；客户维度报表由 account 提供。
- payout、tax 占用额度是跨服务两步操作，依赖幂等与补偿事件，不由 account 单方保证。

## 5. 不变的边界

- billing 仍是唯一经济事实来源；影响 Owner 余额的人工调整不得直接在 ledger 提交。
- 科目表、账簿、财务日历、期间、Journal、人工调整（纯科目重分类）、试算与关账仍归 ledger。
- 经营主体归 CCT（client-master），财务主体归 ledger，银行账户与用途绑定归 reconciliation。
- 不恢复通用跨主体转账表；出款银行账户必须与被扣款余额属于同一主体。
- 事务公理不变：跨服务调用都在本地事务之外。

## 6. 后续

- 阶段 2：按详细设计第 4、5 节修改 `account.pdma.json`、`ledger.pdma.json` 与对应 Schema / 测试 DDL / 初始化 DML；由需求 [requirements/20261019/finance-account明细账与ledger控制科目重构](../../requirements/20261019/finance-account明细账与ledger控制科目重构/requirement.md) 承接（模型、SQL、代码、控制台与注册数据同一需求交付）。
- 渠道结算到账、出款完成、缴税完成登记经济事实的具体合同，在各自切片中设计。
- 是否把本决定登记到 `DOCUMENT_GOVERNANCE.md` 路由表，与目标架构 V2 决定一并由用户决定。
