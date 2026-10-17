package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireConfigurable;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.release.CreateTestReleaseContext;
import com.paytech.risk.decision.domain.release.DecisionReleaseAssembler;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.CreatedCandidate;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.DecisionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionReleaseService;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.service.processor.request.CreateTestReleaseRequest;

import lombok.AllArgsConstructor;

/** 创建 Candidate 的资格、锁与本地原子保存编排。 */
@Component
@AllArgsConstructor
public class CreateTestReleaseProcessor extends AbstractLockProcessor<CreateTestReleaseRequest, CreatedCandidate> {
    private static final List<Long> BUILT_IN_ACTION_IDS = List.of(240001L, 240002L, 240003L);
    private final Clock clock;
    private final DecisionReleaseService service;
    private final DecisionRuleSetService ruleSetService;
    private final DecisionReleaseAssembler assembler;
    private final DecisionReleaseRepository releaseRepository;
    private final DecisionRuleSetRepository ruleSetRepository;
    private final RiskDecisionCanonicalizer canonicalizer;
    private final PlatformTransactionManager transactionManager;
    private final DecisionIdentifierGenerator identifierGenerator;

    /** 沿用规则集完整逻辑 Key、普通 RLock 及原有限等待/租约。 */
    @Override
    protected LockCommand lockCommand(CreateTestReleaseRequest request) {
        String resource = ruleSetService.resourceKeyForVersion(request.getRuleSetVersionId());
        return LockCommand.tryLocks(List.of("decision:rule-set:" + resource),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    /** 一次有效创建产生一个新 Candidate，根与初始事件同事务提交。 */
    @Override
    protected CreatedCandidate processWithinLock(CreateTestReleaseRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        return transaction.execute(status -> {
            var context = prepareContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.create(context);
            return context.result();
        });
    }

    /** 锁定准确规则组成并重证可发布资格，然后一次构造完整 Context。 */
    private CreateTestReleaseContext prepareContext(CreateTestReleaseRequest request, Instant commandTime) {
        RuleSetVersion plain = ruleSetRepository.findVersion(request.getRuleSetVersionId())
            .orElseThrow(DecisionConfigurationNotFoundException::new);
        try {
            DecisionPoint point = ruleSetRepository.lockPoint(plain.decisionPointId());
            var management = ruleSetRepository.lockRuleSet(point.decisionPointId(), plain.ruleSetCode())
                .orElseThrow(() -> new ErrorException("规则集管理根缺失，请先完成迁移"));
            requireConfigurable(management);
            RuleSetVersion current = ruleSetRepository.lockCurrent(point.decisionPointId(), plain.ruleSetCode())
                .orElseThrow(() -> new ErrorException("Rule Set Code Range 锁后为空"));
            management.verifyManagementHead(current);
            RuleSetVersion target = ruleSetRepository.lockVersion(request.getRuleSetVersionId());
            DecisionReleaseAssembler.requireSameRuleSetCoordinates(plain, current, target, point);
            if (VariableRuleConfiguration.CONTENT_FORMAT.equals(target.contentFormat()))
                throw new BizCheckFailException("统一变量仅支持配置，不能创建执行候选");

            DecisionRuleSetRepository.DecisionRuleSetAggregate beforeCompanion =
                ruleSetRepository.requireConfiguration(target.ruleSetVersionId());
            if (beforeCompanion.pointVersion() == null) {
                throw new BizCheckFailException("该规则版本没有旧发布组成，仅支持规则和流程配置");
            }
            DecisionReleaseAssembler.requireClosureCoordinates(beforeCompanion, point, target);
            DecisionPointVersion companion =
                ruleSetRepository.lockCompanion(beforeCompanion.pointVersion().pointVersionId());
            DecisionRuleSetRepository.DecisionRuleSetAggregate composition =
                ruleSetRepository.requireClosure(target.ruleSetVersionId());
            DecisionReleaseAssembler.requireClosureCoordinates(composition, point, target);
            DecisionReleaseAssembler.requireLockedComposition(point, target, companion, composition);
            if (!request.getExpectedRuleSetContentHash().equals(target.contentHash())) {
                throw new ConcurrentRequestException("Rule Set Version 内容哈希已变化");
            }
            var actions = ruleSetRepository.requireActionVersions(BUILT_IN_ACTION_IDS);
            ruleSetRepository.requireNoActionParameterDefinitions(BUILT_IN_ACTION_IDS);
            var catalog = actions.stream().map(value -> DecisionReleaseAssembler.persistedAction(value,
                value.status() == ExecutionVersionStatus.ACTIVE ? ConfigurationStatus.ACTIVE : null)).toList();
            var rule = ruleSetService.detailForPersistedReference(target.ruleSetVersionId());
            var frozen = assembler.frozenBundle(composition, true, assembler.verifiedPersistedCatalog(catalog),
                rule.ruleSetProjection());
            if (!request.getExpectedRuleSetContentHash().equals(frozen.ruleSetHash())) {
                throw new ErrorException("Rule Set Version 存储哈希与规范重算不一致");
            }

            int releaseNo = releaseRepository.nextReleaseNoWhileCheckoutPointLocked();
            if (releaseNo <= 0) {
                throw new ErrorException("Release No 分配结果无效");
            }
            long releaseId = nextIdentifier("releaseId");
            long auditEventId = nextIdentifier("auditEventId");
            var releaseProjection = assembler.releaseProjection(releaseId, releaseNo, composition, frozen.pointHash(),
                frozen.ruleSetHash());
            String releaseHash = DecisionReleaseAssembler
                .canonicalHash(() -> canonicalizer.decisionRelease(releaseProjection), "Release 内容");
            DecisionRelease release =
                new DecisionRelease(releaseId, Integer.toString(releaseNo), point.decisionPointId(),
                    companion.pointVersionId(), DecisionEnvironment.TEST, DecisionReleaseStatus.DRAFT, null,
                    releaseHash, request.getActorAccountId(), null, null, 1, commandTime, commandTime);
            ReleaseAuditEvent initial = DecisionReleaseAssembler.stateChange(auditEventId, releaseId, commandTime, null,
                DecisionReleaseStatus.DRAFT, "CANDIDATE_CREATED", ReleaseAuditEvent.triggerId(releaseId, "CREATE"),
                request.getActorAccountId(), 1);
            return new CreateTestReleaseContext(release, point, target, initial);
        } catch (NoSuchElementException exception) {
            throw new ErrorException("首次命中后 Candidate 创建资源消失", exception);
        }
    }

    /** 根和审计身份由本地 ID 生成器提供。 */
    private long nextIdentifier(String label) {
        long value = identifierGenerator.nextIdentifier();
        if (value <= 0) {
            throw new ErrorException(label + " 生成结果无效");
        }
        return value;
    }
}
