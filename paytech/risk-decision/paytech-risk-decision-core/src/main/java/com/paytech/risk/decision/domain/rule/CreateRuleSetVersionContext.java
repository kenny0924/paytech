package com.paytech.risk.decision.domain.rule;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireConfigurable;
import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireIncrementable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.RiskRule;
import com.paytech.risk.decision.core.dal.dataobject.RuleAction;
import com.paytech.risk.decision.core.dal.dataobject.RuleActionParameter;
import com.paytech.risk.decision.core.dal.dataobject.RuleNode;
import com.paytech.risk.decision.core.dal.dataobject.RuleOperand;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.CreatedRuleSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 保存规则版本的完整事实；Service 不重新加载关联或替换对象。 */
@Getter
@AllArgsConstructor
public class CreateRuleSetVersionContext {
    /** 管理根，首次保存为本次生成的新对象。 */
    private final RuleSet ruleSet;

    /** 本次准备追加的准确业务版本。 */
    private final RuleSetVersion ruleSetVersion;

    /** 锁定的原头版本；首次创建为 null。 */
    private final RuleSetVersion previousVersion;

    /** 原草稿的旧执行编排；新配置或原头已冻结时为 null。 */
    private final DecisionPointVersion previousCompanion;

    /** 新版本全部规则。 */
    private final List<RiskRule> rules;

    /** 新版本条件节点。 */
    private final List<RuleNode> nodes;

    /** 新版本操作数。 */
    private final List<RuleOperand> operands;

    /** 新版本配置动作。 */
    private final List<RuleAction> ruleActions;

    /** 新版本动作参数。 */
    private final List<RuleActionParameter> ruleActionParameters;

    /** 只校验本上下文的身份、关联和初始状态，不查询数据库。 */
    public void verifyIntegrity() {
        requireConfigurable(ruleSet);
        if (!ruleSet.ownsVersion(ruleSetVersion) || ruleSetVersion.status() != RuleSetVersionStatus.DRAFT)
            throw new ErrorException("新 Rule Set Version 与管理根不一致");
        if (previousVersion == null) {
            ruleSet.verifyManagementHead(ruleSetVersion);
        } else {
            ruleSet.verifyManagementHead(previousVersion);
            requireIncrementable(ruleSet);
        }
        if (rules.isEmpty() || nodes.isEmpty() || operands.isEmpty())
            throw new ErrorException("新版本规则、节点和操作数不能为空");
        Map<Long, RiskRule> ruleIndex = index(rules, RiskRule::ruleId);
        Map<Long, RuleNode> nodeIndex = index(nodes, RuleNode::nodeId);
        index(operands, RuleOperand::operandId);
        Map<Long, RuleAction> actionIndex = index(ruleActions, RuleAction::ruleActionId);
        index(ruleActionParameters, RuleActionParameter::parameterId);
        if (rules.stream().anyMatch(row -> row.ruleSetVersionId() != ruleSetVersion.ruleSetVersionId())
            || nodes.stream().anyMatch(row -> !ruleIndex.containsKey(row.ruleId()))
            || operands.stream().anyMatch(row -> !nodeIndex.containsKey(row.nodeId()))
            || ruleActions.stream().anyMatch(row -> !ruleIndex.containsKey(row.ruleId()))
            || ruleActionParameters.stream().anyMatch(row -> !actionIndex.containsKey(row.ruleActionId())))
            throw new ErrorException("新版本包含跨规则或缺失的引用");
    }

    /** 从已提交的原对象组装响应事实。 */
    public CreatedRuleSet result() {
        return new CreatedRuleSet(ruleSetVersion.ruleSetVersionId(), null, ruleSetVersion.ruleSetCode(),
            ruleSetVersion.versionNo(), ruleSetVersion.status(), ruleSetVersion.contentHash(),
            ruleSetVersion.createdBy(), ruleSetVersion.createTime());
    }

    /** 核验上下文各表身份唯一且为正数。 */
    private static <T> Map<Long, T> index(List<T> rows, ToLongFunction<T> id) {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T row : rows) {
            if (row == null || id.applyAsLong(row) <= 0 || result.putIfAbsent(id.applyAsLong(row), row) != null)
                throw new ErrorException("新版本事实包含空值、无效或重复标识");
        }
        return result;
    }
}
