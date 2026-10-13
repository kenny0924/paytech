package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mapper.decision.RuleSetTestMapper;

/**
 * 规则集持久化事实的数据库 Checker
 * <li>快照包含根、全部版本及每个版本的规则、节点、操作数、动作与参数行，用于核对写入结果或拒绝后完全未变。</li>
 * <li>期望值来自用例约定（请求内容、响应中服务端生成的 ID 与时间），不调用被测算法计算。</li>
 *
 * @author Kenny
 */
@TestComponent
public class RuleSetStoreChecker {

    /** 新版本固定使用的输入合同。 */
    static final String INPUT_CONTRACT = "ORDER_PREPARATION";

    private final RuleSetTestMapper mapper;

    public RuleSetStoreChecker(RuleSetTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 某个规则集编码下的完整持久化事实；版本按业务版本号排序，闭包按版本 ID 索引。 */
    public record Snapshot(String ruleSetCode, Map<String, Object> root, List<Map<String, Object>> versions,
        Map<Object, List<Map<String, Object>>> closures) {
        /** 按业务版本号取版本行。 */
        public Map<String, Object> version(int versionNo) {
            return versions.stream().filter(row -> ((Number)row.get("version_no")).intValue() == versionNo).findFirst()
                .orElseThrow(() -> new AssertionError("缺少版本 v" + versionNo));
        }
    }

    /** 期望的一条规则及其按生成顺序（先序）排列的节点和按顺序排列的动作。 */
    public record ExpectedRule(String ruleCode, String ruleName, int priorityNo, boolean enabled, String reasonCode,
        String reasonMessage, List<ExpectedNode> nodes, List<ExpectedAction> actions) {
    }

    /** 期望的节点；parent 为先序列表中父节点下标，根为 -1。 */
    public record ExpectedNode(int parent, String nodeType, String groupOperator, String comparisonOperator,
        int sequenceNo, List<ExpectedOperand> operands) {
    }

    /** 期望的操作数；字面量按值类型写入对应列。 */
    public record ExpectedOperand(String role, int valueSequence, String sourceType, String valueType,
        String variableCode, String stringValue, BigDecimal numberValue, String numericUnit) {
    }

    /** 期望的动作绑定及其参数行。 */
    public record ExpectedAction(long actionVersionId, int sequenceNo, String targetBindingCode,
        List<ExpectedParameter> parameters) {
    }

    /** 期望的动作参数行。 */
    public record ExpectedParameter(long parameterDefinitionId, int valueSequence, String sourceType,
        String variableCode, Long durationValue, String stringValue) {
    }

    /** 读取某编码下的完整持久化事实。 */
    public Snapshot snapshot(String ruleSetCode) {
        List<Map<String, Object>> versions = mapper.selectVersions(RuleSetBuilder.DECISION_POINT_ID, ruleSetCode);
        Map<Object, List<Map<String, Object>>> closures = new LinkedHashMap<>();
        for (Map<String, Object> version : versions) {
            closures.put(version.get("rule_set_version_id"), closure(((Number)version.get("rule_set_version_id"))
                .longValue()));
        }
        return new Snapshot(ruleSetCode, mapper.selectRuleSet(RuleSetBuilder.DECISION_POINT_ID, ruleSetCode),
            versions, closures);
    }

    /** 被拒绝的命令不得改变该编码下的任何持久化事实。 */
    public void assertUnchanged(Snapshot before) {
        Snapshot after = snapshot(before.ruleSetCode());
        assertAll("规则集 " + before.ruleSetCode() + " 不得被改写",
            () -> assertEquals(before.root(), after.root(), "管理根"),
            () -> assertEquals(before.versions(), after.versions(), "版本"),
            () -> assertEquals(before.closures(), after.closures(), "规则闭包"));
    }

    /** 被拒绝的首次保存不得留下任何根或版本。 */
    public void assertNothingWritten(String ruleSetCode) {
        Snapshot after = snapshot(ruleSetCode);
        assertAll("规则集 " + ruleSetCode + " 不得被创建", () -> assertNull(after.root(), "管理根"),
            () -> assertTrue(after.versions().isEmpty(), "版本"));
    }

    /** 首次保存创建的管理根：版本 1，当前指针指向新版本，创建与更新时间均为命令时间。 */
    public void assertCreatedRoot(Snapshot after, long versionId, Instant commandTime) {
        Map<String, Object> root = after.root();
        assertNotNull(root, "缺少管理根");
        assertAll("新管理根",
            () -> assertTrue(((Number)root.get("rule_set_id")).longValue() > 0, "rule_set_id"),
            () -> assertEquals(RuleSetBuilder.DECISION_POINT_ID, ((Number)root.get("decision_point_id")).longValue()),
            () -> assertEquals(versionId, ((Number)root.get("current_rule_set_version_id")).longValue()),
            () -> assertEquals(RuleSetManagementStatus.CONFIGURABLE.getCode(), root.get("status")),
            () -> assertEquals(1L, ((Number)root.get("version")).longValue()),
            () -> assertNull(root.get("deleted_by")), () -> assertNull(root.get("deleted_time")),
            () -> assertNull(root.get("delete_reason_code")), () -> assertNull(root.get("delete_trigger_id")),
            () -> assertEquals(commandTime, instant(root.get("create_time"))),
            () -> assertEquals(commandTime, instant(root.get("update_time"))));
    }

    /** 已有根推进到新版本：管理版本加一、指针指向新版本、更新时间为命令时间，其余不变。 */
    public void assertAdvancedRoot(Snapshot before, Snapshot after, long currentVersionId, Instant commandTime) {
        Map<String, Object> expected = new LinkedHashMap<>(before.root());
        expected.put("current_rule_set_version_id", currentVersionId);
        expected.put("version", ((Number)before.root().get("version")).longValue() + 1);
        expected.put("update_time", commandTime);
        assertEquals(normalize(expected), normalize(after.root()), "推进后的管理根");
    }

    /** 新保存的统一变量 DRAFT 版本头。 */
    public void assertDraftVersion(Snapshot after, int versionNo, long versionId, String ruleSetName,
        String changeReason, String contentHash, long createdBy, Instant commandTime) {
        Map<String, Object> row = after.version(versionNo);
        assertAll("新版本 v" + versionNo,
            () -> assertEquals(versionId, ((Number)row.get("rule_set_version_id")).longValue()),
            () -> assertEquals(RuleSetBuilder.DECISION_POINT_ID, ((Number)row.get("decision_point_id")).longValue()),
            () -> assertEquals(INPUT_CONTRACT, row.get("input_contract_code")),
            () -> assertEquals(4, ((Number)row.get("input_contract_version")).intValue()),
            () -> assertEquals(after.ruleSetCode(), row.get("rule_set_code")),
            () -> assertEquals(ruleSetName, row.get("rule_set_name")),
            () -> assertEquals(RuleSetVersionStatus.DRAFT.getCode(), row.get("status")),
            () -> assertEquals(contentHash, row.get("content_hash")),
            () -> assertEquals(RuleSetBuilder.VARIABLE_FORMAT, row.get("content_format")),
            () -> assertEquals(createdBy, ((Number)row.get("created_by")).longValue()),
            () -> assertEquals(changeReason, row.get("change_reason")),
            () -> assertNull(row.get("frozen_by")), () -> assertNull(row.get("frozen_time")),
            () -> assertEquals(commandTime, instant(row.get("create_time"))),
            () -> assertEquals(commandTime, instant(row.get("update_time"))));
    }

    /** 版本行仅指定列被改为给定值，其余列与之前完全一致。 */
    public void assertVersionChanged(Snapshot before, Snapshot after, int versionNo, Map<String, Object> changes) {
        Map<String, Object> expected = new LinkedHashMap<>(before.version(versionNo));
        expected.putAll(changes);
        assertEquals(normalize(expected), normalize(after.version(versionNo)), "版本 v" + versionNo);
        assertEquals(before.closures().get(before.version(versionNo).get("rule_set_version_id")),
            after.closures().get(after.version(versionNo).get("rule_set_version_id")), "v" + versionNo + " 规则闭包不变");
    }

    /** 根行仅指定列被改为给定值，其余列与之前完全一致。 */
    public void assertRootChanged(Snapshot before, Snapshot after, Map<String, Object> changes) {
        Map<String, Object> expected = new LinkedHashMap<>(before.root());
        expected.putAll(changes);
        assertEquals(normalize(expected), normalize(after.root()), "管理根");
    }

    /** 新版本的完整规则闭包与期望结构一致，所有行时间为命令时间。 */
    public void assertClosure(long versionId, Instant commandTime, ExpectedRule... expected) {
        List<Map<String, Object>> rules = mapper.selectRules(versionId);
        assertEquals(expected.length, rules.size(), "规则数量");
        Instant time = commandTime;
        for (int i = 0; i < expected.length; i++) {
            ExpectedRule rule = expected[i];
            Map<String, Object> row = rules.get(i);
            long ruleId = ((Number)row.get("rule_id")).longValue();
            assertAll("规则 " + rule.ruleCode(),
                () -> assertEquals(rule.ruleCode(), row.get("rule_code")),
                () -> assertEquals(rule.ruleName(), row.get("rule_name")),
                () -> assertEquals(rule.priorityNo(), ((Number)row.get("priority_no")).intValue()),
                () -> assertEquals(rule.enabled(), row.get("enabled_flag")),
                () -> assertEquals(rule.reasonCode(), row.get("reason_code")),
                () -> assertEquals(rule.reasonMessage(), row.get("reason_message")),
                () -> assertEquals(time, instant(row.get("create_time"))),
                () -> assertEquals(time, instant(row.get("update_time"))));
            assertNodes(rule, mapper.selectNodes(ruleId), time);
            assertActions(rule, mapper.selectRuleActions(ruleId), time);
        }
    }

    /** INPUT_COUNTRY_CODE 等当前变量定义行，作为详情展示投影的期望来源。 */
    public Map<String, Object> variable(String variableCode) {
        return mapper.selectVariable(variableCode);
    }

    private void assertNodes(ExpectedRule rule, List<Map<String, Object>> nodes, Instant time) {
        assertEquals(rule.nodes().size(), nodes.size(), rule.ruleCode() + " 节点数量");
        for (int i = 0; i < nodes.size(); i++) {
            ExpectedNode expected = rule.nodes().get(i);
            Map<String, Object> row = nodes.get(i);
            Object parentId = expected.parent() < 0 ? null : nodes.get(expected.parent()).get("node_id");
            assertAll(rule.ruleCode() + " 节点 " + i,
                () -> assertEquals(parentId, row.get("parent_node_id")),
                () -> assertEquals(expected.nodeType(), row.get("node_type")),
                () -> assertEquals(expected.groupOperator(), row.get("group_operator")),
                () -> assertEquals(expected.comparisonOperator(), row.get("comparison_operator")),
                () -> assertEquals(expected.sequenceNo(), ((Number)row.get("sequence_no")).intValue()),
                () -> assertEquals(time, instant(row.get("create_time"))),
                () -> assertEquals(time, instant(row.get("update_time"))));
            List<Map<String, Object>> operands = mapper.selectOperands(((Number)row.get("node_id")).longValue());
            assertEquals(expected.operands().size(), operands.size(), rule.ruleCode() + " 节点 " + i + " 操作数数量");
            for (int j = 0; j < operands.size(); j++) {
                assertOperand(expected.operands().get(j), operands.get(j), time);
            }
        }
    }

    private static void assertOperand(ExpectedOperand expected, Map<String, Object> row, Instant time) {
        assertAll("操作数 " + expected.role() + expected.valueSequence(),
            () -> assertEquals(expected.role(), row.get("operand_role")),
            () -> assertEquals(expected.valueSequence(), ((Number)row.get("value_sequence")).intValue()),
            () -> assertEquals(expected.sourceType(), row.get("source_type")),
            () -> assertNull(row.get("feature_version_id")), () -> assertNull(row.get("field_code")),
            () -> assertEquals(expected.valueType(), row.get("value_type")),
            () -> assertEquals(expected.stringValue(), row.get("string_value")),
            () -> assertEquals(expected.numberValue(), row.get("number_value")),
            () -> assertNull(row.get("boolean_value")), () -> assertNull(row.get("datetime_value")),
            () -> assertNull(row.get("duration_value")),
            () -> assertEquals(expected.variableCode(), row.get("variable_code")),
            () -> assertEquals(expected.numericUnit(), row.get("numeric_unit")),
            () -> assertNull(row.get("currency_code")), () -> assertNull(row.get("currency_variable_code")),
            () -> assertEquals(time, instant(row.get("create_time"))),
                () -> assertEquals(time, instant(row.get("update_time"))));
    }

    private void assertActions(ExpectedRule rule, List<Map<String, Object>> actions, Instant time) {
        assertEquals(rule.actions().size(), actions.size(), rule.ruleCode() + " 动作数量");
        for (int i = 0; i < actions.size(); i++) {
            ExpectedAction expected = rule.actions().get(i);
            Map<String, Object> row = actions.get(i);
            assertAll(rule.ruleCode() + " 动作 " + i,
                () -> assertEquals(expected.actionVersionId(), ((Number)row.get("action_version_id")).longValue()),
                () -> assertEquals(expected.sequenceNo(), ((Number)row.get("sequence_no")).intValue()),
                () -> assertEquals(expected.targetBindingCode(), row.get("target_binding_code")),
                () -> assertEquals(time, instant(row.get("create_time"))));
            List<Map<String, Object>> parameters =
                mapper.selectActionParameters(((Number)row.get("rule_action_id")).longValue());
            assertEquals(expected.parameters().size(), parameters.size(), rule.ruleCode() + " 动作 " + i + " 参数数量");
            for (int j = 0; j < parameters.size(); j++) {
                ExpectedParameter parameter = expected.parameters().get(j);
                Map<String, Object> actual = parameters.get(j);
                assertAll("参数 " + parameter.parameterDefinitionId(),
                    () -> assertEquals(parameter.parameterDefinitionId(),
                        ((Number)actual.get("parameter_definition_id")).longValue()),
                    () -> assertEquals(parameter.valueSequence(), ((Number)actual.get("value_sequence")).intValue()),
                    () -> assertEquals(parameter.sourceType(), actual.get("source_type")),
                    () -> assertEquals(parameter.variableCode(), actual.get("variable_code")),
                    () -> assertNull(actual.get("field_code")), () -> assertNull(actual.get("feature_version_id")),
                    () -> assertEquals(parameter.stringValue(), actual.get("string_value")),
                    () -> assertNull(actual.get("number_value")), () -> assertNull(actual.get("boolean_value")),
                    () -> assertNull(actual.get("datetime_value")),
                    () -> assertEquals(parameter.durationValue(), actual.get("duration_value") == null ? null
                        : ((Number)actual.get("duration_value")).longValue()),
                    () -> assertEquals(time, instant(actual.get("create_time"))));
            }
        }
    }

    /** 读取一个版本的完整规则闭包行，供快照比较。 */
    private List<Map<String, Object>> closure(long versionId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> rule : mapper.selectRules(versionId)) {
            rows.add(rule);
            long ruleId = ((Number)rule.get("rule_id")).longValue();
            for (Map<String, Object> node : mapper.selectNodes(ruleId)) {
                rows.add(node);
                rows.addAll(mapper.selectOperands(((Number)node.get("node_id")).longValue()));
            }
            for (Map<String, Object> action : mapper.selectRuleActions(ruleId)) {
                rows.add(action);
                rows.addAll(mapper.selectActionParameters(((Number)action.get("rule_action_id")).longValue()));
            }
        }
        return rows;
    }

    /** 数值列统一为 long，时间统一为 Instant，避免驱动类型差异影响比较。 */
    private static Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> result.put(key.toLowerCase(java.util.Locale.ROOT),
            value instanceof Number number && !(value instanceof BigDecimal) ? (Object)number.longValue()
                : value instanceof Timestamp || value instanceof LocalDateTime ? instant(value) : value));
        return result;
    }

    /** 数据库 UTC 时间列转为 Instant；会话时区固定为 UTC。 */
    static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Timestamp timestamp ? timestamp.toInstant()
            : ((LocalDateTime)value).toInstant(ZoneOffset.UTC);
    }
}
