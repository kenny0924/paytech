package com.paytech.reconciliation.domainservice.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.domain.ReconResolutionRules;
import com.paytech.reconciliation.domainservice.repository.ReconResolutionRepository;
import com.paytech.reconciliation.domainservice.service.ReconResolutionService;
import com.paytech.reconciliation.service.handler.ReconAdjustmentApprovedHandler;
import lombok.AllArgsConstructor;

/** 异常处置保存；事务传播为 REQUIRED。 */
@Service
@AllArgsConstructor
public class ReconResolutionServiceImpl implements ReconResolutionService {
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final ReconResolutionRepository reconResolutionRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;

    /** 插入处置。 */
    @Override
    public void submit(ReconResolution resolution) {
        transactionTemplate.executeWithoutResult(status -> reconResolutionRepository.insert(resolution));
    }

    /** APPLIED，需要时登记对账调整事件。 */
    @Override
    public void approve(ReconResolution resolution, ReconException exception, Long checkerId,
        String approvedContentHash) {
        transactionTemplate.executeWithoutResult(status -> {
            reconResolutionRepository.review(resolution, ResolutionStatus.APPLIED, checkerId, CheckerDecision.APPROVED,
                approvedContentHash, now());
            if (resolution.getResolutionType() == ResolutionType.CLASSIFY
                && ReconResolutionRules.publishesAdjustment(exception.getSourceType())) {
                compensationEventRegistrar.register(ReconAdjustmentApprovedHandler.buildEvent(resolution.getId()));
            }
        });
    }

    /** REJECTED。 */
    @Override
    public void reject(ReconResolution resolution, Long checkerId, String approvedContentHash) {
        transactionTemplate.executeWithoutResult(status -> reconResolutionRepository.review(resolution,
            ResolutionStatus.REJECTED, checkerId, CheckerDecision.REJECTED, approvedContentHash, now()));
    }

    /** 写入时间：UTC 微秒。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
