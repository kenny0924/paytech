package com.paytech.foundation.compensation.service;

import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.RegisteredCompensationEvent;
import com.paytech.foundation.compensation.core.error.CompensationErrorCode;
import com.paytech.foundation.compensation.core.execution.CompensationAfterCommitWakeup;
import com.paytech.foundation.compensation.core.execution.CompensationHandlerRegistry;
import com.paytech.foundation.compensation.core.observability.CompensationEventObserver;
import com.paytech.foundation.compensation.core.serialization.CompensationPayloadSerializer;
import com.paytech.foundation.compensation.core.transaction.CompensationTransactionBoundary;
import com.paytech.foundation.compensation.model.CompensationEventRecord;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.compensation.persistence.repository.CompensationEventRepository;
import com.paytech.foundation.id.IdGenerator;

/**
 * 使用已持久化来源引用作为幂等身份，在业务事务中登记补偿事件。
 *
 * @author Codex
 */
public class CompensationEventRegistrationService implements CompensationEventRegistrar {

    private final IdGenerator idGenerator;
    private final CompensationEventObserver observer;
    private final CompensationEventRepository eventRepository;
    private final CompensationHandlerRegistry handlerRegistry;
    private final CompensationPayloadSerializer payloadSerializer;
    private final CompensationTransactionBoundary transactionBoundary;
    private final CompensationAfterCommitWakeup afterCommitWakeup;

    public CompensationEventRegistrationService(IdGenerator idGenerator, CompensationEventObserver observer,
        CompensationEventRepository eventRepository, CompensationHandlerRegistry handlerRegistry,
        CompensationPayloadSerializer payloadSerializer, CompensationTransactionBoundary transactionBoundary,
        CompensationAfterCommitWakeup afterCommitWakeup) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry must not be null");
        this.payloadSerializer = Objects.requireNonNull(payloadSerializer, "payloadSerializer must not be null");
        this.transactionBoundary = Objects.requireNonNull(transactionBoundary, "transactionBoundary must not be null");
        this.afterCommitWakeup = Objects.requireNonNull(afterCommitWakeup, "afterCommitWakeup must not be null");
    }

    @Override
    public RegisteredCompensationEvent register(CompensationEventRequest request) {
        return transactionBoundary.executeMandatory("compensation registration", () -> registerInTransaction(request));
    }

    private RegisteredCompensationEvent registerInTransaction(CompensationEventRequest request) {
        CompensationEventRequest requiredRequest = Objects.requireNonNull(request, "request must not be null");
        handlerRegistry.validateRegistration(requiredRequest.eventType());

        CompensationEventRecord existing = eventRepository.findByIdentity(requiredRequest);
        if (existing != null) {
            return reuseExisting(requiredRequest, existing);
        }

        String payloadJson = payloadSerializer.serialize(requiredRequest.payload());
        CompensationEventRecord record = createRecord(requiredRequest, payloadJson);
        try {
            eventRepository.insert(record);
        } catch (DataIntegrityViolationException exception) {
            CompensationEventRecord concurrentExisting = eventRepository.findByIdentityForUpdate(requiredRequest);
            if (concurrentExisting == null) {
                throw exception;
            }
            return reuseExisting(requiredRequest, concurrentExisting);
        }

        registerAfterCommitWakeup(record, false);
        return new RegisteredCompensationEvent(record.id(), false);
    }

    private RegisteredCompensationEvent reuseExisting(CompensationEventRequest request,
        CompensationEventRecord existing) {
        if (!payloadSerializer.matches(existing.payloadJson(), request.payload())) {
            throw new FailException(CompensationErrorCode.EVENT_CONFLICT, "补偿事件的可选 Payload 与现有记录不一致");
        }
        registerAfterCommitWakeup(existing, true);
        return new RegisteredCompensationEvent(existing.id(), true);
    }

    private void registerAfterCommitWakeup(CompensationEventRecord event, boolean duplicate) {
        afterCommitWakeup.registerWakeupAfterCommit(event.id(),
            () -> observer.registrationFinished(event.eventType(), duplicate));
    }

    private CompensationEventRecord createRecord(CompensationEventRequest request, String payloadJson) {
        return new CompensationEventRecord(idGenerator.nextId(), request.eventKey(), request.eventType(),
            request.subEventType(), payloadJson, CompensationEventStatus.INIT, 0, null, null, null, null, null, 1L,
            null, null);
    }
}
