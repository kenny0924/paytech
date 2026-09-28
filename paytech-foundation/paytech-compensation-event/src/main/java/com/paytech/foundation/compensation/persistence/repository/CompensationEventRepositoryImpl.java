package com.paytech.foundation.compensation.persistence.repository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.compensation.core.observability.CompensationBacklogSnapshot;
import com.paytech.foundation.compensation.model.ClaimedCompensationEvent;
import com.paytech.foundation.compensation.model.CompensationEventRecord;
import com.paytech.foundation.compensation.persistence.mapper.CompensationBacklogRecord;
import com.paytech.foundation.compensation.persistence.mapper.CompensationEventMapper;

/**
 * 基于 MyBatis 的补偿事件仓储实现。
 *
 * @author Codex
 */
public final class CompensationEventRepositoryImpl implements CompensationEventRepository {

    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");

    private final CompensationEventMapper mapper;
    private final TransactionTemplate attemptTransaction;

    public CompensationEventRepositoryImpl(CompensationEventMapper mapper,
        PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.attemptTransaction =
            new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.attemptTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void insert(CompensationEventRecord record) {
        int affectedRows = mapper.insert(record);
        if (affectedRows != 1) {
            throw new IllegalStateException("compensation event insert affected rows: " + affectedRows);
        }
    }

    @Override
    public CompensationEventRecord findByIdentity(CompensationEventRequest request) {
        CompensationEventRequest requiredRequest = Objects.requireNonNull(request, "request must not be null");
        return mapper.selectByIdentity(requiredRequest.eventKey(), requiredRequest.eventType(),
            requiredRequest.subEventType());
    }

    @Override
    public CompensationEventRecord findByIdentityForUpdate(CompensationEventRequest request) {
        CompensationEventRequest requiredRequest = Objects.requireNonNull(request, "request must not be null");
        return mapper.selectByIdentityForUpdate(requiredRequest.eventKey(), requiredRequest.eventType(),
            requiredRequest.subEventType());
    }

    @Override
    public CompensationEventRecord findForRecovery(long eventId) {
        if (eventId <= 0L) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        return mapper.selectByIdForRecovery(eventId);
    }

    @Override
    public Optional<ClaimedCompensationEvent> claim(long eventId, String lockOwner, Duration leaseDuration) {
        String requiredOwner = requireLockOwner(lockOwner);
        long leaseMicros = requireLeaseMicros(leaseDuration);
        Optional<ClaimedCompensationEvent> result = attemptTransaction.execute(status -> {
            boolean leaseTakeover = false;
            int affectedRows = mapper.claimReady(eventId, requiredOwner, leaseMicros);
            if (affectedRows == 0) {
                affectedRows = mapper.claimExpiredLease(eventId, requiredOwner, leaseMicros);
                leaseTakeover = affectedRows == 1;
            }
            if (affectedRows != 1) {
                return Optional.empty();
            }
            CompensationEventRecord claimedRecord = mapper.selectClaimed(eventId, requiredOwner);
            if (claimedRecord == null) {
                status.setRollbackOnly();
                return Optional.empty();
            }
            return Optional.of(ClaimedCompensationEvent.from(claimedRecord, leaseTakeover));
        });
        return result == null ? Optional.empty() : result;
    }

    @Override
    public boolean completeSuccess(ClaimedCompensationEvent claimedEvent) {
        ClaimedCompensationEvent requiredEvent = Objects.requireNonNull(claimedEvent, "claimedEvent must not be null");
        Boolean completed = attemptTransaction.execute(status -> mapper.deleteClaimed(requiredEvent.id(),
            requiredEvent.lockOwner(), requiredEvent.lockVersion()) == 1);
        return Boolean.TRUE.equals(completed);
    }

    @Override
    public List<Long> findCandidateIds(CompensationScanRequest request) {
        CompensationScanRequest requiredRequest = Objects.requireNonNull(request, "request must not be null");
        return List.copyOf(mapper.selectCandidateIds(requiredRequest.batchSize(), requiredRequest.shardIndex(),
            requiredRequest.shardCount(), requiredRequest.eventTypes()));
    }

    @Override
    public CompensationBacklogSnapshot backlogSnapshot() {
        CompensationBacklogRecord record = mapper.selectBacklog();
        if (record == null) {
            return new CompensationBacklogSnapshot(0L, Duration.ZERO, 0L);
        }
        return new CompensationBacklogSnapshot(record.pendingRows(),
            Duration.of(record.oldestPendingMicros(), ChronoUnit.MICROS), record.expiredLeaseRows());
    }

    @Override
    public boolean markRetry(ClaimedCompensationEvent claimedEvent, Duration retryDelay, String errorCode,
        String errorSummary) {
        ClaimedCompensationEvent requiredEvent = requireClaimedEvent(claimedEvent);
        long delayMicros = requireDelayMicros(retryDelay);
        String requiredErrorCode = requireErrorCode(errorCode);
        String requiredErrorSummary = requireErrorSummary(errorSummary);
        Boolean updated =
            attemptTransaction.execute(status -> mapper.markRetry(requiredEvent.id(), requiredEvent.lockOwner(),
                requiredEvent.lockVersion(), delayMicros, requiredErrorCode, requiredErrorSummary) == 1);
        return Boolean.TRUE.equals(updated);
    }

    @Override
    public boolean markFailed(ClaimedCompensationEvent claimedEvent, String errorCode, String errorSummary) {
        ClaimedCompensationEvent requiredEvent = requireClaimedEvent(claimedEvent);
        String requiredErrorCode = requireErrorCode(errorCode);
        String requiredErrorSummary = requireErrorSummary(errorSummary);
        Boolean updated = attemptTransaction.execute(status -> mapper.markFailed(requiredEvent.id(),
            requiredEvent.lockOwner(), requiredEvent.lockVersion(), requiredErrorCode, requiredErrorSummary) == 1);
        return Boolean.TRUE.equals(updated);
    }

    @Override
    public boolean reopenFailed(long eventId, long expectedLockVersion) {
        if (eventId <= 0L) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        if (expectedLockVersion <= 0L) {
            throw new IllegalArgumentException("expectedLockVersion must be positive");
        }
        return mapper.reopenFailed(eventId, expectedLockVersion) == 1;
    }

    private ClaimedCompensationEvent requireClaimedEvent(ClaimedCompensationEvent claimedEvent) {
        return Objects.requireNonNull(claimedEvent, "claimedEvent must not be null");
    }

    private String requireErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        String requiredCode = errorCode.trim();
        if (requiredCode.length() > 64) {
            throw new IllegalArgumentException("errorCode exceeds 64 characters");
        }
        if (!ERROR_CODE.matcher(requiredCode).matches()) {
            throw new IllegalArgumentException("errorCode must match [A-Z][A-Z0-9_-]{0,63}");
        }
        return requiredCode;
    }

    private String requireErrorSummary(String errorSummary) {
        if (errorSummary == null || errorSummary.isBlank()) {
            throw new IllegalArgumentException("errorSummary must not be blank");
        }
        String requiredSummary = errorSummary.trim();
        if (requiredSummary.length() > 1000) {
            throw new IllegalArgumentException("errorSummary exceeds 1000 characters");
        }
        return requiredSummary;
    }

    private String requireLockOwner(String lockOwner) {
        if (lockOwner == null || lockOwner.isBlank()) {
            throw new IllegalArgumentException("lockOwner must not be blank");
        }
        String requiredOwner = lockOwner.trim();
        if (requiredOwner.length() > 64) {
            throw new IllegalArgumentException("lockOwner exceeds 64 characters");
        }
        return requiredOwner;
    }

    private long requireLeaseMicros(Duration leaseDuration) {
        Duration requiredDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (requiredDuration.isZero() || requiredDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        return toPositiveMicros(requiredDuration, "leaseDuration");
    }

    private long requireDelayMicros(Duration retryDelay) {
        Duration requiredDuration = Objects.requireNonNull(retryDelay, "retryDelay must not be null");
        if (requiredDuration.isNegative()) {
            throw new IllegalArgumentException("retryDelay must not be negative");
        }
        if (requiredDuration.isZero()) {
            return 0L;
        }
        return toPositiveMicros(requiredDuration, "retryDelay");
    }

    private long toPositiveMicros(Duration duration, String field) {
        long secondsMicros = Math.multiplyExact(duration.getSeconds(), 1_000_000L);
        long nanosMicros = (duration.getNano() + 999L) / 1_000L;
        long micros = Math.addExact(secondsMicros, nanosMicros);
        if (micros <= 0L) {
            throw new IllegalArgumentException(field + " must be at least one microsecond");
        }
        return micros;
    }
}
