package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.managementResourceKey;
import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireConfigurable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAssembler;
import com.paytech.risk.decision.domain.rule.DeleteRuleSetContext;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.DecisionConfigurationNotFoundException;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.DeletedRuleSet;
import com.paytech.risk.decision.domainservice.service.RuleSetManagementConflictException;
import com.paytech.risk.decision.domainservice.service.RuleSetManagementConflictException.Reason;
import com.paytech.risk.decision.domainservice.service.RuleSetManagementService;
import com.paytech.risk.decision.service.processor.request.DeleteRuleSetRequest;
import com.paytech.risk.decision.service.query.RuleSetConfigurationReader;

import lombok.AllArgsConstructor;

/** 整体删除的版本与活动引用核验，保留全部历史。 */
@Component
@AllArgsConstructor
public class DeleteRuleSetProcessor extends AbstractLockProcessor<DeleteRuleSetRequest, DeletedRuleSet> {
    private final Clock clock;
    private final DecisionRuleSetAssembler assembler;
    private final RuleSetManagementService service;
    private final DecisionRuleSetRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final RuleSetConfigurationReader reader;
    private final RedisResourceLockProperties lockProperties;

    /** 沿用完整规则集锁键、普通 RLock 和已配置的等待/租约。 */
    @Override
    protected LockCommand lockCommand(DeleteRuleSetRequest request) {
        RuleSet root =
            repository.findRuleSet(request.getRuleSetId()).orElseThrow(DecisionConfigurationNotFoundException::new);
        root.verifyIntegrity();
        DecisionPoint point =
            repository.findPoint(root.getDecisionPointId()).orElseThrow(() -> new ErrorException("规则集所属事件点缺失"));
        String resource = managementResourceKey(point.pointCode(), root.getRuleSetCode());
        return LockCommand.tryLocks(List.of("decision:rule-set:" + resource),
            Duration.ofNanos(lockProperties.requiredWaitNanos()),
            Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 资源锁覆盖完整本地事务，事务结束后由模板释放。 */
    @Override
    protected DeletedRuleSet processWithinLock(DeleteRuleSetRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        DeleteRuleSetContext context = transaction.execute(status -> {
            var prepared = prepareContext(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.delete(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 持锁后重新读取事实并一次构造完整 Context。 */
    DeleteRuleSetContext prepareContext(DeleteRuleSetRequest request, Instant commandTime) {
        RuleSet located =
            repository.findRuleSet(request.getRuleSetId()).orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionPoint point = repository.lockPoint(located.getDecisionPointId());
        assembler.verifyPoint(point);
        RuleSet root = repository.lockRuleSet(located.getDecisionPointId(), located.getRuleSetCode())
            .orElseThrow(() -> new ErrorException("已定位的规则集根消失"));
        if (root.getRuleSetId() != request.getRuleSetId() || root.getDecisionPointId() != point.decisionPointId()
            || !root.getRuleSetCode().equals(located.getRuleSetCode()))
            throw new ErrorException("规则集删除锁坐标与事务事实不一致");
        requireConfigurable(root);
        root.verifyExpectedVersion(request.getExpectedVersion());
        RuleSetVersion current = repository.lockCurrent(root.getDecisionPointId(), root.getRuleSetCode())
            .orElseThrow(() -> new ErrorException("规则集当前版本缺失"));
        root.verifyManagementHead(current);
        if (reader.usedRuleSets(List.of(root), true).contains(root.getRuleSetId()))
            throw new RuleSetManagementConflictException(Reason.IN_USE, "规则集正在使用，无法删除");
        DeleteRuleSetContext context =
            new DeleteRuleSetContext(root, current, request.getActorAccountId(), commandTime);
        context.verifyIntegrity(request.getExpectedVersion());
        return context;
    }
}
