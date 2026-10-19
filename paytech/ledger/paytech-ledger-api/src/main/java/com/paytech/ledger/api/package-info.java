/**
 * 会计服务（ledger-service）对外 HTTP 契约：Finance Entity、财务日历与期间、账簿与控制科目账户、规则集、人工调整、试算与关账，以及 account 过账流追平。
 * <li>自动记账指令类型与规则行金额来源代码在本模块 {@code enums}（{@code AutoInstructionType}、{@code AmountSource}）。</li>
 * <li>计费事实合同在 {@code paytech-billing-api}；渠道成本合同在 {@code paytech-channel-connector-api}；Client / Partner 余额与明细账合同在
 * {@code paytech-account-api}。</li>
 */
package com.paytech.ledger.api;
