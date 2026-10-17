package com.paytech.risk.test.mocker.decision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.builder.decision.VariableBuilder.MemberSpec;
import com.paytech.risk.test.mapper.decision.VariableTestMapper;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 统一变量及其引用位置的本地数据 Mocker
 * <li>变量编码为 {@code MOCK_VAR_<15 位随机数>_<两位序号>}，同一用例共用随机段，可用作目录关键字只命中本例数据。</li>
 * <li>经接口创建的变量须先通过 {@link #newCode()} 取得编码，使服务端写入的根与成员也纳入清理。</li>
 * <li>规则集引用复用 {@link RuleSetMocker} 插入的最小规则闭包；流程与动作参数行由本 Mocker 插入并删除。</li>
 * <li>Clear 只删除本 Mocker 登记的编码与 ID，并释放用例持有的变量资源锁。</li>
 *
 * @author Kenny
 */
@TestComponent
public class VariableMocker implements Clear {

    /** 变量资源锁 Key 前缀，与生产写入口共用。 */
    private static final String LOCK_PREFIX = "decision:variable:";

    private final VariableTestMapper mapper;

    private final RedissonClient redisson;

    private final List<String> codes = new ArrayList<>();

    private final List<Long> operandIds = new ArrayList<>();

    private final List<Long> parameterIds = new ArrayList<>();

    private final List<Long> flowIds = new ArrayList<>();

    private final List<Long> flowVersionIds = new ArrayList<>();

    private final List<Long> edgeIds = new ArrayList<>();

    private final List<RLock> heldLocks = new ArrayList<>();

    private String token;

    private int sequence;

    private long nextEdgeId;

    public VariableMocker(VariableTestMapper mapper, RedissonClient redisson) {
        this.mapper = mapper;
        this.redisson = redisson;
    }

    /** 已插入的变量根及其规格。 */
    public record MockedVariable(long variableId, String code, VariableBuilder spec) {
        public long version() {
            return spec.version();
        }
    }

    /** 期望的一条引用位置；phase 与 scanId 决定扫描顺序，不出现在响应中。 */
    public record MockedReference(int phase, long scanId, String consumerType, long rootId, Long versionId,
        Integer versionNo, String code, String name, String scope, String location) {
    }

    /** 本例变量编码的公共随机段，可作为目录关键字。 */
    public String token() {
        if (token == null) {
            token = "MOCK_VAR_" + randomId();
        }
        return token;
    }

    /** 分配并登记一个本例变量编码，按序号递增保持字典序。 */
    public String newCode() {
        String code = "%s_%02d".formatted(token(), ++sequence);
        codes.add(code);
        return code;
    }

    /** 插入变量根及全部成员，成员顺序号从 1 开始。 */
    public MockedVariable mock(VariableBuilder spec) {
        String code = newCode();
        long variableId = randomId();
        VariableBuilder frozen = spec.copy();
        mapper.insertVariable(variableId, code, frozen);
        List<MemberSpec> members = frozen.members();
        for (int index = 0; index < members.size(); index++) {
            mapper.insertMember(randomId(), code, frozen, members.get(index), index + 1);
        }
        return new MockedVariable(variableId, code, frozen);
    }

    /** 覆盖变量根的一列，构造持久化损坏或特殊状态。 */
    public void corrupt(MockedVariable variable, String column, Object value) {
        mapper.updateColumn(variable.code(), column, value);
    }

    /** 覆盖全部成员的规范键，构造成员完整性损坏。 */
    public void corruptMemberKeys(MockedVariable variable) {
        mapper.updateMemberKey(variable.code(), "corrupted");
    }

    /** 让规则集指定版本的左操作数引用变量。 */
    public MockedReference ruleOperandReference(MockedRuleSet ruleSet, int versionNo, boolean ruleSetDeleted, String code) {
        return ruleOperand(ruleSet, versionNo, ruleSetDeleted, code, null, 0);
    }

    /** 让规则集指定版本的左操作数以币种变量引用变量。 */
    public MockedReference ruleCurrencyReference(MockedRuleSet ruleSet, int versionNo, String code) {
        return ruleOperand(ruleSet, versionNo, false, null, code, 1);
    }

    /** 在规则集指定版本的终止动作上插入引用变量的动作参数。 */
    public MockedReference ruleParameterReference(MockedRuleSet ruleSet, int versionNo, String code) {
        long parameterId = randomId();
        parameterIds.add(parameterId);
        long versionId = ruleSet.version(versionNo).ruleSetVersionId();
        Map<String, Object> action = mapper.insertActionParameter(parameterId, versionId, code);
        return new MockedReference(2, parameterId, "RULE_SET", ruleSet.ruleSetId(), versionId, versionNo,
            ruleSet.ruleSetCode(), ruleSet.version(versionNo).spec().ruleSetName(), scope(ruleSet, versionNo, false),
            action.get("rule_code") + "/action:" + action.get("rule_action_id") + "/parameter:" + parameterId);
    }

    /** 变量依赖引用：消费者变量以指定字段引用目标变量。 */
    public static MockedReference variableReference(MockedVariable consumer, String location) {
        return new MockedReference(3, consumer.variableId(), "VARIABLE", consumer.variableId(), null, null,
            consumer.code(), consumer.spec().variableName(), "CURRENT", location);
    }

    /**
     * 插入两版本流程，在指定版本放一条两处引用变量的条件连线
     * <li>条件为 AND(变量非空, INPUT_COUNTRY_CODE 等于变量)，命中路径按条件树顺序聚合在一个位置中。</li>
     */
    public MockedReference flowReference(String code, boolean inHistoryVersion, FlowStatus status) {
        long flowId = randomId();
        long[] versionIds = {randomId(), randomId()};
        String flowCode = "MOCK_FLOW_" + flowId;
        flowIds.add(flowId);
        mapper.insertFlow(flowId, RuleSetBuilder.DECISION_POINT_ID, flowCode, versionIds[1], status);
        for (int index = 0; index < 2; index++) {
            flowVersionIds.add(versionIds[index]);
            mapper.insertFlowVersion(versionIds[index], flowId, index + 1, "Mock flow v" + (index + 1),
                RuleSetBuilder.LEGACY_CONTRACT_VERSION);
        }
        int versionNo = inHistoryVersion ? 1 : 2;
        long edgeId = nextEdgeId();
        String edgeKey = "edge_" + edgeId;
        mapper.insertFlowEdge(edgeId, versionIds[versionNo - 1], edgeKey, FlowEdgeType.CONDITIONAL, 1, condition(code));
        boolean current = !inHistoryVersion && status != FlowStatus.DELETED;
        return new MockedReference(4, edgeId, "DECISION_FLOW", flowId, versionIds[versionNo - 1], versionNo, flowCode,
            "Mock flow v" + versionNo, current ? "CURRENT" : "HISTORY",
            edgeKey + ":root.children[0].operands[0].variableCode,root.children[1].operands[1].variableCode");
    }

    /** 插入一条不引用目标变量的条件连线：会被扫描计数，但不产生引用位置。 */
    public void flowEdgeWithoutReference() {
        long flowId = randomId();
        long versionId = randomId();
        flowIds.add(flowId);
        flowVersionIds.add(versionId);
        mapper.insertFlow(flowId, RuleSetBuilder.DECISION_POINT_ID, "MOCK_FLOW_" + flowId, versionId, FlowStatus.CONFIGURABLE);
        mapper.insertFlowVersion(versionId, flowId, 1, "Mock flow v1", RuleSetBuilder.LEGACY_CONTRACT_VERSION);
        mapper.insertFlowEdge(nextEdgeId(), versionId, "edge_other", FlowEdgeType.CONDITIONAL, 1,
            condition("INPUT_CURRENCY"));
    }

    /** 在一个流程版本中插入多条不引用目标变量的条件连线，ID 连续递增，用于耗尽单页扫描预算。 */
    public void flowEdgesWithoutReference(int count) {
        long flowId = randomId();
        long versionId = randomId();
        flowIds.add(flowId);
        flowVersionIds.add(versionId);
        mapper.insertFlow(flowId, RuleSetBuilder.DECISION_POINT_ID, "MOCK_FLOW_" + flowId, versionId, FlowStatus.CONFIGURABLE);
        mapper.insertFlowVersion(versionId, flowId, 1, "Mock flow v1", RuleSetBuilder.LEGACY_CONTRACT_VERSION);
        long first = nextEdgeId();
        nextEdgeId += count - 1;
        mapper.insertFlowEdges(first, versionId, count, condition("INPUT_CURRENCY"));
    }

    /** 插入一条条件 JSON 损坏的连线；流程扫描逐条解析，任何损坏都会失败关闭。 */
    public void corruptedFlowEdge() {
        long flowId = randomId();
        long versionId = randomId();
        flowIds.add(flowId);
        flowVersionIds.add(versionId);
        mapper.insertFlow(flowId, RuleSetBuilder.DECISION_POINT_ID, "MOCK_FLOW_" + flowId, versionId, FlowStatus.CONFIGURABLE);
        mapper.insertFlowVersion(versionId, flowId, 1, "Mock flow v1", RuleSetBuilder.LEGACY_CONTRACT_VERSION);
        mapper.insertFlowEdge(nextEdgeId(), versionId, "edge_broken", FlowEdgeType.CONDITIONAL, 1, "{\"schemaVersion\":9}");
    }

    /** 插入父级节点不存在的引用操作数，构造引用关系损坏。 */
    public void orphanOperandReference(String code) {
        long operandId = randomId();
        operandIds.add(operandId);
        mapper.insertOrphanOperand(operandId, code);
    }

    /** 由测试线程持有变量资源锁，模拟另一写入者正在处理同一变量。 */
    public void holdLock(String code) {
        RLock lock = redisson.getLock(LOCK_PREFIX + code);
        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("变量资源锁已被占用：" + code);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
        heldLocks.add(lock);
    }

    /** 释放持有的锁，删除本 Mocker 登记的全部数据；删除失败时保留登记，下一次清理继续重试。 */
    @Override
    public void clear() {
        heldLocks.forEach(RLock::forceUnlock);
        heldLocks.clear();
        mapper.deleteAll(edgeIds, flowVersionIds, flowIds, parameterIds, operandIds, codes);
        edgeIds.clear();
        flowVersionIds.clear();
        flowIds.clear();
        parameterIds.clear();
        operandIds.clear();
        codes.clear();
        token = null;
        sequence = 0;
        nextEdgeId = 0;
    }

    private MockedReference ruleOperand(MockedRuleSet ruleSet, int versionNo, boolean ruleSetDeleted, String code,
        String currencyCode, int phase) {
        long versionId = ruleSet.version(versionNo).ruleSetVersionId();
        Map<String, Object> operand = mapper.referenceFromRuleOperand(versionId, code, currencyCode);
        return new MockedReference(phase, ((Number) operand.get("operand_id")).longValue(), "RULE_SET", ruleSet.ruleSetId(),
            versionId, versionNo, ruleSet.ruleSetCode(), ruleSet.version(versionNo).spec().ruleSetName(),
            scope(ruleSet, versionNo, ruleSetDeleted), operand.get("rule_code") + "/node:" + operand.get("node_id") + "/LEFT:1");
    }

    /** 根未删除且该版本为当前指针时为 CURRENT，否则为 HISTORY。 */
    private static String scope(MockedRuleSet ruleSet, int versionNo, boolean ruleSetDeleted) {
        boolean current = !ruleSetDeleted && ruleSet.version(versionNo).ruleSetVersionId() == ruleSet.currentVersionId();
        return current ? "CURRENT" : "HISTORY";
    }

    /** 新格式条件：AND(变量 IS_NOT_NULL, INPUT_COUNTRY_CODE EQ 变量)。 */
    private static String condition(String code) {
        return "{\"schemaVersion\":2,\"root\":{\"kind\":\"GROUP\",\"operator\":\"AND\",\"children\":["
            + "{\"kind\":\"PREDICATE\",\"operator\":\"IS_NOT_NULL\",\"operands\":[" + operand("LEFT", code) + "]},"
            + "{\"kind\":\"PREDICATE\",\"operator\":\"EQ\",\"operands\":[" + operand("LEFT", "INPUT_COUNTRY_CODE") + ","
            + operand("RIGHT", code) + "]}]}}";
    }

    private static String operand(String role, String code) {
        return "{\"operandRole\":\"" + role + "\",\"valueSequence\":1,\"sourceType\":\"VARIABLE\",\"variableCode\":\"" + code
            + "\",\"fieldCode\":null,\"valueType\":\"STRING\",\"literalValue\":null,\"numericUnit\":null,"
            + "\"currencyCode\":null,\"currencyVariableCode\":null}";
    }

    /** 连线 ID 在单例内递增，使扫描顺序可预期。 */
    private long nextEdgeId() {
        if (nextEdgeId == 0) {
            nextEdgeId = randomId();
        }
        long id = nextEdgeId++;
        edgeIds.add(id);
        return id;
    }

    /** 15 位正整数 ID，远离 Snowflake 生成的生产 ID 区间。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
