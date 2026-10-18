package com.paytech.card.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.card.domainservice.repository.PaymentCardInstrumentRepository;
import com.paytech.card.service.processor.request.PurgeOrphanCardInstrumentsRequest;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 回收孤立卡交易工具（design/05 §2.2 第 3 点）：Payment 两段创建的第二段（保存支付工具 Token）失败时，Card 侧留下未被引用的工具。
 *
 * <p>
 * 孤立的判定只用 Card 自己的数据：过期超过宽限期、从未签发读卡票据（从未进入渠道执行）且不是任何已存卡的来源。逐条条件删除，并发或条件变化时跳过。
 * 卡与卡凭证是可复用的事实，不删除；CVV 已随工具期限过期，DEK 仍按现状作为卡的一部分保留。由 XXL-Job 定时触发。
 */
@Component
@AllArgsConstructor
public class PurgeOrphanCardInstrumentsProcessor extends AbstractProcessor<PurgeOrphanCardInstrumentsRequest, Integer> {
    private final Clock clock;
    private final PaymentCardInstrumentRepository paymentCardInstrumentRepository;

    /** 宽限期不得为负。 */
    @Override
    protected void validate(PurgeOrphanCardInstrumentsRequest request) {
        if (request.getGrace().isNegative()) {
            throw new InvalidParameterException("宽限期不得为负");
        }
    }

    /**
     * 回收一批
     *
     * @return 本批实际删除条数
     */
    @Override
    protected Integer processRequest(PurgeOrphanCardInstrumentsRequest request) {
        Instant expiredBefore = clock.instant().truncatedTo(ChronoUnit.MICROS).minus(request.getGrace());
        List<Long> candidates = paymentCardInstrumentRepository.listOrphanIds(expiredBefore, request.getBatchSize());
        int purged = 0;
        for (Long paymentCardInstrumentId : candidates) {
            if (paymentCardInstrumentRepository.deleteOrphan(paymentCardInstrumentId, expiredBefore)) {
                purged++;
            }
        }
        return purged;
    }
}
