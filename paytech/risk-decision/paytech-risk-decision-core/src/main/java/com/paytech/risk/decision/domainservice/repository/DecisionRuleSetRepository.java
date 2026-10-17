package com.paytech.risk.decision.domainservice.repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.paytech.risk.decision.core.dal.dataobject.ActionParameterDefinition;
import com.paytech.risk.decision.core.dal.dataobject.ActionVersion;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointFeature;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointInputField;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointObligation;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.FeatureDependency;
import com.paytech.risk.decision.core.dal.dataobject.FeatureVersion;
import com.paytech.risk.decision.core.dal.dataobject.RiskRule;
import com.paytech.risk.decision.core.dal.dataobject.RuleAction;
import com.paytech.risk.decision.core.dal.dataobject.RuleActionParameter;
import com.paytech.risk.decision.core.dal.dataobject.RuleNode;
import com.paytech.risk.decision.core.dal.dataobject.RuleOperand;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetReleaseReference;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;

/** Decision Point 与 Rule Set 聚合的持久化边界。 */
public interface DecisionRuleSetRepository {

    /** 定位规则集整体身份，包含删除终态。 */
    Optional<RuleSet> findRuleSet(long ruleSetId);

    /** 根据版本的不变坐标定位所属管理根。 */
    Optional<RuleSet> findRuleSet(long pointId, String ruleSetCode);

    /** 调用方先持有决策点行锁，再锁根；首次创建允许不存在。 */
    Optional<RuleSet> lockRuleSet(long pointId, String ruleSetCode);

    /** 批量读取指定根相关活动引用，缺失关系不得被连接过滤。 */
    List<RuleSetReleaseReference> releaseReferences(List<Long> pointIds, List<Long> ruleSetIds, boolean lock);

    /** 被生效中或影子中的执行发布经决策流引用的规则集根 ID；写路径以锁定读读取当前事实。 */
    Set<Long> flowReleaseRuleSetIds(List<Long> ruleSetIds, boolean lock);

    /** 只读取不变事件点坐标，准备锁键时不加载规则或动作闭包。 */
    Optional<DecisionPoint> findPoint(long decisionPointId);

    Optional<DecisionPoint> findPointByCode(String pointCode);

    Optional<RuleSetVersion> findVersion(long ruleSetVersionId);

    DecisionPoint lockPoint(long decisionPointId);

    Optional<RuleSetVersion> lockCurrent(long decisionPointId, String ruleSetCode);

    RuleSetVersion lockVersion(long ruleSetVersionId);

    DecisionPointVersion lockCompanion(long pointVersionId);

    /** 普通配置读取只加载规则事实与已有编排的可选身份元数据。 */
    DecisionRuleSetAggregate requireConfiguration(long ruleSetVersionId);

    /** 普通配置读取，排除旧多绑定点中已核验的顺序编排绑定后再判断 Companion。 */
    DecisionRuleSetAggregate requireOrchestratedConfiguration(long ruleSetVersionId, long bindingId);

    /** 仅旧发布/历史使用的完整协议组成。 */
    DecisionRuleSetAggregate requireClosure(long ruleSetVersionId);

    List<ActionVersion> requireActionVersions(List<Long> actionVersionIds);

    void requireNoActionParameterDefinitions(List<Long> actionVersionIds);

    List<ActionParameterDefinition> actionParameterDefinitions(List<Long> actionVersionIds);

    RuleSetPage findPage(RuleSetPageQuery query);

    /** Rule Set 列表的数据库分页条件。 */
    record RuleSetPageQuery(
        String pointCode,
        String ruleSetCode,
        RuleSetVersionStatus status,
        int limit,
        int offset
    ) {
    }

    /** Rule Set 列表行及其唯一 Point/Companion 绑定。 */
    record RuleSetSummary(
        RuleSetVersion ruleSetVersion,
        DecisionPoint decisionPoint,
        DecisionPointRuleSet companionBinding,
        RuleSet managementRoot
    ) {
        public RuleSetSummary {
            Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
            Objects.requireNonNull(decisionPoint, "decisionPoint");
            Objects.requireNonNull(managementRoot, "managementRoot");
        }
    }

    /** Rule Set 的稳定数据库分页结果。 */
    record RuleSetPage(List<RuleSetSummary> items, long total) {
        public RuleSetPage {
            items = List.copyOf(items);
        }
    }

    /** 已完成引用核验且与 MyBatis 可变上下文脱离的完整 Rule Set 闭包。 */
    record DecisionRuleSetAggregate(
        DecisionPoint decisionPoint,
        DecisionPointVersion pointVersion,
        List<DecisionPointInputField> inputFields,
        List<DecisionPointFeature> pointFeatures,
        List<DecisionPointRuleSet> pointRuleSets,
        List<DecisionPointObligation> pointObligations,
        RuleSetVersion ruleSetVersion,
        List<RiskRule> rules,
        List<RuleNode> nodes,
        List<RuleOperand> operands,
        List<RuleAction> ruleActions,
        List<ActionVersion> actionVersions,
        List<ActionParameterDefinition> actionParameterDefinitions,
        List<RuleActionParameter> ruleActionParameters,
        List<FeatureVersion> featureVersions,
        List<FeatureDependency> featureDependencies
    ) {
        public DecisionRuleSetAggregate {
            Objects.requireNonNull(decisionPoint, "decisionPoint");
            inputFields = List.copyOf(inputFields);
            pointFeatures = List.copyOf(pointFeatures);
            pointRuleSets = List.copyOf(pointRuleSets);
            pointObligations = List.copyOf(pointObligations);
            Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
            rules = List.copyOf(rules);
            nodes = List.copyOf(nodes);
            operands = List.copyOf(operands);
            ruleActions = List.copyOf(ruleActions);
            actionVersions = List.copyOf(actionVersions);
            actionParameterDefinitions = List.copyOf(actionParameterDefinitions);
            ruleActionParameters = List.copyOf(ruleActionParameters);
            featureVersions = List.copyOf(featureVersions);
            featureDependencies = List.copyOf(featureDependencies);
        }
    }
}
