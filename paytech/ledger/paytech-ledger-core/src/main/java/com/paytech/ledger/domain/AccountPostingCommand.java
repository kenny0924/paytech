package com.paytech.ledger.domain;

import com.paytech.billing.api.fact.BillingFactPayload;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 控制科目过账命令：一个需要过账的 account 入账批次，以及事务外经 billing-api 读取、版本一致的计费事实
 * <li>Owner 控制科目行来自批次流水；非 Owner 行由规则引擎按计费事实推导。</li>
 * <li>由 AccountPosted 消费 Handler 与过账流追平命令在事务外构造。</li>
 */
@Getter
@AllArgsConstructor
public class AccountPostingCommand {
    /** 已校验且需要过账（APPLIED 事实批次）的入账批次。 */
    private final AccountPostingBatch batch;

    /** 批次引用的计费事实（头与全部明细行，版本与批次一致）。 */
    private final BillingFactPayload fact;
}
