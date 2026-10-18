/**
 * 对账服务（reconciliation-service）业务实现：拥有 Finance 渠道清算账户、渠道与银行对账、现金账户三表（D42）。
 * <li>本期只有 {@code t_channel_account} 的只读代码，其余 16 张表仅 Schema 归属；账面余额不在本服务。</li>
 * <li>原 finance-service 空壳 {@code paytech-finance-treasury} 的对账职责由本服务承接，出款职责归 payout。</li>
 */
package com.paytech.reconciliation;
