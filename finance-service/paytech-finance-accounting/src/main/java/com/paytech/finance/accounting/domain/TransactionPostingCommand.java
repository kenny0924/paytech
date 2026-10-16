package com.paytech.finance.accounting.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 交易自动记账命令：一条已存在的 PAYMENT 交易经济结果与 Payment 已冻结的 MID。 */
@Getter
@AllArgsConstructor
public class TransactionPostingCommand {
    /** 本库交易经济结果主键。 */
    private final long transactionEconomicsId;

    /** Payment 为成功 Attempt 冻结的渠道商户号，用于命中 Finance 渠道清算账户。 */
    private final long midId;
}
