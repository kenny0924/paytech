package com.paytech.ledger.domain;

import com.paytech.billing.api.fact.EconomicFactPayload;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易自动记账命令：billing 发布的一条经济事实（头 + 全部明细行）
 * <li>输入驱动：ledger 不再读取 billing 经济事实表与 reconciliation 渠道清算账户表；渠道清算账户主键由事实头给出。</li>
 * <li>由 EconomicFactRecorded 消费 Handler（{@code EconomicFactRecordedPostingHandler}）从事件 Payload 构造本命令（第 11b 步）。</li>
 */
@Getter
@AllArgsConstructor
public class TransactionPostingCommand {
    /** 经济事实载荷。 */
    private final EconomicFactPayload fact;
}
