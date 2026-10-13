package com.paytech.risk.test.mapper.decision;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.FlowStatus;

/**
 * 决策流四表、拒绝原因、旧点绑定与变量别名的测试 SQL
 * <li>写入只按调用方给出的 ID/编码删除，不清理其他测试或会话插入的数据。</li>
 * <li>读取方法返回数据库原始事实，供 Checker 断言落库结果。</li>
 *
 * @author Kenny
 */
@TestComponent
public class FlowTestMapper {
    /** 种子数据中的决策点。 */
    public static final long DECISION_POINT_ID = 210001L;

    private static final String FLOW_IDS =
        "SELECT flow_id FROM t_decision_flow WHERE decision_point_id=" + DECISION_POINT_ID + " AND flow_code=?";

    private static final String VERSION_IDS = "SELECT flow_version_id FROM t_decision_flow_version WHERE flow_id IN ("
        + FLOW_IDS + ")";

    private final JdbcTemplate jdbc;

    public FlowTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 管理根的数据库事实。 */
    public record RootRow(long flowId, long decisionPointId, String flowCode, long currentFlowVersionId, String status,
        long version, Long deletedBy, Instant deletedTime, Instant createTime, Instant updateTime) {
    }

    /** 版本的数据库事实。 */
    public record VersionRow(long flowVersionId, long flowId, int versionNo, String flowName, String inputContractCode,
        int inputContractVersion, long createdBy, String changeReason, Instant createTime, Instant updateTime) {
    }

    /** 节点的数据库事实。 */
    public record NodeRow(long nodeId, String nodeKey, String nodeType, String nodeName, Long ruleSetVersionId,
        String decisionCode, String reasonCode, String reasonMessage, int positionX, int positionY, Instant createTime,
        Instant updateTime) {
    }

    /** 连线的数据库事实，端点已换算为本版本节点键。 */
    public record EdgeRow(long edgeId, String edgeKey, String sourceNodeKey, String targetNodeKey, String edgeType,
        int priorityNo, String edgeName, String conditionJson, Instant createTime, Instant updateTime) {
    }

    /** 插入管理根。 */
    public void insertRoot(long flowId, String flowCode, long currentVersionId, FlowStatus status, long version,
        Long deletedBy, Instant deletedTime, Instant createTime, Instant updateTime) {
        jdbc.update("INSERT INTO t_decision_flow(flow_id,decision_point_id,flow_code,current_flow_version_id,status,"
            + "version,deleted_by,deleted_time,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)", flowId,
            DECISION_POINT_ID, flowCode, currentVersionId, status.getCode(), version, deletedBy, ts(deletedTime),
            ts(createTime), ts(updateTime));
    }

    /** 插入 ORDER_PREPARATION v4 合同下的不可变版本，更新时间等于创建时间。 */
    public void insertVersion(long versionId, long flowId, int versionNo, String flowName, long createdBy,
        String changeReason, Instant time) {
        jdbc.update("INSERT INTO t_decision_flow_version(flow_version_id,flow_id,version_no,flow_name,"
            + "input_contract_code,input_contract_version,created_by,change_reason,create_time,update_time) "
            + "VALUES(?,?,?,?,'ORDER_PREPARATION',4,?,?,?,?)", versionId, flowId, versionNo, flowName, createdBy,
            changeReason, ts(time), ts(time));
    }

    /** 插入版本节点，审计时间与版本一致。 */
    public void insertNode(long nodeId, long versionId, String nodeKey, String nodeType, String name,
        Long ruleSetVersionId, String decisionCode, String reasonCode, String reasonMessage, int x, int y,
        Instant time) {
        jdbc.update("INSERT INTO t_decision_flow_node(node_id,flow_version_id,node_key,node_type,node_name,"
            + "rule_set_version_id,decision_code,reason_code,reason_message,position_x,position_y,create_time,"
            + "update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", nodeId, versionId, nodeKey, nodeType, name,
            ruleSetVersionId, decisionCode, reasonCode, reasonMessage, x, y, ts(time), ts(time));
    }

    /** 插入版本连线，审计时间与版本一致。 */
    public void insertEdge(long edgeId, long versionId, String edgeKey, long sourceNodeId, long targetNodeId,
        String edgeType, int priority, String name, String conditionJson, Instant time) {
        jdbc.update("INSERT INTO t_decision_flow_edge(edge_id,flow_version_id,edge_key,source_node_id,target_node_id,"
            + "edge_type,priority_no,edge_name,condition_json,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            edgeId, versionId, edgeKey, sourceNodeId, targetNodeId, edgeType, priority, name, conditionJson, ts(time),
            ts(time));
    }

    /** 按编码删除决策点下流程的全部版本明细与根。 */
    public void deleteFlowByCode(String flowCode) {
        jdbc.update("DELETE FROM t_decision_flow_edge WHERE flow_version_id IN (" + VERSION_IDS + ")", flowCode);
        jdbc.update("DELETE FROM t_decision_flow_node WHERE flow_version_id IN (" + VERSION_IDS + ")", flowCode);
        jdbc.update("DELETE FROM t_decision_flow_version WHERE flow_id IN (" + FLOW_IDS + ")", flowCode);
        jdbc.update("DELETE FROM t_decision_flow WHERE decision_point_id=? AND flow_code=?", DECISION_POINT_ID,
            flowCode);
    }

    /** 读取决策点下给定编码的流程根。 */
    public Optional<RootRow> root(String flowCode) {
        return jdbc.query("SELECT * FROM t_decision_flow WHERE decision_point_id=? AND flow_code=?",
            (rs, i) -> new RootRow(rs.getLong("flow_id"), rs.getLong("decision_point_id"), rs.getString("flow_code"),
                rs.getLong("current_flow_version_id"), rs.getString("status"), rs.getLong("version"),
                nullableLong(rs, "deleted_by"), instant(rs, "deleted_time"), instant(rs, "create_time"),
                instant(rs, "update_time")), DECISION_POINT_ID, flowCode).stream().findFirst();
    }

    /** 读取流程全部版本，按版本号升序。 */
    public List<VersionRow> versions(long flowId) {
        return jdbc.query("SELECT * FROM t_decision_flow_version WHERE flow_id=? ORDER BY version_no",
            (rs, i) -> new VersionRow(rs.getLong("flow_version_id"), rs.getLong("flow_id"), rs.getInt("version_no"),
                rs.getString("flow_name"), rs.getString("input_contract_code"), rs.getInt("input_contract_version"),
                rs.getLong("created_by"), rs.getString("change_reason"), instant(rs, "create_time"),
                instant(rs, "update_time")), flowId);
    }

    /** 读取版本节点，按节点键排序。 */
    public List<NodeRow> nodes(long versionId) {
        return jdbc.query("SELECT * FROM t_decision_flow_node WHERE flow_version_id=? ORDER BY node_key",
            (rs, i) -> new NodeRow(rs.getLong("node_id"), rs.getString("node_key"), rs.getString("node_type"),
                rs.getString("node_name"), nullableLong(rs, "rule_set_version_id"), rs.getString("decision_code"),
                rs.getString("reason_code"), rs.getString("reason_message"), rs.getInt("position_x"),
                rs.getInt("position_y"), instant(rs, "create_time"), instant(rs, "update_time")), versionId);
    }

    /** 读取版本连线，按连线键排序，端点换算为节点键。 */
    public List<EdgeRow> edges(long versionId) {
        return jdbc.query("SELECT e.*, s.node_key AS source_key, t.node_key AS target_key FROM t_decision_flow_edge e "
            + "LEFT JOIN t_decision_flow_node s ON s.node_id=e.source_node_id AND s.flow_version_id=e.flow_version_id "
            + "LEFT JOIN t_decision_flow_node t ON t.node_id=e.target_node_id AND t.flow_version_id=e.flow_version_id "
            + "WHERE e.flow_version_id=? ORDER BY e.edge_key",
            (rs, i) -> new EdgeRow(rs.getLong("edge_id"), rs.getString("edge_key"), rs.getString("source_key"),
                rs.getString("target_key"), rs.getString("edge_type"), rs.getInt("priority_no"),
                rs.getString("edge_name"), rs.getString("condition_json"), instant(rs, "create_time"),
                instant(rs, "update_time")), versionId);
    }

    /** 决策点下全部流程根数量，用于确认拒绝的写入未落库。 */
    public long flowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_decision_flow WHERE decision_point_id=?", Long.class,
            DECISION_POINT_ID);
    }

    /** 全部流程版本数量。 */
    public long versionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_decision_flow_version", Long.class);
    }

    /** 覆盖根修订号，构造当前指针与版本号不一致的损坏。 */
    public void updateRootVersion(long flowId, long version) {
        jdbc.update("UPDATE t_decision_flow SET version=? WHERE flow_id=?", version, flowId);
    }

    /** 覆盖版本的输入合同版本，构造历史版本归属损坏。 */
    public void updateVersionContract(long versionId, int contractVersion) {
        jdbc.update("UPDATE t_decision_flow_version SET input_contract_version=? WHERE flow_version_id=?",
            contractVersion, versionId);
    }

    /** 覆盖连线条件存储内容。 */
    public void updateCondition(long versionId, String edgeKey, String conditionJson) {
        jdbc.update("UPDATE t_decision_flow_edge SET condition_json=? WHERE flow_version_id=? AND edge_key=?",
            conditionJson, versionId, edgeKey);
    }

    /** 插入拒绝原因字典项。 */
    public void insertReason(long reasonId, String reasonCode, DictionaryStatus status) {
        jdbc.update("INSERT INTO t_rule_reject_reason(rejection_reason_id,reason_code,reason_name,description,"
            + "default_message,status,version,created_by,updated_by,create_time,update_time) "
            + "VALUES(?,?,'Mock reason',NULL,'Mock default message',?,1,70001,70001,UTC_TIMESTAMP(6),"
            + "UTC_TIMESTAMP(6))", reasonId, reasonCode, status.getCode());
    }

    /** 删除拒绝原因字典项。 */
    public void deleteReason(long reasonId) {
        jdbc.update("DELETE FROM t_rule_reject_reason WHERE rejection_reason_id=?", reasonId);
    }

    /** 插入旧点版本到规则集版本的绑定。 */
    public void insertBinding(long bindingId, long pointVersionId, long ruleSetVersionId, int sequenceNo) {
        jdbc.update("INSERT INTO t_decision_point_rule_set(binding_id,point_version_id,rule_set_version_id,"
            + "sequence_no,create_time) VALUES(?,?,?,?,UTC_TIMESTAMP(6))", bindingId, pointVersionId,
            ruleSetVersionId, sequenceNo);
    }

    /** 删除旧点版本及其全部绑定。 */
    public void deletePointVersion(long pointVersionId) {
        jdbc.update("DELETE FROM t_decision_point_rule_set WHERE point_version_id=?", pointVersionId);
        jdbc.update("DELETE FROM t_decision_point_version WHERE point_version_id=?", pointVersionId);
    }

    /** 读取旧点版本的业务版本号。 */
    public int pointVersionNo(long pointVersionId) {
        return jdbc.queryForObject("SELECT version_no FROM t_decision_point_version WHERE point_version_id=?",
            Integer.class, pointVersionId);
    }

    /** 读取规则集版本下唯一规则的判断节点 ID。 */
    public long ruleNodeId(long ruleSetVersionId) {
        return jdbc.queryForObject("SELECT n.node_id FROM t_rule_node n JOIN t_risk_rule r ON r.rule_id=n.rule_id "
            + "WHERE r.rule_set_version_id=?", Long.class, ruleSetVersionId);
    }

    /** 覆盖规则集版本的输入合同版本，构造合同不一致的引用。 */
    public void updateRuleSetContract(long ruleSetVersionId, int contractVersion) {
        jdbc.update("UPDATE t_rule_set_version SET input_contract_version=? WHERE rule_set_version_id=?",
            contractVersion, ruleSetVersionId);
    }

    /** 读取决策点状态。 */
    public String pointStatus() {
        return jdbc.queryForObject("SELECT status FROM t_decision_point WHERE decision_point_id=?", String.class,
            DECISION_POINT_ID);
    }

    /** 覆盖决策点状态。 */
    public void updatePointStatus(String status) {
        jdbc.update("UPDATE t_decision_point SET status=? WHERE decision_point_id=?", status, DECISION_POINT_ID);
    }

    /** 读取变量最后更新时间。 */
    public Instant variableUpdateTime(String variableCode) {
        return jdbc.queryForObject("SELECT update_time FROM t_risk_variable WHERE variable_code=?",
            (rs, i) -> instant(rs, "update_time"), variableCode);
    }

    /** 插入 country_code 的系统输入变量别名。 */
    public void insertInputAlias(long variableId, String variableCode, String variableName, boolean flowCondition,
        String status) {
        jdbc.update("INSERT INTO t_risk_variable(variable_id,variable_code,variable_name,description,source_type,"
            + "value_type,point_code,source_contract_code,source_contract_version,source_field,rule_condition_flag,"
            + "flow_condition_flag,action_parameter_flag,system_managed_flag,format_code,status,version,create_by,"
            + "update_by,create_time,update_time) VALUES(?,?,?,NULL,'INPUT','STRING','CHECKOUT_PREPARE',"
            + "'ORDER_PREPARATION',4,'country_code',1,?,0,1,'UPPER_ALPHA_2',?,1,1,1,UTC_TIMESTAMP(6),"
            + "UTC_TIMESTAMP(6))", variableId, variableCode, variableName, flowCondition ? 1 : 0, status);
    }

    /** 删除变量。 */
    public void deleteVariable(String variableCode) {
        jdbc.update("DELETE FROM t_risk_variable WHERE variable_code=?", variableCode);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
