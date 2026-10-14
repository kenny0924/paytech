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

    /** 配置能力及已读取系统变量的展示投影。 */
    record RuleAuthoringView(DecisionPointAuthoringDescriptor descriptor,
        List<com.paytech.risk.decision.domain.variable.RiskVariable.Entry> systemDefinitions) {
        /** 纯合同测试可显式传入空的数据库投影。 */
        public RuleAuthoringView(DecisionPointAuthoringDescriptor descriptor) { this(descriptor, List.of()); }
        public RuleAuthoringView {
            Objects.requireNonNull(descriptor, "descriptor");
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
