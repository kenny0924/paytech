package com.paytech.risk.test.mapper.decision;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domain.enums.OperandSource;

/**
 * 字典引用来源的测试 SQL：规则版本内的规则、动作及参数，以及决策流根、版本和结束节点
 * <li>只按调用方给出的 ID 删除，规则集根与版本仍由 {@link RuleSetTestMapper} 维护。</li>
 *
 * @author Kenny
 */
@TestComponent
public class DictionaryReferenceTestMapper {

    private final JdbcTemplate jdbc;

    public DictionaryReferenceTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入一条启用规则，原因编码与文案可为空。 */
    public void insertRule(long ruleId, long versionId, String ruleCode, String ruleName, int priorityNo,
        String reasonCode, String reasonMessage) {
        jdbc.update("INSERT INTO t_risk_rule(rule_id,rule_set_version_id,rule_code,rule_name,priority_no,enabled_flag,"
            + "reason_code,reason_message,create_time,update_time) VALUES(?,?,?,?,?,1,?,?,UTC_TIMESTAMP(6),"
            + "UTC_TIMESTAMP(6))", ruleId, versionId, ruleCode, ruleName, priorityNo, reasonCode, reasonMessage);
    }

    /** 插入规则动作绑定。 */
    public void insertRuleAction(long ruleActionId, long ruleId, long actionVersionId, int sequenceNo,
        String targetBindingCode) {
        jdbc.update("INSERT INTO t_rule_action(rule_action_id,rule_id,action_version_id,sequence_no,"
            + "target_binding_code,create_time) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))", ruleActionId, ruleId,
            actionVersionId, sequenceNo, targetBindingCode);
    }

    /** 插入一个字符串类型的动作参数值，其他类型值列为空。 */
    public void insertStringParameter(long parameterId, long ruleActionId, long parameterDefinitionId,
        int valueSequence, OperandSource sourceType, String stringValue) {
        jdbc.update("INSERT INTO t_rule_action_parameter(parameter_id,rule_action_id,parameter_definition_id,"
            + "value_sequence,source_type,string_value,create_time) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            parameterId, ruleActionId, parameterDefinitionId, valueSequence, sourceType.getCode(), stringValue);
    }

    /** 插入决策流根；DELETED 时同时写入删除人和删除时间。 */
    public void insertFlow(long flowId, long pointId, String flowCode, long currentVersionId, FlowStatus status,
        Instant time) {
        boolean deleted = status == FlowStatus.DELETED;
        jdbc.update("INSERT INTO t_decision_flow(flow_id,decision_point_id,flow_code,current_flow_version_id,status,"
            + "version,deleted_by,deleted_time,create_time,update_time) VALUES(?,?,?,?,?,1,?,?,?,?)", flowId, pointId,
            flowCode, currentVersionId, status.getCode(), deleted ? 70001L : null, deleted ? ts(time) : null, ts(time),
            ts(time));
    }

    /** 插入指定 ORDER_PREPARATION 合同版本下的不可变决策流版本头。 */
    public void insertFlowVersion(long flowVersionId, long flowId, int versionNo, String flowName, Instant time,
        int inputContractVersion) {
        jdbc.update("INSERT INTO t_decision_flow_version(flow_version_id,flow_id,version_no,flow_name,"
            + "input_contract_code,input_contract_version,created_by,change_reason,create_time,update_time) "
            + "VALUES(?,?,?,?,'ORDER_PREPARATION',?,70001,'mock flow change',?,?)", flowVersionId, flowId, versionNo,
            flowName, inputContractVersion, ts(time), ts(time));
    }

    /** 插入结束节点，原因与文案按给定值保存。 */
    public void insertEndNode(long nodeId, long flowVersionId, String nodeKey, String nodeName, DecisionValue decision,
        String reasonCode, String reasonMessage) {
        jdbc.update("INSERT INTO t_decision_flow_node(node_id,flow_version_id,node_key,node_type,node_name,"
            + "rule_set_version_id,decision_code,reason_code,reason_message,position_x,position_y,create_time,"
            + "update_time) VALUES(?,?,?,?,?,NULL,?,?,?,0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", nodeId,
            flowVersionId, nodeKey, FlowNodeType.END.getCode(), nodeName, decision.getCode(), reasonCode,
            reasonMessage);
    }

    /** 删除指定的参数、动作、规则、节点、流程版本与流程根。 */
    public void deleteAll(Collection<Long> parameterIds, Collection<Long> ruleActionIds, Collection<Long> ruleIds,
        Collection<Long> nodeIds, Collection<Long> flowVersionIds, Collection<Long> flowIds) {
        parameterIds.forEach(id -> jdbc.update("DELETE FROM t_rule_action_parameter WHERE parameter_id=?", id));
        ruleActionIds.forEach(id -> jdbc.update("DELETE FROM t_rule_action WHERE rule_action_id=?", id));
        ruleIds.forEach(id -> jdbc.update("DELETE FROM t_risk_rule WHERE rule_id=?", id));
        nodeIds.forEach(id -> jdbc.update("DELETE FROM t_decision_flow_node WHERE node_id=?", id));
        flowIds.forEach(id -> jdbc.update("DELETE FROM t_decision_flow WHERE flow_id=?", id));
        flowVersionIds.forEach(id -> jdbc.update("DELETE FROM t_decision_flow_version WHERE flow_version_id=?", id));
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
