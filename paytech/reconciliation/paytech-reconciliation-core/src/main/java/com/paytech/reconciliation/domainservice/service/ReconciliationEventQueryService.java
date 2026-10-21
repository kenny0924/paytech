package com.paytech.reconciliation.domainservice.service;

import com.paytech.reconciliation.api.event.ChannelSettlementConfirmedPayload;
import com.paytech.reconciliation.api.event.ReconAdjustmentApprovedPayload;
import com.paytech.reconciliation.api.event.ActualChannelCostConfirmedPayload;
import com.paytech.reconciliation.api.event.ChannelBatchReconciledPayload;

/**
 * 发布型补偿 Handler 从本库权威事实重建事件载荷（补偿记录不保存业务内容）
 * <li>只读，不开启事务；载荷金额已按币种精度补齐。</li>
 */
public interface ReconciliationEventQueryService {

    /**
     * 重建 ChannelBatchReconciled 载荷
     *
     * @param statementId 渠道账单主键
     * @return 载荷；账单不存在或尚未对平时为 null
     */
    ChannelBatchReconciledPayload channelBatchReconciled(long statementId);

    /**
     * 重建 ActualChannelCostConfirmed 载荷：确认账单取覆盖该平台流水、最后对平的一张账单
     *
     * @param platformTxnId 平台流水主键
     * @return 载荷；平台流水不存在、未 SETTLED 或实际费用等于预估时为 null
     */
    ActualChannelCostConfirmedPayload actualChannelCostConfirmed(long platformTxnId);

    /**
     * 重建 ChannelSettlementConfirmed 载荷
     *
     * @param bankMatchId 银行匹配主键
     * @return 载荷；匹配不存在、未确认或预期流水不是 CHANNEL_SETTLEMENT 时为 null
     */
    ChannelSettlementConfirmedPayload channelSettlementConfirmed(long bankMatchId);

    /**
     * 重建 ReconAdjustmentApproved 载荷
     *
     * @param resolutionId 处置主键
     * @return 载荷；处置不存在、未应用、不是 CLASSIFY 或来源对象不发本事件时为 null
     */
    ReconAdjustmentApprovedPayload reconAdjustmentApproved(long resolutionId);
}
