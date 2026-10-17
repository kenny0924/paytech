package com.paytech.risk.decision.service.processor;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.managementResourceKey;
import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireConfigurable;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.rule.ChangeReasonPolicy;
import com.paytech.risk.decision.domain.rule.CreateRuleSetVersionContext;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAssembler;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedRule;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedRuleSet;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domain.variable.VariableConditionValidator;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.service.*;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.CreatedRuleSet;
import com.paytech.risk.decision.service.processor.request.CreateRuleSetVersionRequest;
import com.paytech.risk.decision.service.query.RuleSetConfigurationReader;

import lombok.AllArgsConstructor;

/** 保存新规则版本的校验、资源锁和原子写入编排。 */
@Component
@AllArgsConstructor
public class CreateRuleSetVersionProcessor extends AbstractLockProcessor<CreateRuleSetVersionRequest, CreatedRuleSet> {
    private final Clock clock;
    private final RuleAstValidator astValidator;
    private final DecisionActionRegistry actionRegistry;
    private final RiskVariableService variableService;
    private final DecisionRuleSetService ruleSetService;
    private final DecisionRuleSetAssembler assembler;
    private final RuleSetManagementService service;
    private final DecisionDictionaryService dictionaryService;
    private final DecisionRuleSetRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final RuleSetConfigurationReader reader;

    /** 沿用完整规则集锁键、普通 RLock 和原 500ms/30s 等待/租约。 */
    @Override
    protected LockCommand lockCommand(CreateRuleSetVersionRequest request) {
        String resource = managementResourceKey(request.getPointCode(), request.getRuleSetCode());
        return LockCommand.tryLocks(List.of("decision:rule-set:" + resource),
            DecisionResourceLocks.WAIT_TIME, DecisionResourceLocks.LEASE_TIME);
    }

    /** 资源锁覆盖完整本地事务，事务结束后由模板释放。 */
    @Override
    protected CreatedRuleSet processWithinLock(CreateRuleSetVersionRequest request) {
        NormalizedRuleSet normalized = astValidator.validateAndNormalize(request.getDraft());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        CreateRuleSetVersionContext context = transaction.execute(status -> {
            var prepared = prepareContext(request, normalized, clock.instant().truncatedTo(ChronoUnit.MICROS));
            service.create(prepared);
            return prepared;
        });
        return context.result();
    }

    /** 持锁后重新读取事实并一次构造完整 Context。 */
    CreateRuleSetVersionContext prepareContext(CreateRuleSetVersionRequest request, NormalizedRuleSet normalized,
        Instant commandTime) {
        if (!VariableRuleConfiguration.OPERAND_FORMAT.equals(normalized.operandFormat())) {
            throw new RiskVariableException(RiskVariableException.Reason.LEGACY_AUTHORING_NOT_SUPPORTED,
                "请刷新页面并使用统一变量保存");
        }
        ChangeReasonPolicy.normalize(normalized.changeReason());
        for (NormalizedRule rule : normalized.rules()) {
            if (rule.action() != null && actionRegistry.require(rule.action()).decisionCode() == DecisionValue.DENY
                && (rule.reasonCode() == null || rule.reasonMessage() == null))
                throw new BizCheckFailException("新保存的拒绝规则必须填写原因代码和文案");
        }

        DecisionPoint located =
            repository.findPointByCode(normalized.pointCode()).orElseThrow(DecisionConfigurationNotFoundException::new);
        DecisionPoint point = repository.lockPoint(located.decisionPointId());
        assembler.verifySamePoint(located, point);
        assembler.verifyPoint(point);

        RuleSet management = repository.lockRuleSet(point.decisionPointId(), normalized.ruleSetCode()).orElse(null);
        if (management != null)
            requireConfigurable(management);
        RuleSetVersion current = repository.lockCurrent(point.decisionPointId(), normalized.ruleSetCode()).orElse(null);
        if (current != null) {
            current.verifyCurrentHead(point.decisionPointId(), normalized.ruleSetCode());
        }
        if ((management == null) != (current == null))
            throw new ErrorException("规则集根与历史版本不完整，请先完成迁移");
        if (management != null)
            management.verifyManagementHead(current);
        assembler.nextRuleSetVersion(normalized, current);
        Map<String, RiskVariable.Entry> definitions =
            variableService.lockDefinitions(VariableRuleConfiguration.references(normalized));
        Map<String, List<RiskVariable.Member>> members = new HashMap<>();
        definitions.values().stream().filter(e -> e.definition().sourceType() == VariableSource.COLLECTION)
            .forEach(e -> members.put(e.definition().variableCode(), variableService.selectedCollection(e)));
        normalized = VariableRuleConfiguration.validate(normalized, new VariableConditionValidator.Context(
            normalized.pointCode(), VariableUsage.RULE_CONDITION, null, definitions, members));
        dictionaryService.validateSelections(normalized,
            current == null ? null : ruleSetService.detailForPersistedReference(current.ruleSetVersionId()));
        List<ActionVersion> catalog = reader.requireCatalog(point.pointCode());
        DecisionPointVersion currentCompanion = current != null && current.status() == RuleSetVersionStatus.DRAFT
            ? reader.lockAndReloadCompanion(point, current, false) : null;
        return assembler.createContext(normalized, point, management, current, currentCompanion, catalog,
            request.getActorAccountId(), commandTime);
    }
}
