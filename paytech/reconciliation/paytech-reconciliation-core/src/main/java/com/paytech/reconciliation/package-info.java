/**
 * 对账服务（reconciliation-service）业务实现：mor_reconciliation 的 12 张业务表（清算账户与 MID、平台流水、渠道账单与行、银行账户、
 * 银行对账单与流水、预期银行流水、银行匹配、对账异常与处置），以及本库补偿事件与消费幂等表（2026-10-20 对账切片 1）。
 * <li>对账只认定事实，不记账：四类对账事件经本地补偿任务发布给 billing；账面余额不在本服务。</li>
 * <li>channel-connector（MID）与 Vault（银行账号 Protect）只在本地事务之外调用。</li>
 */
package com.paytech.reconciliation;
