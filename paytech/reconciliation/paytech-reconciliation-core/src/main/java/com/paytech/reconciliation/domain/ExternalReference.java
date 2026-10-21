package com.paytech.reconciliation.domain;

import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;

/**
 * 外部交易号（类型 + 交易号），在同一清算账户内标识平台流水与账单行的对应关系（设计 F1 规则 2）
 * <li>匹配任务按它把账单行与平台流水、跨账单相同行分组，并作为批量查询的键。</li>
 *
 * @param referenceType 外部交易号类型
 * @param externalTransactionId 外部交易号
 */
public record ExternalReference(String referenceType, String externalTransactionId) {

    /**
     * 账单行的外部交易号
     *
     * @param line 账单行
     * @return 不带交易号的行为 null
     */
    public static ExternalReference of(ChannelStatementLine line) {
        return ChannelStatementLines.hasReference(line)
            ? new ExternalReference(line.getReferenceType(), line.getExternalTransactionId()) : null;
    }
}
