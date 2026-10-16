package com.paytech.risk.test.mapper.decision;

import static com.paytech.risk.decision.domain.enums.DecisionValueType.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.builder.decision.VariableBuilder.MemberSpec;

/**
 * 统一变量根、集合成员及变量引用位置的测试 SQL
 * <li>只按调用方给出的编码或 ID 删除，不清理种子变量或其他测试插入的数据。</li>
 * <li>引用位置直接写在规则集 Mocker 已插入的规则闭包上，或写入本 Mapper 自己插入的流程与动作参数行。</li>
 *
 * @author Kenny
 */
@TestComponent
public class VariableTestMapper {

    private final JdbcTemplate jdbc;

    public VariableTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按 Builder 的完整字段插入变量根，常量值写入唯一类型槽位。 */
    public void insertVariable(long variableId, String code, VariableBuilder spec) {
        Object value = spec.currentValue();
        jdbc.update("INSERT INTO t_risk_variable(variable_id,variable_code,variable_name,description,source_type,value_type,"
            + "point_code,source_contract_code,source_contract_version,source_field,rule_condition_flag,flow_condition_flag,"
            + "action_parameter_flag,system_managed_flag,format_code,numeric_unit,currency_code,currency_variable_code,"
            + "allowed_values_variable_code,string_value,number_value,boolean_value,datetime_value,duration_value,status,"
            + "version,create_by,update_by,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            variableId, code, spec.variableName(), spec.description(), spec.sourceType().getCode(), spec.valueType().getCode(),
            spec.pointCode(), spec.sourceContractCode(), spec.sourceContractVersion(), spec.sourceField(),
            spec.usages().contains(VariableUsage.RULE_CONDITION), spec.usages().contains(VariableUsage.FLOW_CONDITION),
            spec.usages().contains(VariableUsage.ACTION_PARAMETER), spec.systemManaged(), spec.formatCode().getCode(),
            spec.numericUnit() == null ? null : spec.numericUnit().getCode(), spec.currencyCode(), spec.currencyVariableCode(),
            spec.allowedValuesVariableCode(), slot(spec, STRING, value, text -> text), slot(spec, NUMBER, value, BigDecimal::new),
            value instanceof Boolean ? value : null, slot(spec, DATETIME, value, text -> ts(Instant.parse(text))),
            slot(spec, DURATION, value, Long::valueOf), spec.status().getCode(), spec.version(), spec.createBy(),
            spec.updateBy(), ts(spec.createTime()), ts(spec.updateTime()));
    }

    /** 插入一个集合成员，类型槽位与规范键由 Builder 计算。 */
    public void insertMember(long memberId, String code, VariableBuilder spec, MemberSpec member, int sortOrder) {
        Object value = member.value();
        jdbc.update("INSERT INTO t_risk_variable_member(member_id,variable_code,value_key,string_value,number_value,"
            + "boolean_value,datetime_value,duration_value,display_name,sort_order,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            memberId, code, member.valueKey(), slot(spec, STRING, value, text -> text), slot(spec, NUMBER, value, BigDecimal::new),
            value instanceof Boolean ? value : null, slot(spec, DATETIME, value, text -> ts(Instant.parse(text))),
            slot(spec, DURATION, value, Long::valueOf), member.displayName(), sortOrder,
            ts(spec.updateTime()));
    }

    /** 覆盖变量根的任意列，用于构造持久化损坏或特殊版本。 */
    public void updateColumn(String code, String column, Object value) {
        jdbc.update("UPDATE t_risk_variable SET " + column + "=? WHERE variable_code=?", value, code);
    }

    /** 覆盖成员规范键，构造成员完整性损坏。 */
    public void updateMemberKey(String code, String valueKey) {
        jdbc.update("UPDATE t_risk_variable_member SET value_key=? WHERE variable_code=?", valueKey, code);
    }

    /** 读取变量根的完整持久化行；不存在时返回空列表。 */
    public List<Map<String, Object>> selectVariable(String code) {
        return jdbc.queryForList("SELECT * FROM t_risk_variable WHERE variable_code=?", code);
    }

    /** 按展示顺序读取集合成员行。 */
    public List<Map<String, Object>> selectMembers(String code) {
        return jdbc.queryForList("SELECT * FROM t_risk_variable_member WHERE variable_code=? ORDER BY sort_order,member_id",
            code);
    }

    /** 让规则版本最小规则的左操作数引用变量，返回操作数 ID 与节点 ID。 */
    public Map<String, Object> referenceFromRuleOperand(long ruleSetVersionId, String variableCode,
        String currencyVariableCode) {
        jdbc.update("UPDATE t_rule_operand o JOIN t_rule_node n ON n.node_id=o.node_id JOIN t_risk_rule r ON r.rule_id=n.rule_id "
            + "SET o.variable_code=?, o.currency_variable_code=? WHERE r.rule_set_version_id=?", variableCode,
            currencyVariableCode, ruleSetVersionId);
        return jdbc.queryForMap("SELECT o.operand_id, o.node_id, r.rule_code FROM t_rule_operand o JOIN t_rule_node n "
            + "ON n.node_id=o.node_id JOIN t_risk_rule r ON r.rule_id=n.rule_id WHERE r.rule_set_version_id=?",
            ruleSetVersionId);
    }

    /** 在规则版本最小规则的终止动作上插入引用变量的动作参数，返回动作 ID 与规则编码。 */
    public Map<String, Object> insertActionParameter(long parameterId, long ruleSetVersionId, String variableCode) {
        Map<String, Object> action = jdbc.queryForMap("SELECT a.rule_action_id, r.rule_code FROM t_rule_action a "
            + "JOIN t_risk_rule r ON r.rule_id=a.rule_id WHERE r.rule_set_version_id=?", ruleSetVersionId);
        jdbc.update("INSERT INTO t_rule_action_parameter(parameter_id,rule_action_id,parameter_definition_id,value_sequence,"
            + "source_type,variable_code,create_time) VALUES(?,?,?,1,'VARIABLE',?,UTC_TIMESTAMP(6))", parameterId,
            action.get("rule_action_id"), parameterId, variableCode);
        return action;
    }

    /** 插入一个不属于任何节点的孤立操作数，构造引用父级断边。 */
    public void insertOrphanOperand(long operandId, String variableCode) {
        jdbc.update("INSERT INTO t_rule_operand(operand_id,node_id,operand_role,value_sequence,source_type,value_type,"
            + "variable_code,create_time,update_time) VALUES(?,?,'LEFT',1,'VARIABLE','STRING',?,UTC_TIMESTAMP(6),"
            + "UTC_TIMESTAMP(6))", operandId, operandId, variableCode);
    }

    /** 插入流程根。 */
    public void insertFlow(long flowId, long pointId, String flowCode, long currentVersionId, FlowStatus status) {
        jdbc.update("INSERT INTO t_decision_flow(flow_id,decision_point_id,flow_code,current_flow_version_id,status,version,"
            + "create_time,update_time) VALUES(?,?,?,?,?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", flowId, pointId, flowCode,
            currentVersionId, status.getCode());
    }

    /** 插入指定 ORDER_PREPARATION 合同版本下的流程版本头。 */
    public void insertFlowVersion(long flowVersionId, long flowId, int versionNo, String flowName,
        int inputContractVersion) {
        jdbc.update("INSERT INTO t_decision_flow_version(flow_version_id,flow_id,version_no,flow_name,input_contract_code,"
            + "input_contract_version,created_by,change_reason,create_time,update_time) VALUES(?,?,?,?,'ORDER_PREPARATION',?,"
            + "70001,'mock',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", flowVersionId, flowId, versionNo, flowName,
            inputContractVersion);
    }

    /** 插入流程连线；条件 JSON 为已持久化的规范内容。 */
    public void insertFlowEdge(long edgeId, long flowVersionId, String edgeKey, FlowEdgeType type, int priority,
        String conditionJson) {
        jdbc.update("INSERT INTO t_decision_flow_edge(edge_id,flow_version_id,edge_key,source_node_id,target_node_id,"
            + "edge_type,priority_no,edge_name,condition_json,create_time,update_time) VALUES(?,?,?,1,2,?,?,'mock edge',?,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", edgeId, flowVersionId, edgeKey, type.getCode(), priority, conditionJson);
    }

    /** 在同一流程版本批量插入条件连线，连线 ID 从 firstEdgeId 起连续递增。 */
    public void insertFlowEdges(long firstEdgeId, long flowVersionId, int count, String conditionJson) {
        jdbc.batchUpdate("INSERT INTO t_decision_flow_edge(edge_id,flow_version_id,edge_key,source_node_id,target_node_id,"
            + "edge_type,priority_no,edge_name,condition_json,create_time,update_time) VALUES(?,?,?,1,2,'CONDITIONAL',?,"
            + "'mock edge',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", IntStream.range(0, count)
                .mapToObj(index -> new Object[] {firstEdgeId + index, flowVersionId, "edge_" + index, index + 1, conditionJson})
                .toList());
    }

    /** 按依赖逆序删除指定流程、动作参数、孤立操作数、集合成员与变量根。 */
    public void deleteAll(Collection<Long> edgeIds, Collection<Long> flowVersionIds, Collection<Long> flowIds,
        Collection<Long> parameterIds, Collection<Long> operandIds, Collection<String> codes) {
        flowVersionIds.forEach(id -> jdbc.update("DELETE FROM t_decision_flow_edge WHERE flow_version_id=?", id));
        edgeIds.forEach(id -> jdbc.update("DELETE FROM t_decision_flow_edge WHERE edge_id=?", id));
        flowVersionIds.forEach(id -> jdbc.update("DELETE FROM t_decision_flow_version WHERE flow_version_id=?", id));
        flowIds.forEach(id -> jdbc.update("DELETE FROM t_decision_flow WHERE flow_id=?", id));
        parameterIds.forEach(id -> jdbc.update("DELETE FROM t_rule_action_parameter WHERE parameter_id=?", id));
        operandIds.forEach(id -> jdbc.update("DELETE FROM t_rule_operand WHERE operand_id=?", id));
        codes.forEach(code -> jdbc.update("DELETE FROM t_risk_variable_member WHERE variable_code=?", code));
        codes.forEach(code -> jdbc.update("DELETE FROM t_risk_variable WHERE variable_code=?", code));
    }

    /** 按类型把规范字符串转换为对应列值，其他类型为空。 */
    private static Object slot(VariableBuilder spec, DecisionValueType type, Object value, Function<String, Object> convert) {
        return value instanceof String text && spec.valueType() == type ? convert.apply(text) : null;
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
