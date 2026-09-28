package com.paytech.risk.domainservice.repository.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.core.dal.dataobject.CommandIdempotency;
import com.paytech.risk.core.dal.mapper.CommandIdempotencyMapper;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.domain.enums.CommandIdempotencyStatus;
import com.paytech.risk.domainservice.repository.CommandIdempotencyRepository;
import com.paytech.risk.domainservice.service.CommandIdempotencyService.CommandIdentity;
import com.paytech.risk.domainservice.service.CommandIdempotencyService.ResponseReference;
import org.springframework.stereotype.Repository;

/**
 * 通过原子 upsert 与同一事务的锁读取串行化精确幂等身份。
 */
@Repository
public class CommandIdempotencyRepositoryImpl implements CommandIdempotencyRepository {

    private final CommandIdempotencyMapper mapper;

    private final DecisionIdentifierGenerator identifierGenerator;

    public CommandIdempotencyRepositoryImpl(
        CommandIdempotencyMapper mapper,
        DecisionIdentifierGenerator identifierGenerator
    ) {
        this.mapper = mapper;
        this.identifierGenerator = identifierGenerator;
    }

    @Override
    public LockedCommandClaim claim(CommandIdentity identity, String requestHash, Instant firstNow) {
        CommandIdempotency candidate = firstRecord(identity, requestHash, firstNow);
        mapper.upsertClaimCandidate(candidate);
        return classifyLockedCandidate(candidate, lock(identity));
    }

    @Override
    public CommandIdempotency lock(CommandIdentity identity) {
        return mapper.selectByIdentityForUpdate(
            identity.callerScope(), identity.operationType(), identity.idempotencyKey());
    }

    @Override
    public int recordValidateProcessingReference(
        CommandIdentity identity,
        String requestHash,
        ResponseReference responseReference,
        Instant now
    ) {
        return mapper.updateProcessingResponseReference(
            identity.callerScope(), identity.operationType(), identity.idempotencyKey(), requestHash,
            responseReference.value(), now, CommandIdempotencyStatus.PROCESSING);
    }

    @Override
    public int complete(
        CommandIdentity identity,
        String requestHash,
        ResponseReference responseReference,
        Instant now
    ) {
        return mapper.markSucceeded(
            identity.callerScope(), identity.operationType(), identity.idempotencyKey(), requestHash,
            responseReference.value(), now, CommandIdempotencyStatus.PROCESSING,
            CommandIdempotencyStatus.SUCCEEDED);
    }

    private CommandIdempotency firstRecord(CommandIdentity identity, String requestHash, Instant firstNow) {
        CommandIdempotency record = new CommandIdempotency();
        record.setIdempotencyId(requirePositiveIdentifier(identifierGenerator.nextIdentifier()));
        record.setCallerScope(identity.callerScope());
        record.setOperationType(identity.operationType());
        record.setIdempotencyKey(identity.idempotencyKey());
        record.setRequestHash(requestHash);
        record.setResponseRef(null);
        record.setStatus(CommandIdempotencyStatus.PROCESSING);
        record.setExpireTime(firstNow.plus(Duration.ofHours(24)));
        record.setCreateTime(firstNow);
        record.setUpdateTime(firstNow);
        return record;
    }

    static LockedCommandClaim classifyLockedCandidate(
        CommandIdempotency candidate,
        CommandIdempotency locked
    ) {
        if (candidate == null || candidate.getIdempotencyId() == null || candidate.getIdempotencyId() <= 0L) {
            throw new ErrorException("命令幂等候选记录损坏");
        }
        if (locked == null || locked.getIdempotencyId() == null || locked.getIdempotencyId() <= 0L) {
            throw new ErrorException("命令幂等 upsert 后未读取到目标记录");
        }
        boolean first = Objects.equals(candidate.getIdempotencyId(), locked.getIdempotencyId());
        if (first && !sameInitialRecord(candidate, locked)) {
            throw new ErrorException("命令幂等候选标识与已有记录冲突");
        }
        return new LockedCommandClaim(first, locked);
    }

    private static boolean sameInitialRecord(CommandIdempotency candidate, CommandIdempotency locked) {
        return Objects.equals(candidate.getCallerScope(), locked.getCallerScope())
            && candidate.getOperationType() == locked.getOperationType()
            && Objects.equals(candidate.getIdempotencyKey(), locked.getIdempotencyKey())
            && Objects.equals(candidate.getRequestHash(), locked.getRequestHash())
            && locked.getResponseRef() == null
            && candidate.getStatus() == locked.getStatus()
            && Objects.equals(candidate.getExpireTime(), locked.getExpireTime())
            && Objects.equals(candidate.getCreateTime(), locked.getCreateTime())
            && Objects.equals(candidate.getUpdateTime(), locked.getUpdateTime());
    }

    static long requirePositiveIdentifier(long identifier) {
        if (identifier <= 0L) {
            throw new ErrorException("命令幂等记录标识无效");
        }
        return identifier;
    }
}
