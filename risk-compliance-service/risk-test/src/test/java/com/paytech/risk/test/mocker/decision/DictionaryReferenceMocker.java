package com.paytech.risk.test.mocker.decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mapper.decision.DictionaryReferenceTestMapper;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedVersion;

/**
 * 字典引用来源的本地数据 Mocker
 * <li>规则集根与版本复用 {@link RuleSetMocker}；本类只追加引用字典的规则、动作、参数，以及决策流根、版本和结束节点。</li>
 * <li>规则与动作使用种子动作版本：ALLOW_CHECKOUT 240001、DENY_CHECKOUT 240003、ADD_RISK_TAG 240005（参数 SUBJECT_ID 2400051、TAG
 * 2400052）。</li>
 * <li>Clear 只按本类登记的 ID 删除；规则集及其最小规则仍由 RuleSetMocker 清理。</li>
 *
 * @author Kenny
 */
@TestComponent
public class DictionaryReferenceMocker implements Clear {
    public static final long ALLOW_ACTION_VERSION_ID = 240001L;

    public static final long DENY_ACTION_VERSION_ID = 240003L;

    public static final long ADD_TAG_ACTION_VERSION_ID = 240005L;

    static final long SUBJECT_PARAMETER_ID = 2400051L;

    static final long TAG_PARAMETER_ID = 2400052L;

    /** 标签动作默认的工程主体绑定。 */
    public static final String TARGET_BINDING = "CURRENT_CLIENT_V1";

    static final Instant FLOW_TIME = Instant.parse("2026-06-07T08:09:10.111111Z");

    private final RuleSetMocker ruleSets;

    private final DictionaryReferenceTestMapper mapper;

    private final List<Long> parameterIds = new ArrayList<>();

    private final List<Long> ruleActionIds = new ArrayList<>();

    private final List<Long> ruleIds = new ArrayList<>();

    private final List<Long> nodeIds = new ArrayList<>();

    private final List<Long> flowVersionIds = new ArrayList<>();

    private final List<Long> flowIds = new ArrayList<>();

    /** 每个规则集版本内已分配的规则序号，保证规则编码与优先级唯一。 */
    private final Map<Long, Integer> ruleNumbers = new HashMap<>();

    /** 每条规则已分配的动作序号。 */
    private final Map<Long, Integer> actionNumbers = new HashMap<>();

    public DictionaryReferenceMocker(RuleSetMocker ruleSets, DictionaryReferenceTestMapper mapper) {
        this.ruleSets = ruleSets;
        this.mapper = mapper;
    }

    /** 已插入的规则集及其整体管理状态。 */
    public record ReferencedRuleSet(MockedRuleSet ruleSet, RuleSetManagementStatus status) {
        public MockedVersion version(int versionNo) {
            return ruleSet.version(versionNo);
        }
    }

    /** 已插入、可承载字典引用的规则。 */
    public record MockedRule(ReferencedRuleSet ruleSet, MockedVersion version, long ruleId, String ruleCode,
        String ruleName) {
        /** 该规则所在版本是否为规则集根的当前指针。 */
        public boolean isCurrentConfiguration() {
            return version.ruleSetVersionId() == ruleSet.ruleSet().currentVersionId();
        }
    }

    /** 规则上的一个动作；标签引用以动作序号区分同一规则内的多个保存项。 */
    public record MockedRuleAction(MockedRule rule, long ruleActionId, int sequenceNo, String targetBindingCode) {
    }

    /** 已插入的决策流版本。 */
    public record MockedFlowVersion(long flowVersionId, int versionNo, String flowName) {
    }

    /** 已插入的决策流根及其版本，最后一个版本为当前版本。 */
    public record MockedFlow(long flowId, String flowCode, FlowStatus status, List<MockedFlowVersion> versions) {
        public MockedFlowVersion version(int versionNo) {
            return versions.get(versionNo - 1);
        }

        public long currentVersionId() {
            return versions.getLast().flowVersionId();
        }
    }

    /** 决策流版本内的结束节点。 */
    public record MockedFlowEnd(MockedFlow flow, MockedFlowVersion version, long nodeId, String nodeKey,
        String nodeName) {
        public boolean isCurrentConfiguration() {
            return version.flowVersionId() == flow.currentVersionId();
        }
    }

    /** 通过 RuleSetMocker 插入规则集根及版本，并记录其管理状态。 */
    public ReferencedRuleSet ruleSet(RuleSetBuilder builder) {
        return new ReferencedRuleSet(ruleSets.mock(builder),
            builder.isDeleted() ? RuleSetManagementStatus.DELETED : RuleSetManagementStatus.CONFIGURABLE);
    }

    /** 在指定版本内追加一条规则，原因编码可为空；文案随原因一同保存。 */
    public MockedRule rule(ReferencedRuleSet ruleSet, int versionNo, String reasonCode) {
        MockedVersion version = ruleSet.version(versionNo);
        int number = ruleNumbers.merge(version.ruleSetVersionId(), 1, Integer::sum);
        long ruleId = randomId();
        String ruleCode = "MOCK_REF_RULE_" + number;
        String ruleName = "Reference rule " + number + " of v" + versionNo;
        ruleIds.add(ruleId);
        mapper.insertRule(ruleId, version.ruleSetVersionId(), ruleCode, ruleName, 100 + number, reasonCode,
            reasonCode == null ? null : "Saved message " + number);
        return new MockedRule(ruleSet, version, ruleId, ruleCode, ruleName);
    }

    /** 规则命中后拒绝结账（终态 DENY），使规则上的原因构成拒绝原因引用。 */
    public MockedRuleAction deny(MockedRule rule) {
        return action(rule, DENY_ACTION_VERSION_ID, null);
    }

    /** 规则命中后放行（终态 ALLOW），规则上的原因不构成拒绝原因引用。 */
    public MockedRuleAction allow(MockedRule rule) {
        return action(rule, ALLOW_ACTION_VERSION_ID, null);
    }

    /** 追加一个 ADD_RISK_TAG 动作，TAG 参数按顺序保存给定字面值；绑定为 null 时构造损坏坐标。 */
    public MockedRuleAction addTag(MockedRule rule, String targetBindingCode, String... tagCodes) {
        MockedRuleAction action = action(rule, ADD_TAG_ACTION_VERSION_ID, targetBindingCode);
        for (int i = 0; i < tagCodes.length; i++) {
            parameter(action, TAG_PARAMETER_ID, i + 1, tagCodes[i]);
        }
        return action;
    }

    /** 在标签动作上保存 SUBJECT_ID 字面参数；参数编码不是 TAG，不构成标签引用。 */
    public void subjectParameter(MockedRuleAction action, String value) {
        parameter(action, SUBJECT_PARAMETER_ID, 1, value);
    }

    /** 插入决策流根及版本 1..versionCount，最后一个为当前版本。 */
    public MockedFlow flow(int versionCount, FlowStatus status) {
        long flowId = randomId();
        String flowCode = "MOCK_FLOW_" + flowId;
        List<MockedFlowVersion> versions = new ArrayList<>();
        for (int versionNo = 1; versionNo <= versionCount; versionNo++) {
            long versionId = randomId();
            String flowName = "Mock flow v" + versionNo;
            flowVersionIds.add(versionId);
            mapper.insertFlowVersion(versionId, flowId, versionNo, flowName, FLOW_TIME);
            versions.add(new MockedFlowVersion(versionId, versionNo, flowName));
        }
        flowIds.add(flowId);
        mapper.insertFlow(flowId, RuleSetBuilder.DECISION_POINT_ID, flowCode, versions.getLast().flowVersionId(),
            status, FLOW_TIME);
        return new MockedFlow(flowId, flowCode, status, List.copyOf(versions));
    }

    /** 在流程版本内插入结束节点，保存给定决定及原因。 */
    public MockedFlowEnd end(MockedFlow flow, int versionNo, DecisionValue decision, String reasonCode) {
        MockedFlowVersion version = flow.version(versionNo);
        long nodeId = randomId();
        String nodeKey = "end_" + nodeId;
        String nodeName = "End " + decision.getCode() + " v" + versionNo;
        nodeIds.add(nodeId);
        mapper.insertEndNode(nodeId, version.flowVersionId(), nodeKey, nodeName, decision, reasonCode,
            reasonCode == null ? null : "Flow message");
        return new MockedFlowEnd(flow, version, nodeId, nodeKey, nodeName);
    }

    /** 删除本 Mocker 登记的全部引用来源；删除失败时保留登记，下一次清理继续重试。 */
    @Override
    public void clear() {
        mapper.deleteAll(parameterIds, ruleActionIds, ruleIds, nodeIds, flowVersionIds, flowIds);
        parameterIds.clear();
        ruleActionIds.clear();
        ruleIds.clear();
        nodeIds.clear();
        flowVersionIds.clear();
        flowIds.clear();
        ruleNumbers.clear();
        actionNumbers.clear();
    }

    private MockedRuleAction action(MockedRule rule, long actionVersionId, String targetBindingCode) {
        int sequenceNo = actionNumbers.merge(rule.ruleId(), 1, Integer::sum);
        long ruleActionId = randomId();
        ruleActionIds.add(ruleActionId);
        mapper.insertRuleAction(ruleActionId, rule.ruleId(), actionVersionId, sequenceNo, targetBindingCode);
        return new MockedRuleAction(rule, ruleActionId, sequenceNo, targetBindingCode);
    }

    private void parameter(MockedRuleAction action, long definitionId, int valueSequence, String value) {
        long parameterId = randomId();
        parameterIds.add(parameterId);
        mapper.insertStringParameter(parameterId, action.ruleActionId(), definitionId, valueSequence,
            OperandSource.LITERAL, value);
    }

    /** 15 位正整数，远离 Snowflake 生成的生产 ID 区间。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
