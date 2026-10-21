# F：缺陷 D-1 修复报告（匹配任务集合化）

> 2026-10-21，阶段 5 同范围缺陷修复。只改 `paytech/reconciliation/**` 与本需求 verification / tasks；未提交 / 推送 / 部署；未碰 mor-test；未调用 Superpowers。复测结果见 [stage5-report.md §8](../verification/stage5-report.md)。

## 1. 改动

**core（`paytech-reconciliation-core`）**
- `service/processor/MatchChannelStatementProcessor`：新增 `prepareCandidates`（一次批量加载待规划交易行的候选平台流水）、`duplicatedLineIds`（跨账单相同行 → 账单状态 → 重复结算最近异常，全部批量；`DuplicateKey` 以金额 `stripTrailingZeros` 等价原 `compareTo`）、`costDeviations`（批量查最近 COST_DEVIATION）；保存改用 `openAll` / `autoCloseAll`。规则 1–7、FEE 并入、等待、AMBIGUOUS / DUPLICATE / AMOUNT_MISMATCH、成本偏差、同账单多行部分覆盖（内存 `TxnProgress`）均不变。
- `domain/StatementMatchContext`：异常、交易行（按交易号）、同交易号 FEE 合计、计划结果建内存索引；`actualFee`、`transactionLinesOf`、`plannedMatch`、`candidateTxns`、`duplicated`，消除逐行 O(n) 扫描（原 2 万行约 4 亿次比较）。
- `domainservice/service/impl/ChannelStatementMatchServiceImpl`：`saveMatches` / `reconcile` 改为批量条件更新；覆盖金额预先按平台流水汇总。
- `ReconExceptionService(+Impl)`：新增 `openAll`（按对象类型 + 异常类型批量查未结异常，缺的批量插入，active_slot 唯一键兜底）、`autoCloseAll`（重读后只结 OPEN）。
- 仓储（接口 + Impl + Mapper + XML）：`PlatformTxnRepository.listByReferences / updateProgresses`、`ChannelStatementLineRepository.listByReferences / updateMatches`、`ReconExceptionRepository.insertAll / listActiveBySources / listLatestBySources / changeStatusAll`；`findByIds`（平台流水、账单）内部分段并按主键排序。多行更新用 `UPDATE … JOIN (VALUES ROW(…)) … ON id AND version`，命中行数 ≠ 段大小即抛并发冲突。
- 新增：`domain/ExternalReference`、`domain/ChannelLineMatchChange`、`domain/PlatformTxnProgressChange`、`domainservice/repository/impl/RepositoryBatches`（每段 1,000）。

**test**：`PersistenceMappingTest.test_matchBatchReadsAndWrites`；新 `auto/ext/compensation/ChannelStatementBulkMatchTest`（2 例）。

**verification**：`stack.sh` 增 `start-recon`；新 `perf_direct.py`（直连 reconciliation 的 vhost / 账户准备 / N 行计时 / 清理）。

## 2. 查询次数（2 万行全部匹配并对平）

| | 修复前 | 修复后 |
|---|---|---|
| 规划读 | 每行 2–3 次（平台流水、跨账单行、账单），约 4–6 万 | 行 1、异常 1、候选 20、跨账单行 20（+ 账单 / 最近异常按需分段） |
| 写 | 每行 1 次 + 每笔平台流水 2 次，约 6 万 | 行 20、进度 20、结清 20 段 + 账单状态 / 事件登记 |
| 合计 | 约 10 万 | 约 130 |

## 3. 验证

- `./mvnw -B -o -pl reconciliation/paytech-reconciliation-api,…-core,…-app install -DskipTests`：BUILD SUCCESS。
- `./mvnw -B -o -pl reconciliation/paytech-reconciliation-test verify`：**93/93 通过**（含 ArchitectureAutoTest、ModuleBoundaryTest）。
- 性能（私有 reconciliation，mor-test 同档 JVM 参数）：1,000 行 **0.67 s**（原 4.3 s）；20,000 行 **4.86 s**（原 ≈100.8 s 超时），一次尝试成功、账单 RECONCILED、2 万笔平台流水 SETTLED。
- 清理：`mor_reconciliation_v2e2e` 15 张表回 0；临时 vhost 删除；进程已停。

## 4. 说明 / 遗留

- 100 s 超时与 Lease 未改；D-2 仍待 Foundation 评估。
- `ActualChannelCostConfirmed` 登记仍逐笔（只对费用有差额的平台流水，一般很少）；`ReconcileGate`、预期银行流水生成本身是 O(n) / 单次，未动。
- 复测直连 reconciliation，未经 Gateway / BFF（入口层在第 5 节已测，耗时 < 1 s，与匹配无关）。
