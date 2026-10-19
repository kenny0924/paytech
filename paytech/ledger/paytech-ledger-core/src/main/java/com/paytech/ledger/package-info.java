/**
 * 会计服务业务实现：过账内核、自动记账规则、人工调整、试算、关账、期间、账簿、科目、Finance Entity 与科目账户身份。
 * <li>第 11b 步拆表（design/04 §3.1）：{@code t_ledger_account} 只保存科目账户身份，余额一律由本库 Journal 聚合；Owner 余额桶的实时权威是
 * account-service。</li>
 * <li>控制科目过账消费 account 的 AccountPosted（按过账流严格有序），事务外经 billing-api 读经济事实；不读 billing、account 的表。</li>
 * <li>关账步骤 3 CHECK_ACCOUNT_POSTING_PROGRESS 在事务外经 billing-api 与 account-api 做 billing → account、account → ledger 两段进度检查，
 * 任一不可用或未追平即失败关闭。</li>
 */
package com.paytech.ledger;
