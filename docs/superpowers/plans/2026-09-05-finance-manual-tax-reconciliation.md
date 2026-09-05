# Finance Manual Tax Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans or superpowers:subagent-driven-development; use TDD for every state/amount/concurrency change.

**Goal:** 在不丢失真实银行扣款的前提下，闭合计划内 Tax Payment 与无合法前置 Tax Payment 的事后扣款登记、暂记、调查、归属和税负结清。

**Architecture:** 正常流在外部操作前创建 `CREATED` Tax Payment 并占用 Tax Payable/BANK_CASH 容量。银行流水始终先作为不可变 `bank_statement_item` 保存；可合法匹配既有 Payment 时走 `TAX_PAYMENT_DEBIT`，否则从 Bank Item 打开 Recon Exception。只有经受控解决建立完整 Tax Return/Tax Payable/Bank Item/Journal 导航后才结清税负。

**Tech Stack:** Finance PDManer/Manifest/Policy、MySQL 8.4、Java 25、MyBatis、TransactionTemplate、JUnit 5 + `local-environment`。

**Spec:** [`agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md#d5-finance-手工缴税分离事前承诺与事后事实`](../../../agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md#d5-finance-手工缴税分离事前承诺与事后事实)

## Global Constraints

- 真实 Bank Statement Debit 不以 `available_cash` 为导入准入条件。
- 无合法目标时不补造正常 Tax Payment、不创建缺少 `tax_payment_id` 的 `TAX_PAYMENT_DEBIT` Match、不提前结清 Tax Payable。
- `LATE_DEBIT` 只表示既有 FAILED Payment 的迟到扣款；其他情况使用准确现有类型或新增经批准类型。
- 所有 Journal 追加且幂等，任何解决路径不得二次贷记 BANK_CASH。

## Task 1: 先冻结反例

为模型 Policy 和场景测试增加：计划内 Payment 必须先于外部提交；无前置 Payment 的真实 Debit 保留 Bank Item 并开 `UNMATCHED`；错账户/币种/短款/超额准确分类；既有 FAILED Payment 才能走 `LATE_DEBIT`；未解决异常不能关闭 Tax Payable。

## Task 2: 决定事后归属模型

比较并冻结一个最小方案：为 Tax Payment 增加可审计的 `PLANNED/POST_FACT` 来源及受控直达 Recon 路径，或为 Recon Resolution 增加 Tax-specific Return/Payable 归属。方案必须从 Bank Item 导航到 Exception、审批/Resolution、Tax Return、Tax Payable、Accounting Instruction/Journal，并明确一个 Debit 拆分多个 Return 的规则；不得依赖自由 JSON 或多态 ID 猜测。

## Task 3: 补齐会计和状态机

定义普通未匹配 Debit 的即时暂记科目、Instruction Type、稳定幂等键和 Journal 导航；定义调查后重分类、部分/多目标分配、重复导入、撤销更正和关账影响。另行决定预创建但从未提交的 Tax Payment 是否增加可证明未扣款的退出状态，不能借用 FAILED 隐藏无银行证据的取消。

## Task 4: 并发与事务

锁定 Bank Item、Exception/Resolution、Tax Return、Tax Payable 与 BANK_CASH 的稳定顺序；确认 Match 累计不超过银行金额，税负分配不超额，Journal 和状态同事务提交。外部事实入账后由新余额阻断后续承诺。

## Task 5: 同步与验证

同步 Finance 文档、场景、Manifest、Policy、PDManer、手工迁移 SQL 和 Java；先获得 RED 门禁，再运行 Finance 模型测试和真实本地 MySQL 并发/回滚测试。PDManer 4.9.3 人工复核后更新 FIN-01，数据库执行和部署仍单独记录。
