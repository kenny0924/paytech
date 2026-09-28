package com.paytech.risk.domainservice.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.core.dal.dataobject.CommandIdempotency;
import com.paytech.risk.domain.enums.CommandIdempotencyStatus;
import com.paytech.risk.domain.enums.CommandOperationType;
import com.paytech.risk.domainservice.repository.CommandIdempotencyRepository;
import com.paytech.risk.domainservice.service.CommandIdempotencyService;
import org.springframework.stereotype.Service;

/**
 * 在短写事务内分类命令幂等记录，并对任何损坏状态失败关闭。
 */
@Service
public class CommandIdempotencyServiceImpl implements CommandIdempotencyService {

    private static final CommandIdempotencyClaimClassifier CLAIM_CLASSIFIER = new CommandIdempotencyClaimClassifier();

    private final CommandIdempotencyRepository commandIdempotencyRepository;

    public CommandIdempotencyServiceImpl(CommandIdempotencyRepository commandIdempotencyRepository) {
        this.commandIdempotencyRepository = commandIdempotencyRepository;
    }

    @Override
    public CommandClaim claim(CommandRequest request, Instant firstNow) {
        requireRequest(request);
        requireTime(firstNow);

        CommandIdempotencyRepository.LockedCommandClaim claim = commandIdempotencyRepository.claim(
            request.identity(), request.requestHash(), firstNow);
        if (claim == null) {
            throw new ErrorException("命令幂等声明未返回结果");
        }
        if (claim.first()) {
            return new First(request);
        }
        return CLAIM_CLASSIFIER.classify(request, claim.record());
    }

    @Override
    public void recordValidateProcessingReference(CommandRequest request, long releaseId, Instant now) {
        if (request == null || request.identity().operationType() != CommandOperationType.VALIDATE_TEST_DECISION_RELEASE) {
            throw new ErrorException("仅 Validate 命令可保留 PROCESSING 响应引用");
        }
        requireRequest(request);
        requireTime(now);

        ResponseReference reference = CommandIdempotencyService.responseReference(
            CommandOperationType.VALIDATE_TEST_DECISION_RELEASE, Long.toString(releaseId));
        int affectedRows = commandIdempotencyRepository.recordValidateProcessingReference(
            request.identity(), request.requestHash(), reference, now);
        requireExactlyOne(affectedRows, "Validate PROCESSING 响应引用");
    }

    @Override
    public void complete(CommandRequest request, ResponseReference responseReference, Instant now) {
        requireRequest(request);
        requireTime(now);
        if (responseReference == null) {
            throw new InvalidParameterException("响应资源引用格式无效");
        }
        ResponseReference compatibleReference = CommandIdempotencyService.parseResponseReference(
            request.identity().operationType(), responseReference.value());
        int affectedRows = commandIdempotencyRepository.complete(
            request.identity(), request.requestHash(), compatibleReference, now);
        requireExactlyOne(affectedRows, "命令幂等完成状态");
    }

    private void requireRequest(CommandRequest request) {
        if (request == null || request.identity() == null || !isCanonicalRequestHash(request.requestHash())) {
            throw new InvalidParameterException("命令请求哈希格式无效");
        }
    }

    private void requireTime(Instant now) {
        if (now == null || !now.equals(now.truncatedTo(ChronoUnit.MICROS))) {
            throw new InvalidParameterException("命令写入时间必须是 UTC 微秒精度");
        }
    }

    private void requireExactlyOne(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new ErrorException(action + "条件更新失败");
        }
    }

    private boolean isCanonicalRequestHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}

/**
 * 纯粹分类一条已由 Repository 在当前写事务中锁住的幂等记录。
 */
final class CommandIdempotencyClaimClassifier {

    CommandIdempotencyService.CommandClaim classify(
        CommandIdempotencyService.CommandRequest request,
        CommandIdempotency existing
    ) {
        if (existing == null) {
            throw new ErrorException("命令幂等唯一冲突后不存在受锁记录");
        }
        requireMatchingIdentity(request.identity(), existing);
        if (!isCanonicalRequestHash(existing.getRequestHash())) {
            throw new ErrorException("命令幂等记录请求哈希损坏");
        }
        if (!Objects.equals(request.requestHash(), existing.getRequestHash())) {
            throw new ConcurrentRequestException("同一命令标识不能复用不同请求");
        }
        if (existing.getStatus() == null) {
            throw new ErrorException("命令幂等记录状态损坏");
        }

        return switch (existing.getStatus()) {
            case SUCCEEDED -> new CommandIdempotencyService.SucceededReplay(compatibleStoredReference(
                request.identity().operationType(), existing.getResponseRef()));
            case PROCESSING -> processingClaim(request.identity().operationType(), existing.getResponseRef());
            case FAILED -> throw new ErrorException("命令幂等记录处于不可恢复失败状态");
        };
    }

    private CommandIdempotencyService.CommandClaim processingClaim(
        CommandOperationType operationType,
        String responseRef
    ) {
        if (operationType != CommandOperationType.VALIDATE_TEST_DECISION_RELEASE) {
            throw new ErrorException("普通命令不允许存在可见 PROCESSING 记录");
        }
        CommandIdempotencyService.ResponseReference reference = compatibleStoredReference(operationType, responseRef);
        return new CommandIdempotencyService.ValidateProcessingRecovery(reference.value().substring("release:".length()));
    }

    private CommandIdempotencyService.ResponseReference compatibleStoredReference(
        CommandOperationType operationType,
        String responseRef
    ) {
        try {
            return CommandIdempotencyService.parseResponseReference(operationType, responseRef);
        } catch (InvalidParameterException exception) {
            throw new ErrorException("命令幂等记录响应引用损坏", exception);
        }
    }

    private void requireMatchingIdentity(CommandIdempotencyService.CommandIdentity identity, CommandIdempotency existing) {
        if (existing.getIdempotencyId() == null || existing.getIdempotencyId() <= 0L
            || !Objects.equals(identity.callerScope(), existing.getCallerScope())
            || identity.operationType() != existing.getOperationType()
            || !Objects.equals(identity.idempotencyKey(), existing.getIdempotencyKey())
            || existing.getExpireTime() == null
            || existing.getCreateTime() == null
            || existing.getUpdateTime() == null
            || !existing.getExpireTime().equals(existing.getCreateTime().plus(Duration.ofHours(24)))
            || existing.getUpdateTime().isBefore(existing.getCreateTime())) {
            throw new ErrorException("命令幂等记录身份损坏");
        }
    }

    private boolean isCanonicalRequestHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
