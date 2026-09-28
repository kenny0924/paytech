package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireConfigurable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAssembler;
import com.paytech.risk.decision.domain.rule.FreezeRuleSetVersionContext;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.FrozenRuleSet;
import com.paytech.risk.decision.domainservice.service.RuleSetManagementService;
import com.paytech.risk.decision.service.processor.request.FreezeRuleSetVersionRequest;
import com.paytech.risk.decision.service.query.RuleSetConfigurationReader;

import lombok.AllArgsConstructor;

/** 冻结规则的内容核验、资源锁和原子写入编排。 */
@Component
@AllArgsConstructor
public class FreezeRuleSetVersionProcessor extends AbstractLockProcessor<FreezeRuleSetVersionRequest, FrozenRuleSet> {
    private final Clock clock;
    private final DecisionRuleSetService ruleSetService;
    private final DecisionRuleSetAssembler assembler;
    private final RuleSetManagementService service;
    private final DecisionRuleSetRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final RuleSetConfigurationReader reader;
    private final RedisResourceLockProperties lockProperties;

    /** 沿用完整规则集锁键、普通 RLock 和已配置的等待/租约。 */
    @Override
    protected LockCommand lockCommand(FreezeRuleSetVersionRequest request) {
        String resource = ruleSetService.resourceKeyForVersion(request.getRuleSetVersionId());
        return LockCommand.tryLocks(List.of("decision:rule-set:" + resource),
            Duration.ofNanos(lockProperties.requiredWaitNanos()),
            Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 资源锁覆盖完整本地事务，事务结束后由模板释放。 */
    @Override
    protected FrozenRuleSet processWithinLock(FreezeRuleSetVersionRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        FreezeRuleSetVersionContext context = transaction.execute(status -> {
            var prepared = prepareContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.freeze(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 持锁后重新读取事实并一次构造完整 Context。 */
    FreezeRuleSetVersionContext prepareContext(FreezeRuleSetVersionRequest request, Instant commandTime) {
        RuleSetVersion located = repository.findVersion(request.getRuleSetVersionId())
            .orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionPoint point = repository.lockPoint(located.decisionPointId());
        assembler.verifyPoint(point);
        RuleSet management = repository.lockRuleSet(point.decisionPointId(), located.ruleSetCode())
            .orElseThrow(() -> new ErrorException("规则集管理根缺失，请先完成迁移"));
        requireConfigurable(management);
        RuleSetVersion current = repository.lockCurrent(point.decisionPointId(), located.ruleSetCode())
            .orElseThrow(() -> new ErrorException("Rule Set 当前版本缺失"));
        current.verifyCurrentHead(point.decisionPointId(), located.ruleSetCode());
        management.verifyManagementHead(current);
        RuleSetVersion target = current.ruleSetVersionId() == located.ruleSetVersionId() ? current
            : repository.lockVersion(located.ruleSetVersionId());
        if (target.ruleSetVersionId() != current.ruleSetVersionId()) {
            throw new ConcurrentRequestException("Rule Set Version 已不是最新版本");
        }
        if (!target.contentHash().equals(request.getExpectedContentHash())) {
            throw new ConcurrentRequestException("Rule Set 内容已变化");
        }
        if (target.status() != RuleSetVersionStatus.DRAFT) {
            throw new BizCheckFailException("只有 DRAFT Rule Set Version 可以冻结");
        }

        reader.requireCatalog();
        DecisionPointVersion companion = reader.lockAndReloadCompanion(point, target, true);
        FreezeRuleSetVersionContext context =
            new FreezeRuleSetVersionContext(management, target, companion, request.getActorAccountId(), commandTime);
        context.verifyIntegrity();
        return context;
    }
}
