package com.paytech.risk.decision.domainservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;

/** 规则配置的共享读取边界，写入用例由独立管理 Service 保存。 */
public interface DecisionRuleSetService {

    /** 查询版本的不变锁坐标；生命周期在持锁事务内判断。 */
    String resourceKeyForVersion(long ruleSetVersionId);

    /** 读取可配置管理根下的准确历史版本。 */
    DecisionRuleSetAggregate detail(long ruleSetVersionId);

    /** 已有流程/发布引用允许读取删除根的不可变历史。 */
    DecisionRuleSetAggregate detailForPersistedReference(long ruleSetVersionId);

    /** 旧多绑定点的顺序编排引用读取准确版本，该绑定不视为规则版本的 Companion。 */
    DecisionRuleSetAggregate detailForLegacyOrchestration(long ruleSetVersionId, long bindingId);

    /**
     * 配置能力及已读取系统变量的展示投影
     *
     * @param descriptor 决策点合同
     * @param inputVariables 当前输入合同的系统输入变量，按来源字段排序；旧合同变量只读，不在其中
     * @param configurationActions 该决策点开放的仅配置附加动作；运行期决策点为空
     */
    record RuleAuthoringView(DecisionPointAuthoringDescriptor descriptor,
        List<com.paytech.risk.decision.domain.variable.RiskVariable.Entry> inputVariables,
        List<com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog.ConfigurationAction> configurationActions) {
        public RuleAuthoringView {
            Objects.requireNonNull(descriptor, "descriptor");
            inputVariables = List.copyOf(inputVariables);
            configurationActions = List.copyOf(configurationActions);
        }

        /**
         * 由决策点合同与已读取的系统变量构造编排能力
         *
         * @param descriptor 决策点合同
         * @param systemDefinitions 该决策点的全部系统变量
         * @return 只含当前输入合同输入变量与该点开放附加动作的投影
         */
        public static RuleAuthoringView from(DecisionPointAuthoringDescriptor descriptor,
            List<com.paytech.risk.decision.domain.variable.RiskVariable.Entry> systemDefinitions) {
            var point = descriptor.point();
            var inputs = systemDefinitions.stream()
                .filter(e -> e.definition().sourceType() == com.paytech.risk.decision.domain.enums.VariableSource.INPUT)
                .filter(e -> point.inputContractCode().equals(e.definition().sourceContractCode())
                    && Integer.valueOf(point.inputContractVersion()).equals(e.definition().sourceContractVersion()))
                .sorted(java.util.Comparator.comparing(e -> e.definition().sourceField())).toList();
            var actions = descriptor.additionalActionsSupported()
                ? com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog.actions()
                : List.<com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog.ConfigurationAction>of();
            return new RuleAuthoringView(descriptor, inputs, actions);
        }
    }

    /** 保留数据库计数和稳定顺序的规则分页结果。 */
    record RuleSetPage(List<RuleSetSummary> items, int page, int size, long total) {
        public RuleSetPage {
            items = immutable("items", items);
        }
    }

    /** 当前版本与整体管理事实的列表投影。 */
    record RuleSetSummary(
        long ruleSetVersionId,
        Long pointVersionId,
        String pointCode,
        String ruleSetCode,
        String ruleSetName,
        int versionNo,
        RuleSetVersionStatus status,
        String contentHash,
        long createdBy,
        String changeReason,
        Instant createTime,
        Long frozenBy,
        Instant frozenTime,
        long ruleSetId,
        long managementVersion,
        boolean inUse
    ) {
    }

    /** 删除终态事实。 */
    record DeletedRuleSet(long ruleSetId, long version, long deletedBy, Instant deletedTime) { }

    /** 新版本提交后的稳定事实。 */
    record CreatedRuleSet(
        long ruleSetVersionId,
        Long pointVersionId,
        String ruleSetCode,
        int versionNo,
        RuleSetVersionStatus status,
        String contentHash,
        long createdBy,
        Instant createTime
    ) {
    }

    /** 冻结提交后的稳定事实。 */
    record FrozenRuleSet(
        long ruleSetVersionId,
        Long pointVersionId,
        String ruleSetCode,
        int versionNo,
        RuleSetVersionStatus status,
        String contentHash,
        long frozenBy,
        Instant frozenTime
    ) {
    }

    /** 保存不可变结果集合，拒绝缺失或空元素。 */
    private static <T> List<T> immutable(String name, List<T> values) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " 无效");
        }
        return List.copyOf(values);
    }
}
