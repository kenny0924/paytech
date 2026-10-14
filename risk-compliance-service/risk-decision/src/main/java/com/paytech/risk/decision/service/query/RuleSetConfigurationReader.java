package com.paytech.risk.decision.service.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.LongStream;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.ActionVersion;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetReleaseReference;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAssembler;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.convert.RuleActionConfigurationConverter;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import lombok.AllArgsConstructor;

/** 规则管理用例共用的目录读取、旧编排锁定和活动发布引用核验。 */
@Component
@AllArgsConstructor
public class RuleSetConfigurationReader {
    private final DecisionActionRegistry actionRegistry;
    private final DecisionRuleSetAssembler assembler;
    private final DecisionRuleSetRepository repository;

    /**
     * 读取并核验一个决策点工程注册的完整持久化动作目录
     *
     * @param pointCode 决策点编码
     * @return 该决策点的终态动作，及其开放的附加动作
     */
    public List<ActionVersion> requireCatalog(String pointCode) {
        DecisionPointAuthoringDescriptor descriptor = DecisionPointAuthoringDescriptor.require(pointCode);
        try {
            List<Long> terminalIds = LongStream.range(0, descriptor.actions().size())
                .map(index -> descriptor.firstActionVersionId() + index).boxed().toList();
            List<ActionVersion> catalog = repository.requireActionVersions(terminalIds);
            repository.requireNoActionParameterDefinitions(terminalIds);
            actionRegistry.verifyPersistedCatalog(descriptor, catalog.stream().map(this::toActiveCatalogRow).toList());
            for (ActionVersion action : catalog) {
                var expected = actionRegistry.require(new ActionKey(action.actionCode(), action.versionNo()));
                if (!expected.actionName().equals(action.actionName())) {
                    throw corruption("持久化动作名称与内置目录不一致");
                }
            }
            List<ActionVersion> complete = new ArrayList<>(catalog);
            if (!descriptor.additionalActionsSupported()) {
                return List.copyOf(complete);
            }
            List<Long> configuredIds = RuleActionConfigurationCatalog.actions().stream()
                .map(RuleActionConfigurationCatalog.ConfigurationAction::actionVersionId).toList();
            List<ActionVersion> configured = repository.requireActionVersions(configuredIds);
            configured.forEach(action -> RuleActionConfigurationConverter.verifyAction(action, true));
            RuleActionConfigurationConverter.verifyDefinitions(repository.actionParameterDefinitions(configuredIds),
                RuleActionConfigurationCatalog.actions());
            complete.addAll(configured);
            return List.copyOf(complete);
        } catch (ErrorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw corruption("持久化动作目录完整性校验失败", exception);
        }
    }

    /** 核验活动发布引用后返回确实在用的整体根。 */
    public Set<Long> usedRuleSets(List<RuleSet> roots, boolean lock) {
        if (roots.isEmpty()) return Set.of();
        List<Long> ids = roots.stream().map(RuleSet::getRuleSetId).distinct().toList();
        List<Long> pointIds = roots.stream().map(RuleSet::getDecisionPointId).distinct().toList();
        Set<Long> used = new HashSet<>();
        for (RuleSetReleaseReference ref : repository.releaseReferences(pointIds, ids, lock)) {
            ref.verifyIntegrity();
            if (ids.contains(ref.getRuleSetId())) used.add(ref.getRuleSetId());
        }
        return Set.copyOf(used);
    }

    /** 锁定旧编排并按原协议复核其完整内容与冻结资格。 */
    public DecisionPointVersion lockAndReloadCompanion(DecisionPoint lockedPoint, RuleSetVersion lockedRuleSet, boolean forFreeze) {
        DecisionRuleSetRepository.DecisionRuleSetAggregate discovered = requireRawAfterKnownReference(
            lockedRuleSet.ruleSetVersionId());
        if (!lockedPoint.equals(discovered.decisionPoint()) || !lockedRuleSet.equals(discovered.ruleSetVersion()))
            throw corruption("规则锁与读取内容不一致");
        DecisionRuleSetAggregate aggregate = assembler.toAggregate(discovered);
        DecisionPointVersion lockedCompanion = null;
        if (discovered.pointVersion() != null) {
            lockedCompanion = repository.lockCompanion(discovered.pointVersion().pointVersionId());
            // 旧协议 Hash 只按原字段快照复核，普通新配置没有此分支。
            var legacy = repository.requireClosure(lockedRuleSet.ruleSetVersionId());
            assembler.toAggregate(legacy, true);
            if (lockedCompanion.status() != DecisionPointVersionStatus.DRAFT
                || !lockedCompanion.equals(legacy.pointVersion()) || !lockedRuleSet.equals(legacy.ruleSetVersion()))
                throw corruption("旧编排锁与完整历史组成不一致");
        }
        // 运行期决策点的统一变量规则经执行发布运行，可以冻结；无终态动作的规则只表达“命中”，供流程连线判断。
        if (forFreeze && DecisionPointAuthoringDescriptor.require(lockedPoint.pointCode()).runtimeSupported())
            return lockedCompanion;
        if (forFreeze && VariableRuleConfiguration.CONTENT_FORMAT.equals(lockedRuleSet.contentFormat()))
            throw new BizCheckFailException("统一变量仅支持配置，当前版本不能冻结");
        if (forFreeze && aggregate.rules().stream().anyMatch(rule -> rule.action() == null
            && rule.additionalActions().isEmpty()))
            throw new BizCheckFailException("未配置动作的规则仅支持流程配置，当前版本不能冻结");
        if (forFreeze && aggregate.rules().stream().anyMatch(rule -> !rule.additionalActions().isEmpty()))
            throw new BizCheckFailException("附加动作仅支持配置，执行能力未就绪，当前版本不能冻结");
        return lockedCompanion;
    }

    /** 已知身份消失属于持久化损坏，不降级为未找到。 */
    private DecisionRuleSetRepository.DecisionRuleSetAggregate requireRawAfterKnownReference(long id) {
        try {
            return repository.requireConfiguration(id);
        } catch (NoSuchElementException exception) {
            throw corruption("锁后 Rule Set 闭包消失", exception);
        }
    }

    /** 将持久化不变量损坏报告为内部错误。 */
    private static ErrorException corruption(String message) {
        return new ErrorException(message);
    }

    /** 首次配置要求动作目录仍为 ACTIVE。 */
    private PersistedActionVersion toActiveCatalogRow(ActionVersion value) {
        if (value.actionVersionId() <= 0 || value.status() != ExecutionVersionStatus.ACTIVE) {
            throw corruption("Action Version 非 ACTIVE 或标识损坏");
        }
        return toStableActionRow(value);
    }

    /** 提取已冻结动作实现和执行能力。 */
    private PersistedActionVersion toStableActionRow(ActionVersion value) {
        return new PersistedActionVersion(value.actionVersionId(), value.actionCode(), value.versionNo(),
            value.handlerCode(), value.implementationVersion(), value.implementationHash(), value.actionCategory(),
            value.terminalFlag(), value.decisionCode(), value.mergePolicy(), value.shadowSupportedFlag(),
            value.terminalFlag() ? ConfigurationStatus.ACTIVE : ConfigurationStatus.CONFIGURATION_ONLY);
    }

    /** 将持久化不变量损坏报告为内部错误。 */
    private static ErrorException corruption(String message, Throwable cause) {
        return new ErrorException(message, cause);
    }

}
