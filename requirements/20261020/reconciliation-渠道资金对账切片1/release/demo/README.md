# 对账控制台演示数据（mor-test）

让对账 7 个页面和主要状态都有内容。数据**持久保留**，不随脚本清理；需要移除时用 `demo_cleanup.py`。由主会话执行，子 Agent 只写脚本、做 `--dry-run`。

## 运行

```bash
cd requirements/20261020/reconciliation-渠道资金对账切片1/release/demo
python3 -I demo_seed.py --dry-run          # 只打印计划，做只读 GET / SELECT 与 XXL 只读核对
python3 -I demo_seed.py                    # 写入；结束时停用演示复核人
python3 -I demo_seed.py --keep-checker     # 保留演示复核人（可在页面上演示复核）
python3 -I demo_seed.py --backdate-exceptions   # 可选：未结异常账龄前移（直接 UPDATE mor_reconciliation）
python3 -I demo_seed.py --resume ~/Code-MoR-backups/recon-demo/demo-<ts>.json   # 中途失败后续跑
```

- 复用 `../acceptance/recon7_common.py`（入口 `http://localhost:28080`、IAM、`ReconBillingFactPublisher.java` 合成事实、只读 SQL）和 `../scripts/xxl_reconciliation.py`（手动触发 `channelLineWaitExpiry`）。
- 预检：reconciliation 运行镜像必须是切片 1 候选镜像；管理员导航有对账分组；mor-test 上没有 `NI-AE001-USD*` 清算账户、`DEMO_NI` 渠道和 AE001 USD 结算户。有任何一项就中止（不覆盖），先清理或用 `--resume` 续跑。
- 任一调用不符合预期就停在该步，打印原因和续跑命令。每个写步骤完成后都记入记录文件，续跑时跳过已完成的步骤。
- 合成 `BillingFactRecorded` 只投 `reconciliation.billing.hash`，account 收不到。
- **预期的 FAILED 补偿任务**：CLASSIFY 批准、账单对平、银行匹配会登记 `ChannelBatchReconciled`、`ActualChannelCostConfirmed`、`ChannelSettlementConfirmed`、`ReconAdjustmentApproved`。这四类事件目前没有订阅方（I-08），所以对应的补偿任务会留存 FAILED。

## 会创建什么

日期以运行当天 D0 往前推。经营主体 AE001，币种 USD。

| 对象 | 内容 |
|---|---|
| 接入方 | `DEMO_NI`（渠道激活要求有同编码接入方；地址 `https://demo-ni.example.test/api`，sandbox；没有 API、脚本或实现，不能执行）。如果已存在就沿用，不新建 |
| 渠道 | `DEMO_NI` ACTIVE；AE001 技术账号（随机合成参数，走 mor-test Vault 真实 Protect）；MID `DEMO-NI-AE001-01` / `-02`，都是 ACTIVE，各带 USD 能力组 |
| 人员 | Maker 是管理员（`test-environment/private/access.md`）；Checker 是 IAM 新建的 `DEMO_RECON_CHECKER_<ts>`（对账读 + 审批 + 财务读）。登录名和口令只写在记录文件（0600），不打印。默认在结束时停用（不删除），角色保留 |
| 银行账户 | 「AE001 NI 结算户」ENBD，末四位 6021，ACTIVE（Maker 提交、Checker 批准）；「AE001 出款户（待复核）」PAYOUT，末四位 7710，停在 PENDING_VERIFICATION |
| 清算账户 | `NI-AE001-USD`（等待 3 天，容差 0.10，到账宽限 2 天，挂 MID-01）；`NI-AE001-USD-B`（等待 0 天，挂 MID-02）；都指向结算户 |
| 平台流水 | 17 条：PAYMENT 14 笔（120.00、85.50、300.00、49.99、1250.00、210.00、75.00、560.00、99.00、150.00、42.00、64.00、18.00、65.00，都带预估渠道成本），REFUND 3 笔；业务日期分布在 D-10 到 D0 |

### 渠道账单

| 账单 | 结算日 | 结果 |
|---|---|---|
| `NI-<D-7>-AE001-A` | D-7 | 4 条交易行 + 月度服务费调整行 15.00 → UNEXPLAINED_ADJUSTMENT → CLASSIFY `CHANNEL_FEE_OTHER`（Checker 批准）→ RECONCILED；银行到账 468.93 匹配 → **CASH_CONFIRMED**。300.00 那笔实际费 0.95、预估 0.90，在容差内 |
| `NI-<D-4>-AE001-A` | D-4 | 自动对平 **RECONCILED**，净额 1455.47；银行只到 1455.27 → 人工部分匹配，预期银行流水 PARTIALLY_CONFIRMED，**BANK_AMOUNT_MISMATCH 0.20 未结** |
| `NI-<D-2>-AE001-A` | D-2 | **MATCHING**：78.00 对平台 75.00 → AMOUNT_MISMATCH（阻塞，未结）；560.00 实际费 2.40、预估 1.68 → COST_DEVIATION 0.72（阻塞，未结）；滚动保证金扣留 56.00 → CLASSIFY `CHANNEL_RESERVE_HOLD` 已提交，**待复核**（异常 UNDER_REVIEW）。详情页能看到门禁未满足项 |
| `NI-<D-1>-AE001-A` 第 1 版 | D-1 | 表头总额写成 172.00 → **REJECTED** |
| 同批次第 2 版 | D-1 | 渠道多加了一条 4.50 调整行 → 作废 → **VOIDED**（其异常 AUTO_CLOSED） |
| 同批次第 3 版 | D-1 | **RECONCILED**，在途（银行还没到账） |
| `NI-<D-1>-AE001-B` | D-1 | 清算账户 B：65.00 已匹配；230.00 在平台没有对应流水 → 等待 0 天 → XXL `channelLineWaitExpiry` → **CHANNEL_LINE_UNMATCHED**，账单停在 MATCHING。如果手动触发失败，定时任务（每 10 分钟）随后会开 |

### 银行对账单（结算户 6021，期初 10,000.00）

| 对账单 | 期间 | 内容 |
|---|---|---|
| `ENBD-6021-<D-8>-<D-5>` | D-8..D-5 | 468.93 结算到账（确认系统建议；没有建议时人工匹配）；利息 3.12 → 人工开 BANK_LINE_UNMATCHED → CLASSIFY `BANK_INTEREST`（Checker 批准，RESOLVED） |
| `ENBD-6021-<D-4>-<D-2>` | D-4..D-2 | 与上一张连续；1455.27 部分匹配 D-4；银行手续费借记 25.00 → BANK_LINE_UNMATCHED **未结** |
| `ENBD-6021-<D-1>-PROV` | D-1..D0 | 期末余额不平 → **REJECTED** |
| `ENBD-6021-<D-1>-<D0>` | D-1..D0 | 期初 11,950.00 ≠ 上一张期末 11,902.32 → **BANK_STATEMENT_GAP** 47.68；ACME 来账 500.00 未匹配 |

### 其他

- 64.00 那笔付款（D-10）没有账单行 → 人工开 **PLATFORM_ITEM_UNSETTLED**（未结）；18.00 那笔（D0）只是 OPEN，正常等待。
- 汇总页预期：在途 MATCHING 2 张（D-2、B）、RECONCILED 2 张（D-4 未到账部分 0.20、D-1 第 3 版 161.42）；未结异常 8 条；未匹配银行流水有借 25.00、贷 500.00（利息行处置后是否计入以页面为准）。
- 异常账龄按开立时间计算。不加 `--backdate-exceptions` 时，所有异常都在 D0_3。加了以后：D-10 未结算 → 9 天（D8_30），D-4 银行差额 → 5 天、手续费 → 4 天（D4_7），D-2 三条 → 2 天，其余不变。这是直接 UPDATE `t_recon_exception.opened_time/create_time`，只改本记录里的未结异常。

## 记录文件

`~/Code-MoR-backups/recon-demo/demo-<ts>.json`（目录 0700，文件 0600）。内容包括：各对象 ID（接入方是否本次新建、渠道 / 技术账号 / MID、银行账户、清算账户、事实 ID → 平台流水 ID、渠道 / 银行对账单、预期银行流水、人工异常）、每步完成标记（`done`）、Checker 登录名与口令、运行后的汇总。不要提交、不要外发。

## 移除

```bash
python3 -I demo_cleanup.py ~/Code-MoR-backups/recon-demo/demo-<ts>.json --dry-run
python3 -I demo_cleanup.py ~/Code-MoR-backups/recon-demo/demo-<ts>.json
```

做法仿 `../acceptance/stage7_cleanup.py`：

1. 先核对 `mor_reconciliation` 的清算账户、银行账户和平台流水都属于记录。有外来行就中止，什么都不删。
2. 核对通过后清空 15 张表（包括 FAILED 的补偿任务和消费记录）。
3. 按渠道 ID 删除 channel-connector 的演示渠道、技术账号、MID、能力组和变更记录。
4. 如果接入方 `DEMO_NI` 是本次新建的，并且仍然没有 API、脚本或实现，就经接口按版本删除。
5. 按 ID 删除 Platform 的演示复核人和角色。审计事件保留。
6. Vault 记录按先例只计数，不删。
