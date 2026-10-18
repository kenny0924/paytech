package com.paytech.foundation.compensation.core.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationExecutionSequencer;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.core.error.CompensationErrorCode;
import com.paytech.foundation.compensation.core.observability.CompensationEventObserver;
import com.paytech.foundation.compensation.model.ClaimedCompensationEvent;
import com.paytech.foundation.compensation.persistence.repository.CompensationEventRepository;

/**
 * 使用数据库 Claim/Lease、版本围栏和隔离 Handler 执行的有界协调器。
 * <li>Claim 成功后的执行交给 {@link CompensationExecutionSequencer}；默认 {@link CompensationExecutionSequencer#DIRECT}
 * 在协调线程直接执行（原有并发）。编排器拒绝时按可重试失败释放任务。</li>
 *
 * @author Codex
 */
public final class CompensationEventDispatcher implements CompensationEventWakeup {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationEventDispatcher.class);
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");
    private static final String UNEXPECTED_ERROR_CODE = "UNEXPECTED_HANDLER_EXCEPTION";
    private static final String RETRYABLE_EXCEPTION_CODE = "RETRYABLE_HANDLER_EXCEPTION";
    private static final String NON_RETRYABLE_EXCEPTION_CODE = "NON_RETRYABLE_HANDLER_EXCEPTION";
    private static final String TIMEOUT_ERROR_CODE = "HANDLER_TIMEOUT";
    private static final String HANDLER_EXECUTOR_ERROR_CODE = "HANDLER_EXECUTOR_UNAVAILABLE";
    private static final String SEQUENCER_ERROR_CODE = "SEQUENCER_UNAVAILABLE";
    private static final String MAXIMUM_ATTEMPTS_ERROR_CODE = "MAXIMUM_ATTEMPTS_EXCEEDED";

    private final ExecutorService handlerExecutor;
    private final SafeErrorSummary safeErrorSummary;
    private final ThreadPoolExecutor coordinatorExecutor;
    private final RetryDelayCalculator retryDelayCalculator;
    private final CompensationEventObserver observer;
    private final CompensationEventRepository eventRepository;
    private final CompensationHandlerRegistry handlerRegistry;
    private final Semaphore handlerCapacity;
    private final String instanceId;
    private final Duration leaseDuration;
    private final CompensationExecutionSequencer sequencer;

    public CompensationEventDispatcher(ExecutorService handlerExecutor, SafeErrorSummary safeErrorSummary,
        ThreadPoolExecutor coordinatorExecutor, RetryDelayCalculator retryDelayCalculator,
        CompensationEventObserver observer, CompensationEventRepository eventRepository,
        CompensationHandlerRegistry handlerRegistry, int maximumHandlerConcurrency, String instanceId,
        Duration leaseDuration) {
        this(handlerExecutor, safeErrorSummary, coordinatorExecutor, retryDelayCalculator, observer, eventRepository,
            handlerRegistry, maximumHandlerConcurrency, instanceId, leaseDuration,
            CompensationExecutionSequencer.DIRECT);
    }

    public CompensationEventDispatcher(ExecutorService handlerExecutor, SafeErrorSummary safeErrorSummary,
        ThreadPoolExecutor coordinatorExecutor, RetryDelayCalculator retryDelayCalculator,
        CompensationEventObserver observer, CompensationEventRepository eventRepository,
        CompensationHandlerRegistry handlerRegistry, int maximumHandlerConcurrency, String instanceId,
        Duration leaseDuration, CompensationExecutionSequencer sequencer) {
        this.handlerExecutor = Objects.requireNonNull(handlerExecutor, "handlerExecutor must not be null");
        this.safeErrorSummary = Objects.requireNonNull(safeErrorSummary, "safeErrorSummary must not be null");
        this.coordinatorExecutor = Objects.requireNonNull(coordinatorExecutor, "coordinatorExecutor must not be null");
        this.retryDelayCalculator =
            Objects.requireNonNull(retryDelayCalculator, "retryDelayCalculator must not be null");
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry must not be null");
        if (maximumHandlerConcurrency <= 0) {
            throw new IllegalArgumentException("maximumHandlerConcurrency must be positive");
        }
        this.handlerCapacity = new Semaphore(maximumHandlerConcurrency);
        this.instanceId = requireInstanceId(instanceId);
        this.leaseDuration = requirePositiveLease(leaseDuration);
        this.sequencer = Objects.requireNonNull(sequencer, "sequencer must not be null");
    }

    @Override
    public void wakeUp(long eventId) {
        try {
            coordinatorExecutor.execute(() -> claimAndExecute(eventId));
        } catch (RejectedExecutionException exception) {
            observeExecutorRejected();
            throw exception;
        }
    }

    public int queuedTaskCount() {
        return coordinatorExecutor.getQueue().size();
    }

    public int activeTaskCount() {
        return coordinatorExecutor.getActiveCount();
    }

    private void claimAndExecute(long eventId) {
        if (!handlerCapacity.tryAcquire()) {
            observeHandlerCapacityRejected();
            return;
        }
        PermitLease permitLease = new PermitLease(handlerCapacity);
        try {
            Optional<ClaimedCompensationEvent> claimedEvent =
                eventRepository.claim(eventId, createAttemptOwner(), leaseDuration);
            if (claimedEvent.isEmpty()) {
                permitLease.close();
                return;
            }
            observeClaim(claimedEvent.get());
            sequenceClaimed(claimedEvent.get(), permitLease);
        } catch (RuntimeException exception) {
            permitLease.closeIfCoordinatorOwned();
            LOGGER.warn("Compensation coordinator failed safely; Lease recovery remains available, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }

    private void sequenceClaimed(ClaimedCompensationEvent claimedEvent, PermitLease permitLease) {
        try {
            sequencer.execute(claimedEvent.eventType(), claimedEvent.eventKey(),
                () -> executeClaimedSafely(claimedEvent, permitLease));
        } catch (RejectedExecutionException exception) {
            permitLease.close();
            finishSequencerRejected(claimedEvent);
        }
    }

    private void executeClaimedSafely(ClaimedCompensationEvent claimedEvent, PermitLease permitLease) {
        try {
            executeClaimed(claimedEvent, permitLease);
        } catch (RuntimeException exception) {
            permitLease.closeIfCoordinatorOwned();
            LOGGER.warn("Compensation coordinator failed safely; Lease recovery remains available, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }

    private void finishSequencerRejected(ClaimedCompensationEvent claimedEvent) {
        CompensationExecutionPolicy policy;
        try {
            policy = handlerRegistry.policy(claimedEvent.eventType());
        } catch (FailException exception) {
            finishFailException(claimedEvent, exception, Duration.ZERO);
            return;
        }
        finishRetryable(claimedEvent, policy, SEQUENCER_ERROR_CODE,
            "Execution sequencer capacity is temporarily unavailable.", "sequencer_unavailable", Duration.ZERO);
    }

    private void executeClaimed(ClaimedCompensationEvent claimedEvent, PermitLease permitLease) {
        CompensationExecutionPolicy policy;
        try {
            policy = handlerRegistry.policy(claimedEvent.eventType());
        } catch (FailException exception) {
            permitLease.close();
            finishFailException(claimedEvent, exception, Duration.ZERO);
            return;
        }
        if (claimedEvent.executeCount() > policy.maximumAttempts()) {
            permitLease.close();
            finishFailed(claimedEvent, MAXIMUM_ATTEMPTS_ERROR_CODE,
                "Compensation maximum attempts were already exhausted.", "attempts_exhausted", Duration.ZERO);
            return;
        }

        long startedAtNanos = System.nanoTime();
        HandlerAttempt attempt = new HandlerAttempt(() -> invoke(claimedEvent), permitLease);
        try {
            handlerExecutor.execute(attempt);
            permitLease.transferToHandler();
        } catch (RejectedExecutionException exception) {
            permitLease.close();
            finishRetryable(claimedEvent, policy, HANDLER_EXECUTOR_ERROR_CODE,
                "Handler execution capacity is temporarily unavailable.", "handler_executor_unavailable",
                elapsed(startedAtNanos));
            return;
        }
        awaitHandler(claimedEvent, policy, attempt, startedAtNanos);
    }

    private void awaitHandler(ClaimedCompensationEvent claimedEvent, CompensationExecutionPolicy policy,
        HandlerAttempt attempt, long startedAtNanos) {
        try {
            HandlerInvocation invocation = attempt.await(policy.timeout());
            finishInvocation(claimedEvent, policy, invocation, elapsed(startedAtNanos));
        } catch (TimeoutException exception) {
            attempt.interrupt();
            finishTimeout(claimedEvent, policy, elapsed(startedAtNanos));
        } catch (InterruptedException exception) {
            attempt.interrupt();
            Thread.currentThread().interrupt();
            LOGGER.warn("Compensation coordinator interrupted; Lease recovery remains available");
        } catch (ExecutionException exception) {
            Throwable failure = exception.getCause();
            if (failure instanceof Error error) {
                throw error;
            }
            finishUnexpected(claimedEvent, policy, failure, elapsed(startedAtNanos));
        }
    }

    private CompensationHandlerResult invoke(ClaimedCompensationEvent claimedEvent) {
        return handlerRegistry.requireHandler(claimedEvent.eventType()).handle(claimedEvent.toEvent());
    }

    private void finishInvocation(ClaimedCompensationEvent claimedEvent, CompensationExecutionPolicy policy,
        HandlerInvocation invocation, Duration duration) {
        Throwable invocationFailure = unwrapInvocationFailure(invocation.failure());
        if (invocationFailure instanceof Error error) {
            throw error;
        }
        if (invocationFailure instanceof FailException exception) {
            finishFailException(claimedEvent, exception, duration);
            return;
        }
        if (invocationFailure instanceof ErrorException exception) {
            finishRetryable(claimedEvent, policy,
                controlledExceptionCode(exception.getCode(), RETRYABLE_EXCEPTION_CODE),
                safeErrorSummary.unexpected(exception), "error_exception", duration);
            return;
        }
        if (invocationFailure != null) {
            finishUnexpected(claimedEvent, policy, invocationFailure, duration);
            return;
        }
        finishResult(claimedEvent, policy, invocation.result(), duration);
    }

    private void finishFailException(ClaimedCompensationEvent claimedEvent, FailException exception,
        Duration duration) {
        String errorCode = controlledExceptionCode(exception.getCode(), NON_RETRYABLE_EXCEPTION_CODE);
        if (isConfigurationFailure(exception)) {
            finishFailed(claimedEvent, errorCode, "Compensation execution configuration is unavailable.",
                "configuration_missing", duration);
            return;
        }
        finishFailed(claimedEvent, errorCode, "Handler reported a non-retryable failure.", "fail_exception", duration);
    }

    private boolean isConfigurationFailure(FailException exception) {
        return CompensationErrorCode.CONFIGURATION_INVALID.getCode().equals(exception.getCode());
    }

    private Throwable unwrapInvocationFailure(RuntimeException failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private void finishResult(ClaimedCompensationEvent claimedEvent, CompensationExecutionPolicy policy,
        CompensationHandlerResult result, Duration duration) {
        if (result == null) {
            finishFailed(claimedEvent, "INVALID_HANDLER_RESULT", "Handler returned no execution result.",
                "invalid_handler_result", duration);
            return;
        }
        switch (result.outcome()) {
            case SUCCESS -> finishSuccess(claimedEvent, duration);
            case TERMINAL_FAILURE -> finishFailed(claimedEvent, result.errorCode(),
                safeErrorSummary.explicit(result.safeSummary()), "terminal_failure", duration);
            case RETRYABLE_FAILURE -> finishRetryable(claimedEvent, policy, result.errorCode(),
                safeErrorSummary.explicit(result.safeSummary()), "retryable_failure", duration);
        }
    }

    private void finishUnexpected(ClaimedCompensationEvent claimedEvent, CompensationExecutionPolicy policy,
        Throwable exception, Duration duration) {
        finishRetryable(claimedEvent, policy, UNEXPECTED_ERROR_CODE, safeErrorSummary.unexpected(exception),
            "unexpected_exception", duration);
    }

    private void finishTimeout(ClaimedCompensationEvent claimedEvent, CompensationExecutionPolicy policy,
        Duration duration) {
        FailureTransition transition =
            transitionFailure(claimedEvent, policy, TIMEOUT_ERROR_CODE, safeErrorSummary.timeout());
        observeTransition(claimedEvent, transition.transitioned(), "timeout", duration);
        observeFailed(claimedEvent, transition, "attempts_exhausted");
    }

    private void finishSuccess(ClaimedCompensationEvent claimedEvent, Duration duration) {
        boolean completed = eventRepository.completeSuccess(claimedEvent);
        observeTransition(claimedEvent, completed, "success", duration);
    }

    private void finishRetryable(ClaimedCompensationEvent claimedEvent, CompensationExecutionPolicy policy,
        String errorCode, String errorSummary, String result, Duration duration) {
        FailureTransition transition = transitionFailure(claimedEvent, policy, errorCode, errorSummary);
        observeTransition(claimedEvent, transition.transitioned(), result, duration);
        observeFailed(claimedEvent, transition, "attempts_exhausted");
    }

    private FailureTransition transitionFailure(ClaimedCompensationEvent claimedEvent,
        CompensationExecutionPolicy policy, String errorCode, String errorSummary) {
        if (claimedEvent.executeCount() >= policy.maximumAttempts()) {
            return new FailureTransition(eventRepository.markFailed(claimedEvent, errorCode, errorSummary), true);
        }
        Duration retryDelay = retryDelayCalculator.calculate(policy, claimedEvent.executeCount());
        return new FailureTransition(eventRepository.markRetry(claimedEvent, retryDelay, errorCode, errorSummary),
            false);
    }

    private void finishFailed(ClaimedCompensationEvent claimedEvent, String errorCode, String errorSummary,
        String result, Duration duration) {
        boolean transitioned =
            eventRepository.markFailed(claimedEvent, errorCode, safeErrorSummary.explicit(errorSummary));
        observeTransition(claimedEvent, transitioned, result, duration);
        if (transitioned) {
            observeFailed(claimedEvent, new FailureTransition(true, true), result);
        }
    }

    private void observeTransition(ClaimedCompensationEvent claimedEvent, boolean transitioned, String result,
        Duration duration) {
        String eventType = controlledEventType(claimedEvent);
        try {
            if (transitioned) {
                observer.attemptFinished(eventType, result, duration);
                return;
            }
            observer.staleTransition(eventType, result);
        } catch (RuntimeException exception) {
            LOGGER.warn("Compensation observation failed without changing execution state, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }

    private void observeClaim(ClaimedCompensationEvent claimedEvent) {
        try {
            observer.claimFinished(controlledEventType(claimedEvent), claimedEvent.leaseTakeover());
        } catch (RuntimeException exception) {
            LOGGER.warn("Compensation Claim observation failed, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }

    private void observeFailed(ClaimedCompensationEvent claimedEvent, FailureTransition transition, String reason) {
        if (!transition.transitioned() || !transition.failed()) {
            return;
        }
        try {
            observer.eventFailed(controlledEventType(claimedEvent), reason);
        } catch (RuntimeException exception) {
            LOGGER.warn("Compensation FAILED observation failed, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }

    private String controlledEventType(ClaimedCompensationEvent claimedEvent) {
        return handlerRegistry.controlledEventType(claimedEvent.eventType());
    }

    private String controlledExceptionCode(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        String requiredCode = code.trim();
        return ERROR_CODE.matcher(requiredCode).matches() ? requiredCode : fallback;
    }

    private void observeExecutorRejected() {
        try {
            observer.executorRejected();
        } catch (RuntimeException exception) {
            LOGGER.warn("Compensation rejection observation failed, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }

    private void observeHandlerCapacityRejected() {
        try {
            observer.handlerCapacityRejected();
        } catch (RuntimeException exception) {
            LOGGER.warn("Compensation Handler capacity observation failed, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }

    private String createAttemptOwner() {
        String uniqueSuffix = UUID.randomUUID().toString();
        int maximumPrefixLength = 64 - uniqueSuffix.length() - 1;
        String instancePrefix = instanceId.substring(0, Math.min(instanceId.length(), maximumPrefixLength));
        return instancePrefix + ":" + uniqueSuffix;
    }

    private Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAtNanos));
    }

    private String requireInstanceId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("instanceId must not be blank");
        }
        String requiredValue = value.trim();
        if (requiredValue.length() > 64) {
            throw new IllegalArgumentException("instanceId exceeds 64 characters");
        }
        return requiredValue;
    }

    private Duration requirePositiveLease(Duration value) {
        Duration requiredValue = Objects.requireNonNull(value, "leaseDuration must not be null");
        if (requiredValue.isZero() || requiredValue.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        return requiredValue;
    }

    private record FailureTransition(boolean transitioned, boolean failed) {
    }

    private record HandlerInvocation(CompensationHandlerResult result, RuntimeException failure) {
    }

    private static final class HandlerAttempt implements Runnable {

        private final Supplier<CompensationHandlerResult> invocation;
        private final PermitLease permitLease;
        private final CompletableFuture<HandlerInvocation> completion = new CompletableFuture<>();
        private final AtomicReference<Thread> handlerThread = new AtomicReference<>();
        private final AtomicBoolean interruptRequested = new AtomicBoolean();

        private HandlerAttempt(Supplier<CompensationHandlerResult> invocation, PermitLease permitLease) {
            this.invocation = invocation;
            this.permitLease = permitLease;
        }

        @Override
        public void run() {
            Thread currentThread = Thread.currentThread();
            handlerThread.set(currentThread);
            try {
                if (interruptRequested.get()) {
                    currentThread.interrupt();
                    return;
                }
                completion.complete(new HandlerInvocation(invocation.get(), null));
            } catch (RuntimeException exception) {
                completion.complete(new HandlerInvocation(null, exception));
            } catch (Error error) {
                completion.completeExceptionally(error);
                throw error;
            } finally {
                handlerThread.set(null);
                permitLease.close();
            }
        }

        HandlerInvocation await(Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
            long timeoutNanos;
            try {
                timeoutNanos = timeout.toNanos();
            } catch (ArithmeticException exception) {
                timeoutNanos = Long.MAX_VALUE;
            }
            return completion.get(timeoutNanos, TimeUnit.NANOSECONDS);
        }

        void interrupt() {
            interruptRequested.set(true);
            Thread currentThread = handlerThread.get();
            if (currentThread != null) {
                currentThread.interrupt();
            }
        }
    }

    private static final class PermitLease implements AutoCloseable {

        private final Semaphore capacity;
        private final AtomicBoolean released = new AtomicBoolean();
        private final AtomicBoolean handlerOwned = new AtomicBoolean();

        private PermitLease(Semaphore capacity) {
            this.capacity = capacity;
        }

        void transferToHandler() {
            handlerOwned.set(true);
        }

        void closeIfCoordinatorOwned() {
            if (!handlerOwned.get()) {
                close();
            }
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                capacity.release();
            }
        }
    }
}
