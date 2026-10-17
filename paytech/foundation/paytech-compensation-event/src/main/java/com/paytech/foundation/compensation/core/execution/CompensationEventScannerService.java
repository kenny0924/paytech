package com.paytech.foundation.compensation.core.execution;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.compensation.api.CompensationScanResult;
import com.paytech.foundation.compensation.core.observability.CompensationEventObserver;
import com.paytech.foundation.compensation.persistence.repository.CompensationEventRepository;

/**
 * 扫描数据库候选记录，并由已接收的工作任务负责 Claim。
 *
 * @author Codex
 */
public final class CompensationEventScannerService implements CompensationEventScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationEventScannerService.class);

    private final CompensationEventObserver observer;
    private final CompensationEventDispatcher dispatcher;
    private final CompensationEventRepository eventRepository;
    private final int maximumBatchSize;

    public CompensationEventScannerService(CompensationEventObserver observer, CompensationEventDispatcher dispatcher,
        CompensationEventRepository eventRepository, int maximumBatchSize) {
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        if (maximumBatchSize <= 0) {
            throw new IllegalArgumentException("maximumBatchSize must be positive");
        }
        this.maximumBatchSize = maximumBatchSize;
    }

    @Override
    public CompensationScanResult scan(CompensationScanRequest request) {
        observeBacklog();
        CompensationScanRequest boundedRequest = boundBatchSize(request);
        List<Long> candidateIds = eventRepository.findCandidateIds(boundedRequest);
        int submittedCount = 0;
        int rejectedCount = 0;
        for (Long candidateId : candidateIds) {
            try {
                dispatcher.wakeUp(candidateId);
                submittedCount++;
            } catch (RejectedExecutionException exception) {
                rejectedCount++;
            }
        }
        return new CompensationScanResult(candidateIds.size(), submittedCount, rejectedCount);
    }

    private void observeBacklog() {
        try {
            observer.backlogObserved(eventRepository.backlogSnapshot());
        } catch (RuntimeException exception) {
            LOGGER.warn("Compensation backlog observation failed without stopping recovery scan, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }

    private CompensationScanRequest boundBatchSize(CompensationScanRequest request) {
        CompensationScanRequest requiredRequest = Objects.requireNonNull(request, "request must not be null");
        if (requiredRequest.batchSize() <= maximumBatchSize) {
            return requiredRequest;
        }
        return new CompensationScanRequest(maximumBatchSize, requiredRequest.shardIndex(), requiredRequest.shardCount(),
            requiredRequest.eventTypes());
    }
}
