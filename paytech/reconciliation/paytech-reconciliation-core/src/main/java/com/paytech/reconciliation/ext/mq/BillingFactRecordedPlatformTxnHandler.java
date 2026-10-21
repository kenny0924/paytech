package com.paytech.reconciliation.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.reconciliation.domain.PlatformTxnFact;
import com.paytech.reconciliation.domainservice.service.PlatformTxnService;

import lombok.AllArgsConstructor;

/**
 * 平台流水登记入口：消费 billing 的 BillingFactRecorded（billing.events → reconciliation.billing.hash → reconciliation.billing.p0..p3）
 * <li>paytech-messaging 先按 eventId 跳过重放，再在本地事务内调用本 Handler（SHARED）；平台流水按 billing_fact_id 再做业务幂等，平台流水、
 * 等待行的匹配任务与消费登记同一事务提交（technique §2.4）。</li>
 * <li>只处理已批准的 PAYMENT（应收）与 REFUND（应付），其他类型或状态直接确认。</li>
 * <li>拒收进 DLQ：信封非法、更正事实（D-07）、经营主体 / 币种与清算账户不符、外部交易号与方向已被另一事实登记。</li>
 * <li>可重试（退避重投，到投递上限进 DLQ 告警，不静默丢弃，AC-04）：缺 CLEARING_ACCOUNT / CHANNEL_TRANSACTION_REF 引用，清算账户不存在或
 * 已停用。</li>
 * <li>{@code strictOrder = false}：不同事实之间无顺序依赖。</li>
 */
@Component
@AllArgsConstructor
@DomainEventHandler(eventType = BillingFactPayload.EVENT_TYPE, strictOrder = false)
public class BillingFactRecordedPlatformTxnHandler implements DomainEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(BillingFactRecordedPlatformTxnHandler.class);

    private final PlatformTxnService platformTxnService;

    /** 日志只记录事件 ID 与处理结果，不记录 Payload。 */
    @Override
    public void consume(DomainEvent event) {
        try {
            PlatformTxnFact fact = PlatformTxnFactReader.read(event);
            if (fact == null) {
                LOG.debug("BillingFactRecorded {} ignored by reconciliation", event.eventId());
                return;
            }
            platformTxnService.record(fact);
            LOG.info("BillingFactRecorded {} recorded as platform transaction", event.eventId());
        } catch (IllegalArgumentException | BizCheckFailException rejected) {
            LOG.warn("BillingFactRecorded {} rejected to dead letter: {}", event.eventId(), rejected.getMessage());
            throw new DomainEventRejectedException("计费事实无法登记为平台流水: " + event.eventId(), rejected);
        }
    }
}
