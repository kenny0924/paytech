/**
 * 会计服务业务实现：过账内核、自动记账规则、人工调整、试算、关账、期间、账簿、科目、Finance Entity 与科目账户身份。
 * <li>第 11b 步拆表（design/04 §3.1）：{@code t_ledger_account} 只保存科目账户身份，余额一律由本库 Journal 聚合；Owner 余额桶的实时权威是
 * account-service。</li>
 * <li>交易自动记账消费 billing 的 EconomicFactRecorded（design/04 §5），不读 billing 与 reconciliation 的表。</li>
 * <li>关账步骤 2、3 在事务外经 account-api 与 billing-api 检查追平与非零渠道账户，任一不可用即失败关闭（design/04 §6）。</li>
 */
package com.paytech.ledger;
