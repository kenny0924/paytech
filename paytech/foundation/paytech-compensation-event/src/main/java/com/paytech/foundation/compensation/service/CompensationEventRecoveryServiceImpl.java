package com.paytech.foundation.compensation.service;

import java.util.Objects;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.*;
import com.paytech.foundation.compensation.core.error.CompensationErrorCode;
import com.paytech.foundation.compensation.core.execution.CompensationAfterCommitWakeup;
import com.paytech.foundation.compensation.core.execution.CompensationHandlerRegistry;
import com.paytech.foundation.compensation.core.observability.CompensationEventObserver;
import com.paytech.foundation.compensation.core.transaction.CompensationTransactionBoundary;
import com.paytech.foundation.compensation.model.CompensationEventRecord;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.compensation.persistence.repository.CompensationEventRepository;

/**
 * 在事务中完成 FAILED 到 ERROR 的人工恢复，并强制宿主记录审计信息。
 *
 * @author Codex
 */
public class CompensationEventRecoveryServiceImpl implements CompensationEventRecoveryService {

    private final CompensationEventObserver observer;
    private final CompensationEventRepository eventRepository;
    private final CompensationHandlerRegistry handlerRegistry;
    private final CompensationRecoveryAuditRecorder auditRecorder;
    private final CompensationTransactionBoundary transactionBoundary;
    private final CompensationAfterCommitWakeup afterCommitWakeup;

    public CompensationEventRecoveryServiceImpl(CompensationEventObserver observer,
        CompensationEventRepository eventRepository, CompensationHandlerRegistry handlerRegistry,
        CompensationTransactionBoundary transactionBoundary, CompensationAfterCommitWakeup afterCommitWakeup,
        CompensationRecoveryAuditRecorder auditRecorder) {
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry must not be null");
        this.transactionBoundary = Objects.requireNonNull(transactionBoundary, "transactionBoundary must not be null");
        this.afterCommitWakeup = Objects.requireNonNull(afterCommitWakeup, "afterCommitWakeup must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
    }

    @Override
    public ReopenedCompensationEvent reopen(CompensationEventReopenRequest request) {
        return transactionBoundary.executeRequired(() -> reopenInTransaction(request));
    }

    private ReopenedCompensationEvent reopenInTransaction(CompensationEventReopenRequest request) {
        CompensationEventReopenRequest requiredRequest = Objects.requireNonNull(request, "request must not be null");
        CompensationEventRecord failedEvent = requireRecoverableEvent(requiredRequest);
        handlerRegistry.requireHandler(failedEvent.eventType());
        long newLockVersion = Math.addExact(failedEvent.lockVersion(), 1L);
        if (!eventRepository.reopenFailed(failedEvent.id(), requiredRequest.expectedLockVersion())) {
            throw recoveryRejected("补偿事件状态已变化，无法人工重开: eventId=" + requiredRequest.eventId());
        }
        auditRecorder.record(audit(requiredRequest, failedEvent, newLockVersion));
        afterCommitWakeup.registerWakeupAfterCommit(failedEvent.id(),
            () -> observer.manualReopened(handlerRegistry.controlledEventType(failedEvent.eventType())));
        return new ReopenedCompensationEvent(failedEvent.id(), newLockVersion);
    }

    private CompensationEventRecord requireRecoverableEvent(CompensationEventReopenRequest request) {
        CompensationEventRecord event = eventRepository.findForRecovery(request.eventId());
        if (event == null) {
            throw recoveryRejected("补偿事件不存在，无法人工重开: eventId=" + request.eventId());
        }
        if (event.executeStatus() != CompensationEventStatus.FAILED) {
            throw recoveryRejected("补偿事件不是 FAILED 状态，无法人工重开: eventId=" + request.eventId());
        }
        if (event.lockVersion() != request.expectedLockVersion()) {
            throw recoveryRejected("补偿事件版本已变化，无法人工重开: eventId=" + request.eventId());
        }
        return event;
    }

    private FailException recoveryRejected(String message) {
        return new FailException(CompensationErrorCode.RECOVERY_REJECTED, message);
    }

    private CompensationRecoveryAudit audit(CompensationEventReopenRequest request, CompensationEventRecord event,
        long newLockVersion) {
        return new CompensationRecoveryAudit(event.id(), event.eventKey(), event.eventType(), event.subEventType(),
            request.actor(), request.reason(), request.ticket(), event.executeCount(), event.lockVersion(),
            newLockVersion, event.lastErrorCode(), event.lastErrorSummary());
    }
}
