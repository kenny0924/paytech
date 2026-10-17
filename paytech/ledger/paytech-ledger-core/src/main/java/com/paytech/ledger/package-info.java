/**
 * 会计服务业务实现：过账内核、自动记账规则、人工调整、试算、关账、期间、账簿、科目、Finance Entity 与科目账户身份。
 * <li>本期 {@code t_account} 仍同时承载科目账户身份与余额投影，account-service 只读共库（第 11b 步拆表）。</li>
 * <li>交易自动记账以 billing 经济事实 DTO 为输入，不读 billing 与 reconciliation 的表。</li>
 */
package com.paytech.ledger;
