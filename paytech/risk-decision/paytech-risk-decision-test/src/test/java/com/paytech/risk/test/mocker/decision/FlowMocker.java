package com.paytech.risk.test.mocker.decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.test.builder.decision.FlowBuilder;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.VersionSpec;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mapper.decision.RuleSetTestMapper;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedVersion;

/**
 * 决策流及其关联目录的本地数据 Mocker
 * <li>流程按随机编码登记，HTTP 保存产生的服务端 ID 也按编码清理；Clear 只删除本 Mocker 登记的数据。</li>
 * <li>拒绝原因、旧点版本绑定与变量别名均为本 Mocker 插入；决策点状态被临时改变时在 Clear 中恢复。</li>
 *
 * @author Kenny
 */
@TestComponent
public class FlowMocker implements Clear {

    private final FlowTestMapper mapper;

    private final RuleSetTestMapper ruleSetMapper;

    private final List<String> flowCodes = new ArrayList<>();

    private final List<Long> reasonIds = new ArrayList<>();

    private final List<Long> pointVersionIds = new ArrayList<>();

    private final List<String> variableCodes = new ArrayList<>();

    /** 决策点被临时改变前的状态；null 表示未改变。 */
    private String originalPointStatus;

    public FlowMocker(FlowTestMapper mapper, RuleSetTestMapper ruleSetMapper) {
        this.mapper = mapper;
        this.ruleSetMapper = ruleSetMapper;
    }

    /** 已插入的流程版本及其节点 ID。 */
    public record MockedFlowVersion(long flowVersionId, VersionSpec spec, Map<String, Long> nodeIds) {
        public int versionNo() {
            return spec.versionNo();
        }
    }

    /** 已插入的流程根及其版本，最后一个版本为当前版本。 */
    public record MockedFlow(long flowId, String flowCode, List<MockedFlowVersion> versions, long version,
        boolean deleted, Instant deletedTime, Instant createTime, Instant updateTime) {
        /** 按业务版本号取版本。 */
        public MockedFlowVersion version(int versionNo) {
            return versions.stream().filter(v -> v.versionNo() == versionNo).findFirst().orElseThrow();
        }

        public MockedFlowVersion current() {
            return versions.getLast();
        }

        public FlowStatus status() {
            return deleted ? FlowStatus.DELETED : FlowStatus.CONFIGURABLE;
        }
    }

    /** 已插入的旧点版本，按绑定顺序列出 Binding ID 与规则集版本。 */
    public record MockedLegacyPoint(long pointVersionId, int versionNo, List<Long> bindingIds,
        List<MockedVersion> ruleSetVersions) {
    }

    /** 插入流程根、全部版本及每个版本的节点与连线。 */
    public MockedFlow mock(FlowBuilder builder) {
        long flowId = randomId();
        String code = newFlowCode();
        List<VersionSpec> specs = builder.versionSpecs();
        List<MockedFlowVersion> versions = new ArrayList<>();
        for (VersionSpec spec : specs) {
            long versionId = randomId();
            mapper.insertVersion(versionId, flowId, spec.versionNo(), spec.flowName(), spec.createdBy(),
                spec.changeReason(), spec.createTime(), spec.inputContractVersion());
            versions.add(new MockedFlowVersion(versionId, spec, insertGraph(versionId, spec)));
        }
        VersionSpec head = specs.getLast();
        long version = head.versionNo() + (builder.isDeleted() ? 1L : 0L);
        Instant createTime = specs.getFirst().createTime();
        mapper.insertRoot(flowId, code, versions.getLast().flowVersionId(),
            builder.isDeleted() ? FlowStatus.DELETED : FlowStatus.CONFIGURABLE, version,
            builder.isDeleted() ? FlowBuilder.DELETED_BY : null, builder.deletedTime(), createTime,
            builder.rootUpdateTime());
        return new MockedFlow(flowId, code, List.copyOf(versions), version, builder.isDeleted(), builder.deletedTime(),
            createTime, builder.rootUpdateTime());
    }

    /** 登记一个新的随机流程编码，供 HTTP 保存使用；Clear 时按编码删除其全部数据。 */
    public String newFlowCode() {
        String code = "MOCK_FLOW_" + randomId();
        flowCodes.add(code);
        return code;
    }

    /** 插入给定状态的拒绝原因，返回原因编码。 */
    public String mockReason(DictionaryStatus status) {
        long reasonId = randomId();
        String code = "MOCK_REASON_" + reasonId;
        reasonIds.add(reasonId);
        mapper.insertReason(reasonId, code, status);
        return code;
    }

    /** 插入冻结旧点版本，按给定顺序绑定规则集版本（序号 1..N）。 */
    public MockedLegacyPoint mockLegacyPoint(MockedVersion... ruleSetVersions) {
        int[] sequences = new int[ruleSetVersions.length];
        for (int i = 0; i < sequences.length; i++) {
            sequences[i] = i + 1;
        }
        return mockLegacyPoint(sequences, ruleSetVersions);
    }

    /** 插入冻结旧点版本，绑定使用调用方给出的序号，用于构造顺序断档。 */
    public MockedLegacyPoint mockLegacyPoint(int[] sequences, MockedVersion... ruleSetVersions) {
        long pointVersionId = randomId();
        int versionNo = ThreadLocalRandom.current().nextInt(100_000, 2_000_000_000);
        pointVersionIds.add(pointVersionId);
        ruleSetMapper.insertPointVersion(pointVersionId, RuleSetBuilder.DECISION_POINT_ID, versionNo,
            RuleSetBuilder.sha256("legacy" + pointVersionId));
        List<Long> bindingIds = new ArrayList<>();
        for (int i = 0; i < ruleSetVersions.length; i++) {
            long bindingId = randomId();
            bindingIds.add(bindingId);
            mapper.insertBinding(bindingId, pointVersionId, ruleSetVersions[i].ruleSetVersionId(), sequences[i]);
        }
        return new MockedLegacyPoint(pointVersionId, versionNo, List.copyOf(bindingIds), List.of(ruleSetVersions));
    }

    /** 插入当前合同 consumer_country_code 的系统输入变量别名，返回变量编码。 */
    public String mockInputAlias(String name, boolean flowCondition, VariableStatus status) {
        long variableId = randomId();
        String code = "MOCK_FLOW_ALIAS_" + variableId;
        variableCodes.add(code);
        mapper.insertInputAlias(variableId, code, name, flowCondition, status.getCode());
        return code;
    }

    /** 临时停用种子决策点，Clear 时恢复原状态。 */
    public void deactivatePoint() {
        if (originalPointStatus == null) {
            originalPointStatus = mapper.pointStatus();
        }
        mapper.updatePointStatus(DecisionPointStatus.INACTIVE.getCode());
    }

    /** 根修订号与当前版本号不一致，构造根闭包损坏。 */
    public void corruptRootVersion(MockedFlow flow) {
        mapper.updateRootVersion(flow.flowId(), flow.version() + 5);
    }

    /** 版本输入合同被改写，构造版本归属损坏。 */
    public void corruptVersionContract(MockedFlowVersion version) {
        mapper.updateVersionContract(version.flowVersionId(), RuleSetBuilder.UNREGISTERED_CONTRACT_VERSION);
    }

    /** 连线条件存储内容被改写为不可读格式。 */
    public void corruptCondition(MockedFlowVersion version, String edgeKey) {
        mapper.updateCondition(version.flowVersionId(), edgeKey, "{\"schemaVersion\":3,\"root\":null}");
    }

    /** 删除本 Mocker 插入或登记的全部数据，并恢复决策点状态。 */
    @Override
    public void clear() {
        flowCodes.forEach(mapper::deleteFlowByCode);
        reasonIds.forEach(mapper::deleteReason);
        pointVersionIds.forEach(mapper::deletePointVersion);
        variableCodes.forEach(mapper::deleteVariable);
        if (originalPointStatus != null) {
            mapper.updatePointStatus(originalPointStatus);
        }
        flowCodes.clear();
        reasonIds.clear();
        pointVersionIds.clear();
        variableCodes.clear();
        originalPointStatus = null;
    }

    private Map<String, Long> insertGraph(long versionId, VersionSpec spec) {
        Map<String, Long> nodeIds = new HashMap<>();
        for (NodeSpec node : spec.graph().nodes()) {
            long nodeId = randomId();
            nodeIds.put(node.nodeKey(), nodeId);
            mapper.insertNode(nodeId, versionId, node.nodeKey(), node.type().getCode(), node.name(),
                node.ruleSetVersionId(), node.decision() == null ? null : node.decision().getCode(), node.reasonCode(),
                node.reasonMessage(), node.x(), node.y(), spec.createTime());
        }
        for (EdgeSpec edge : spec.graph().edges()) {
            mapper.insertEdge(randomId(), versionId, edge.edgeKey(), nodeIds.get(edge.sourceNodeKey()),
                nodeIds.get(edge.targetNodeKey()), edge.type().getCode(), edge.priority(), edge.name(),
                edge.conditionJson(), spec.createTime());
        }
        return Map.copyOf(nodeIds);
    }

    /** 15 位正整数 ID，远离 Snowflake 生成的生产 ID 区间。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
