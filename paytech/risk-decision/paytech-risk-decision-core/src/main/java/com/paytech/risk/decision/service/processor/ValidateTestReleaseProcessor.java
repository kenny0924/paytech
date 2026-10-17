package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseAssembler;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ValidatedCandidate;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domain.release.ValidateTestReleaseContext;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.DecisionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionReleaseService;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.service.processor.request.ValidateTestReleaseRequest;
import com.paytech.risk.decision.service.query.DecisionReleaseReader;

import lombok.AllArgsConstructor;

/** 同一发布的可恢复校验：开始短事务、只读快照、纯检查、完成短事务。 */
@Component
@AllArgsConstructor
public class ValidateTestReleaseProcessor
    extends AbstractLockProcessor<ValidateTestReleaseRequest, ValidatedCandidate> {
    private static final List<Long> BUILT_IN_ACTION_IDS = List.of(240001L, 240002L, 240003L);
    private final Clock clock;
    private final DecisionReleaseReader reader;
    private final DecisionReleaseService service;
    private final DecisionRuleSetService ruleSetService;
    private final DecisionReleaseAssembler assembler;
    private final DecisionReleaseRepository releases;
    private final DecisionRuleSetRepository ruleSetRepository;
    private final PlatformTransactionManager transactionManager;
    private final DecisionIdentifierGenerator identifierGenerator;
    private final DecisionReleaseStaticValidator validator;

    /** 对实际发布资源互斥；数据库行锁仍保证租约到期后的并发正确性。 */
    @Override
    protected LockCommand lockCommand(ValidateTestReleaseRequest request) {
        return LockCommand.tryLocks(List.of("decision:release:" + request.getReleaseId()),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    /** 已完成时提前返回；未完成只恢复原校验，不创建另一轮校验身份。 */
    @Override
    protected ValidatedCandidate processWithinLock(ValidateTestReleaseRequest request) {
        TransactionTemplate write = new TransactionTemplate(transactionManager);
        write.setTimeout(5);
        var context = write.execute(status -> startOrResume(request));
        if (context.completed()) {
            return context.result();
        }

        TransactionTemplate read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var closure = read.execute(status -> {
            var stored = reader.requireFrozenComposition(request.getReleaseId());
            var rule = ruleSetService
                .detailForPersistedReference(stored.frozenComposition().ruleSetVersion().ruleSetVersionId());
            var rows = ruleSetRepository.requireActionVersions(BUILT_IN_ACTION_IDS);
            ruleSetRepository.requireNoActionParameterDefinitions(BUILT_IN_ACTION_IDS);
            var catalog = rows.stream().map(value -> DecisionReleaseAssembler.persistedAction(value,
                value.status() == ExecutionVersionStatus.ACTIVE ? ConfigurationStatus.ACTIVE : null)).toList();
            return assembler.validationClosure(stored, rule.ruleSetProjection(), catalog);
        });
        List<StaticCheckResult> checks = assembler.requireExactChecks(validator.validate(closure));
        return write.execute(status -> complete(request, checks));
    }

    /** 第一次记录开始事实；重入必须匹配原操作者与原 expectedVersion。 */
    private ValidateTestReleaseContext startOrResume(ValidateTestReleaseRequest request) {
        var context = lockedContext(request.getReleaseId());
        if (context.getRelease().status() != DecisionReleaseStatus.DRAFT) {
            context.verifyOriginalValidation(request.getActorAccountId(), request.getExpectedVersion());
            return context;
        }
        var root = context.getRelease();
        root.verifyValidationStart(request.getExpectedVersion());
        Instant time = now();
        var started = DecisionReleaseAssembler.stateChange(nextId(), root.releaseId(), time,
            DecisionReleaseStatus.DRAFT, DecisionReleaseStatus.VALIDATING, "STATIC_VALIDATION_STARTED",
            ReleaseAuditEvent.triggerId(root.releaseId(), "VALIDATE"), request.getActorAccountId(), 2);
        var startedContext = new ValidateTestReleaseContext(root, started, List.of(), null);
        service.startValidation(startedContext);
        return startedContext;
    }

    /** 事务 B 再锁同一根；并发调用已经完成时复用其八项事实。 */
    private ValidatedCandidate complete(ValidateTestReleaseRequest request, List<StaticCheckResult> checks) {
        var current = lockedContext(request.getReleaseId());
        current.verifyOriginalValidation(request.getActorAccountId(), request.getExpectedVersion());
        if (current.completed()) {
            return current.result();
        }
        var root = current.getRelease();
        Instant time = now();
        var audits = checks.stream()
            .map(check -> DecisionReleaseAssembler.staticCheck(nextId(), root.releaseId(), time, check)).toList();
        var rejected = checks.stream().anyMatch(check -> check.checkStatus() == StaticCheckStatus.FAILED)
            ? DecisionReleaseAssembler.stateChange(nextId(), root.releaseId(), time, DecisionReleaseStatus.VALIDATING,
                DecisionReleaseStatus.REJECTED, "STATIC_VALIDATION_FAILED", current.getStarted().triggerId(),
                current.getStarted().actorId(), 3)
            : null;
        var completed = new ValidateTestReleaseContext(root, current.getStarted(), audits, rejected);
        service.completeValidation(completed);
        return completed.result();
    }

    /** 用同一行锁根读取完整审计及冻结闭包，损坏或部分证据一律失败关闭。 */
    private ValidateTestReleaseContext lockedContext(long releaseId) {
        var root = releases.lockRoot(releaseId).orElseThrow(DecisionConfigurationNotFoundException::new);
        root.verifyIntegrity();
        try {
            var stored = reader.requireClosure(root);
            var rule = ruleSetService
                .detailForPersistedReference(stored.frozenComposition().ruleSetVersion().ruleSetVersionId());
            assembler.domainAggregate(stored, rule.ruleSetProjection());
            return new ValidateTestReleaseContext(root,
                stored.stateChanges().size() >= 2 ? stored.stateChanges().get(1) : null, stored.staticChecks(),
                stored.stateChanges().size() == 3 ? stored.stateChanges().get(2) : null);
        } catch (NoSuchElementException missing) {
            throw new ErrorException("锁定发布的冻结组成缺失", missing);
        }
    }

    /** 审计时刻统一为 UTC 微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 每一条新增审计使用独立的正数身份。 */
    private long nextId() {
        long id = identifierGenerator.nextIdentifier();
        if (id <= 0) {
            throw new ErrorException("发布审计标识生成失败");
        }
        return id;
    }
}
