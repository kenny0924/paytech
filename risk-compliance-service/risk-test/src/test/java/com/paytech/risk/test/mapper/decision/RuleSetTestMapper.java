package com.paytech.risk.test.mapper.decision;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;

/**
 * 规则集、版本、决策点版本、绑定与发布记录的测试 SQL
 * <li>只按调用方给出的 ID 或测试自有的随机规则集编码删除，不清理其他测试或会话插入的数据。</li>
 * <li>读取方法按表返回原始行，供数据库 Checker 逐列断言。</li>
 *
 * @author Kenny
 */
@TestComponent
public class RuleSetTestMapper {

    private final JdbcTemplate jdbc;

    public RuleSetTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入可配置的规则集根。 */
    public void insertRuleSet(long ruleSetId, long pointId, String ruleSetCode, long currentVersionId,
        Instant time) {
        jdbc.update("INSERT INTO t_rule_set(rule_set_id,decision_point_id,rule_set_code,current_rule_set_version_id,"
            + "status,version,create_time,update_time) VALUES(?,?,?,?,'CONFIGURABLE',1,?,?)", ruleSetId, pointId,
            ruleSetCode, currentVersionId, ts(time), ts(time));
    }

    /** 插入整体删除的规则集根，删除时间与更新时间一致。 */
    public void insertDeletedRuleSet(long ruleSetId, long pointId, String ruleSetCode, long currentVersionId,
        long deletedBy, String triggerId, Instant time) {
        jdbc.update("INSERT INTO t_rule_set(rule_set_id,decision_point_id,rule_set_code,current_rule_set_version_id,"
            + "status,version,deleted_by,deleted_time,delete_reason_code,delete_trigger_id,create_time,update_time) "
            + "VALUES(?,?,?,?,'DELETED',2,?,?,'OPERATOR_REQUEST',?,?,?)", ruleSetId, pointId, ruleSetCode,
            currentVersionId, deletedBy, ts(time), triggerId, ts(time), ts(time));
    }

    /** 插入旧格式规则集版本；FROZEN 时写入冻结证据，其他状态为空。 */
    public void insertVersion(long versionId, long pointId, String ruleSetCode, String ruleSetName, int versionNo,
        RuleSetVersionStatus status, String contentHash, long createdBy, String changeReason, Instant createTime) {
        insertVersion(versionId, pointId, ruleSetCode, ruleSetName, versionNo, status, contentHash, createdBy,
            changeReason, createTime, "LEGACY");
    }

    /** 插入指定内容格式的规则集版本；FROZEN 时写入冻结证据，其他状态为空。 */
    public void insertVersion(long versionId, long pointId, String ruleSetCode, String ruleSetName, int versionNo,
        RuleSetVersionStatus status, String contentHash, long createdBy, String changeReason, Instant createTime,
        String contentFormat) {
        boolean frozen = status == RuleSetVersionStatus.FROZEN;
        jdbc.update("INSERT INTO t_rule_set_version(rule_set_version_id,decision_point_id,input_contract_code,"
            + "input_contract_version,rule_set_code,rule_set_name,version_no,status,content_hash,content_format,"
            + "created_by,change_reason,frozen_by,frozen_time,create_time,update_time) "
            + "VALUES(?,?,'ORDER_PREPARATION',4,?,?,?,?,?,?,?,?,?,?,?,?)", versionId, pointId, ruleSetCode,
            ruleSetName, versionNo, status.getCode(), contentHash, contentFormat, createdBy, changeReason,
            frozen ? createdBy : null, frozen ? ts(createTime) : null, ts(createTime), ts(createTime));
    }

    /** 插入一条最小规则：规则、唯一根判断节点、左操作数与终止动作，ID 依次为 base..base+3。 */
    public void insertMinimalRule(long ruleId, long versionId, long actionVersionId) {
        jdbc.update("INSERT INTO t_risk_rule(rule_id,rule_set_version_id,rule_code,rule_name,priority_no,enabled_flag,"
            + "reason_code,reason_message,create_time,update_time) VALUES(?,?,'MOCK_RULE','Mock rule',10,1,NULL,NULL,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId, versionId);
        jdbc.update("INSERT INTO t_rule_node(node_id,rule_id,parent_node_id,node_type,group_operator,"
            + "comparison_operator,sequence_no,create_time,update_time) VALUES(?,?,NULL,'PREDICATE',NULL,'IS_NOT_NULL',1,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId + 1, ruleId);
        jdbc.update("INSERT INTO t_rule_operand(operand_id,node_id,operand_role,value_sequence,source_type,field_code,"
            + "value_type,create_time,update_time) VALUES(?,?,'LEFT',1,'CONTEXT','country_code','STRING',"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId + 2, ruleId + 1);
        jdbc.update("INSERT INTO t_rule_action(rule_action_id,rule_id,action_version_id,sequence_no,target_binding_code,"
            + "create_time) VALUES(?,?,?,1,NULL,UTC_TIMESTAMP(6))", ruleId + 3, ruleId, actionVersionId);
    }

    /** 插入一条统一变量规则：INPUT_COUNTRY_CODE EQ 'AE'，规则、节点、左右操作数与终止动作 ID 依次为 base..base+4。 */
    public void insertVariableRule(long ruleId, long versionId, long actionVersionId) {
        jdbc.update("INSERT INTO t_risk_rule(rule_id,rule_set_version_id,rule_code,rule_name,priority_no,enabled_flag,"
            + "reason_code,reason_message,create_time,update_time) VALUES(?,?,'MOCK_RULE','Mock rule',10,1,NULL,NULL,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId, versionId);
        jdbc.update("INSERT INTO t_rule_node(node_id,rule_id,parent_node_id,node_type,group_operator,"
            + "comparison_operator,sequence_no,create_time,update_time) VALUES(?,?,NULL,'PREDICATE',NULL,'EQ',1,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId + 1, ruleId);
        jdbc.update("INSERT INTO t_rule_operand(operand_id,node_id,operand_role,value_sequence,source_type,field_code,"
            + "value_type,variable_code,create_time,update_time) VALUES(?,?,'LEFT',1,'VARIABLE',NULL,'STRING',"
            + "'INPUT_COUNTRY_CODE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId + 2, ruleId + 1);
        jdbc.update("INSERT INTO t_rule_operand(operand_id,node_id,operand_role,value_sequence,source_type,field_code,"
            + "value_type,string_value,create_time,update_time) VALUES(?,?,'RIGHT',1,'LITERAL',NULL,'STRING','AE',"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId + 3, ruleId + 1);
        jdbc.update("INSERT INTO t_rule_action(rule_action_id,rule_id,action_version_id,sequence_no,target_binding_code,"
            + "create_time) VALUES(?,?,?,1,NULL,UTC_TIMESTAMP(6))", ruleId + 4, ruleId, actionVersionId);
    }

    /** 覆盖规则集根的当前版本指针，用于构造管理根损坏。 */
    public void updateCurrentVersion(long ruleSetId, long currentVersionId) {
        jdbc.update("UPDATE t_rule_set SET current_rule_set_version_id=? WHERE rule_set_id=?", currentVersionId,
            ruleSetId);
    }

    /** 读取动作版本名称。 */
    public String selectActionName(long actionVersionId) {
        return jdbc.queryForObject("SELECT action_name FROM t_action_version WHERE action_version_id=?", String.class,
            actionVersionId);
    }

    /** 覆盖动作版本名称，用于构造动作目录漂移；调用方负责恢复。 */
    public void updateActionName(long actionVersionId, String actionName) {
        jdbc.update("UPDATE t_action_version SET action_name=? WHERE action_version_id=?", actionName,
            actionVersionId);
    }

    /** 插入拒绝原因字典项。 */
    public void insertRejectionReason(long reasonId, String reasonCode, String status) {
        jdbc.update("INSERT INTO t_rule_reject_reason(rejection_reason_id,reason_code,reason_name,description,"
            + "default_message,status,version,created_by,updated_by,create_time,update_time) VALUES(?,?,?,NULL,?,?,1,"
            + "70001,70001,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", reasonId, reasonCode, "Mock reason",
            "Mock reason message", status);
    }

    /** 删除指定拒绝原因字典项。 */
    public void deleteRejectionReasons(Collection<Long> reasonIds) {
        reasonIds.forEach(id -> jdbc.update("DELETE FROM t_rule_reject_reason WHERE rejection_reason_id=?", id));
    }

    /** 读取规则集根；不存在时返回 null。 */
    public Map<String, Object> selectRuleSet(long pointId, String ruleSetCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM t_rule_set WHERE decision_point_id=? "
            + "AND rule_set_code=?", pointId, ruleSetCode);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 按业务版本号升序读取规则集全部版本。 */
    public List<Map<String, Object>> selectVersions(long pointId, String ruleSetCode) {
        return jdbc.queryForList("SELECT * FROM t_rule_set_version WHERE decision_point_id=? AND rule_set_code=? "
            + "ORDER BY version_no", pointId, ruleSetCode);
    }

    /** 读取版本下的规则，按优先级升序。 */
    public List<Map<String, Object>> selectRules(long versionId) {
        return jdbc.queryForList("SELECT * FROM t_risk_rule WHERE rule_set_version_id=? ORDER BY priority_no",
            versionId);
    }

    /** 读取规则下的条件节点，按 ID 升序（即生成顺序）。 */
    public List<Map<String, Object>> selectNodes(long ruleId) {
        return jdbc.queryForList("SELECT * FROM t_rule_node WHERE rule_id=? ORDER BY node_id", ruleId);
    }

    /** 读取节点下的操作数，按角色与值顺序排列。 */
    public List<Map<String, Object>> selectOperands(long nodeId) {
        return jdbc.queryForList("SELECT * FROM t_rule_operand WHERE node_id=? ORDER BY operand_role, value_sequence",
            nodeId);
    }

    /** 读取规则下的动作绑定，按顺序排列。 */
    public List<Map<String, Object>> selectRuleActions(long ruleId) {
        return jdbc.queryForList("SELECT * FROM t_rule_action WHERE rule_id=? ORDER BY sequence_no", ruleId);
    }

    /** 读取动作绑定下的参数，按定义与值顺序排列。 */
    public List<Map<String, Object>> selectActionParameters(long ruleActionId) {
        return jdbc.queryForList("SELECT * FROM t_rule_action_parameter WHERE rule_action_id=? "
            + "ORDER BY parameter_definition_id, value_sequence", ruleActionId);
    }

    /** 读取统一变量当前定义行。 */
    public Map<String, Object> selectVariable(String variableCode) {
        return jdbc.queryForMap("SELECT * FROM t_risk_variable WHERE variable_code=?", variableCode);
    }

    /** 按规则集编码删除根、全部版本及规则闭包，用于清理经业务入口写入的数据。 */
    public void deleteByCode(long pointId, String ruleSetCode) {
        List<Long> versionIds = jdbc.queryForList("SELECT rule_set_version_id FROM t_rule_set_version WHERE "
            + "decision_point_id=? AND rule_set_code=?", Long.class, pointId, ruleSetCode);
        jdbc.update("DELETE FROM t_rule_set WHERE decision_point_id=? AND rule_set_code=?", pointId, ruleSetCode);
        versionIds.forEach(this::deleteRules);
        versionIds.forEach(id -> jdbc.update("DELETE FROM t_rule_set_version WHERE rule_set_version_id=?", id));
    }

    /** 覆盖版本内容摘要，用于构造闭包损坏。 */
    public void updateContentHash(long versionId, String contentHash) {
        jdbc.update("UPDATE t_rule_set_version SET content_hash=? WHERE rule_set_version_id=?", contentHash,
            versionId);
    }

    /** 插入冻结的决策点版本。 */
    public void insertPointVersion(long pointVersionId, long pointId, int versionNo, String contentHash) {
        jdbc.update("INSERT INTO t_decision_point_version(point_version_id,decision_point_id,version_no,"
            + "input_contract_code,input_contract_version,output_contract_code,default_decision,evaluation_strategy,"
            + "failure_strategy,timeout_ms,status,content_hash,create_time,update_time) VALUES(?,?,?,"
            + "'ORDER_PREPARATION',4,'RISK_DECISION_RESULT','REVIEW','FIRST_TERMINAL','FAIL_CLOSED',500,'FROZEN',?,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", pointVersionId, pointId, versionNo, contentHash);
    }

    /** 插入决策点版本到规则集版本的唯一绑定。 */
    public void insertBinding(long bindingId, long pointVersionId, long ruleSetVersionId) {
        jdbc.update("INSERT INTO t_decision_point_rule_set(binding_id,point_version_id,rule_set_version_id,"
            + "sequence_no,create_time) VALUES(?,?,?,1,UTC_TIMESTAMP(6))", bindingId, pointVersionId,
            ruleSetVersionId);
    }

    /** 插入发布记录。 */
    public void insertRelease(long releaseId, String releaseNo, long pointId, long pointVersionId,
        DecisionEnvironment environment, DecisionReleaseStatus status, Integer activeSlot, String contentHash, Instant activatedTime,
        Instant supersededTime) {
        jdbc.update("INSERT INTO t_decision_release(release_id,release_no,decision_point_id,point_version_id,"
            + "environment_code,status,active_slot,content_hash,created_by,activated_time,superseded_time,version,"
            + "create_time,update_time) VALUES(?,?,?,?,?,?,?,?,70001,?,?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            releaseId, releaseNo, pointId, pointVersionId, environment.getCode(), status.getCode(), activeSlot, contentHash,
            ts(activatedTime), ts(supersededTime));
    }

    /** 按依赖逆序删除指定发布、绑定、决策点版本、规则集版本与根。 */
    public void deleteAll(Collection<Long> releaseIds, Collection<Long> bindingIds, Collection<Long> pointVersionIds,
        Collection<Long> versionIds, Collection<Long> ruleSetIds) {
        releaseIds.forEach(id -> jdbc.update("DELETE FROM t_decision_release WHERE release_id=?", id));
        bindingIds.forEach(id -> jdbc.update("DELETE FROM t_decision_point_rule_set WHERE binding_id=?", id));
        pointVersionIds
            .forEach(id -> jdbc.update("DELETE FROM t_decision_point_version WHERE point_version_id=?", id));
        ruleSetIds.forEach(id -> jdbc.update("DELETE FROM t_rule_set WHERE rule_set_id=?", id));
        versionIds.forEach(this::deleteRules);
        versionIds.forEach(id -> jdbc.update("DELETE FROM t_rule_set_version WHERE rule_set_version_id=?", id));
    }

    /** 删除版本下的规则闭包。 */
    private void deleteRules(long versionId) {
        String rules = "SELECT rule_id FROM t_risk_rule WHERE rule_set_version_id=?";
        jdbc.update("DELETE o FROM t_rule_operand o JOIN t_rule_node n ON n.node_id=o.node_id WHERE n.rule_id IN ("
            + rules + ")", versionId);
        jdbc.update("DELETE FROM t_rule_node WHERE rule_id IN (" + rules + ")", versionId);
        jdbc.update("DELETE p FROM t_rule_action_parameter p JOIN t_rule_action a ON a.rule_action_id=p.rule_action_id "
            + "WHERE a.rule_id IN (" + rules + ")", versionId);
        jdbc.update("DELETE FROM t_rule_action WHERE rule_id IN (" + rules + ")", versionId);
        jdbc.update("DELETE FROM t_risk_rule WHERE rule_set_version_id=?", versionId);
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
