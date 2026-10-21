package com.paytech.reconciliation.domainservice.service;

import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.PlatformTxnFact;

/**
 * 平台流水登记（UC-R05）：消费 BillingFactRecorded 时在消费事务内调用
 */
public interface PlatformTxnService {

    /**
     * 按 billing 事实登记平台流水，按 billing_fact_id 幂等
     * <li>清算账户不存在或已停用：抛 {@code ErrorException}（可重试，消息重投到上限后进 DLQ），不丢弃（AC-04）。</li>
     * <li>经营主体或币种与清算账户不一致、同一清算账户 + 外部交易号 + 方向已由另一事实登记：抛 {@code BizCheckFailException}（拒收进 DLQ）。</li>
     * <li>新登记后，为同清算账户中同交易号仍在 MATCHING 且有待匹配行的账单登记 CHANNEL_STATEMENT_MATCH（来源 PLATFORM_TXN），让等待中的行
     * 重试（technique D-10）。</li>
     *
     * @param fact 派生的平台流水事实
     * @return 新登记或已存在的平台流水
     */
    PlatformTxn record(PlatformTxnFact fact);
}
