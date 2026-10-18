package com.paytech.ledger.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.ledger.domain.TransactionPostingCommand;
import com.paytech.ledger.domain.view.PostedInstructionView;
import com.paytech.ledger.domainservice.service.TransactionPostingService;

import lombok.AllArgsConstructor;

/**
 * 交易自动记账入口：消费 billing 的 EconomicFactRecorded（billing.events → ledger.billing.hash → ledger.billing.p0..p3，design/04 §5）
 * <li>paytech-messaging 先按 eventId（= fact_id + fact_version 的事件键）跳过重放，再在本地事务内调用本 Handler（{@code SHARED}）；
 * 过账引擎以 t_accounting_instruction 的 economic_fact_id 再做业务幂等，生成指令、过账与消费登记同一个 ledger 本地事务提交。</li>
 * <li>过账结果不回写 billing；事务内只读写 ledger 本库表。</li>
 * <li>信封非法或过账规则明确拒绝（非 PAYMENT 事实、账簿或期间不满足等）：抛 {@link DomainEventRejectedException} 直接进入
 * ledger.billing.dlq；并发冲突（如另一分区刚创建同一科目账户）与其他运行时失败由 paytech-messaging 退避重投，超过投递上限进入死信队列。</li>
 */
@Component
@AllArgsConstructor
@DomainEventHandler(eventType = EconomicFactPayload.EVENT_TYPE, strictOrder = false)
public class EconomicFactRecordedPostingHandler implements DomainEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(EconomicFactRecordedPostingHandler.class);

    private final TransactionPostingService transactionPostingService;

    /** 日志只记录事件 ID 与指令数，不记录 Payload。 */
    @Override
    public void consume(DomainEvent event) {
        try {
            EconomicFactPayload fact = EconomicFactEventReader.read(event);
            var posted = transactionPostingService.post(new TransactionPostingCommand(fact));
            LOG.info("EconomicFactRecorded {} posted: instructions={}", event.eventId(),
                posted.stream().map(PostedInstructionView::getInstructionId).toList());
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("EconomicFactRecorded {} conflicted and will be redelivered", event.eventId());
            throw conflict;
        } catch (IllegalArgumentException | FailException rejected) {
            LOG.warn("EconomicFactRecorded {} rejected to dead letter: {}", event.eventId(), rejected.getMessage());
            throw new DomainEventRejectedException("经济事实不可过账: " + event.eventId(), rejected);
        }
    }
}
