package com.paytech.account.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.domain.FactRecord;
import com.paytech.account.domainservice.service.BalanceApplicationService;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;

import lombok.AllArgsConstructor;

/**
 * 明细账入账入口：消费 billing 的 BillingFactRecorded（billing.events → account.billing.hash → account.billing.p0..p3）
 * <li>paytech-messaging 先按 eventId（= fact_id + fact_version 的事件键）跳过重放，再在本地事务内调用本 Handler（{@code SHARED}）；入账以
 * t_balance_applied_fact 的 fact_id + fact_version 再做业务幂等，批次、流水、余额、处理记录、AccountPosted 补偿事件与消费登记同一个 account
 * 本地事务提交。</li>
 * <li>{@code strictOrder = false}：不同事实之间无顺序依赖；同一事实的版本替代由 supersedesFactId 判定，乱序到达也收敛到最新版本。</li>
 * <li>信封非法或映射明确拒绝：抛 {@link DomainEventRejectedException} 直接进入 account.billing.dlq；并发冲突等运行时失败由
 * paytech-messaging 退避重投。</li>
 */
@Component
@AllArgsConstructor
@DomainEventHandler(eventType = BillingFactPayload.EVENT_TYPE, strictOrder = false)
public class BillingFactRecordedBalanceHandler implements DomainEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(BillingFactRecordedBalanceHandler.class);

    private final BalanceApplicationService balanceApplicationService;

    /** 日志只记录事件 ID 与处理状态，不记录 Payload。 */
    @Override
    public void consume(DomainEvent event) {
        try {
            FactRecord fact = BillingFactEventReader.read(event);
            AppliedFactStatus status = balanceApplicationService.apply(fact);
            LOG.info("BillingFactRecorded {} posted to subledger: {}", event.eventId(), status.getCode());
        } catch (IllegalArgumentException | BizCheckFailException rejected) {
            LOG.warn("BillingFactRecorded {} rejected to dead letter: {}", event.eventId(), rejected.getMessage());
            throw new DomainEventRejectedException("计费事实无法入账到明细账: " + event.eventId(), rejected);
        }
    }
}
