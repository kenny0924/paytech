package com.paytech.risk.test.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅测试侧准备迁移后的真实旧规则事实；不调用新保存命令制造旧编排。 */
public final class LegacyRuleSetTestMapper {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DecisionIdentifierGenerator identifiers;

    public LegacyRuleSetTestMapper(JdbcTemplate jdbc, TransactionTemplate transaction, DecisionIdentifierGenerator identifiers) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.identifiers = identifiers;
    }

    /** 与原首版请求及其成功回执对应的 DRAFT，冻结和发布仍从真实 HTTP 入口执行。 */
    public Version insertFirstVersion(String code, long actor, String createKey) {
        var owned = new RiskDecisionTestMapper(jdbc);
        assertEquals(0, owned.ruleSetVersionCount(code));
        var request = DecisionRuleSetRequestBuilder.firstVersion(code);
        var rule = request.rules().getFirst();
        var catalog = CheckoutPrepareAuthoringDescriptor.v1();
        var action = catalog.actions().getFirst();
        var point = catalog.point();
        var canonicalizer = new RiskDecisionCanonicalizerImpl();
        var condition = new PredicateProjection(ComparisonOperator.EQ, List.of(
            new OperandProjection(OperandRole.LEFT, 1, OperandSource.CONTEXT, "country_code", DecisionValueType.STRING, NullValue.INSTANCE),
            new OperandProjection(OperandRole.RIGHT, 1, OperandSource.LITERAL, null, DecisionValueType.STRING, new StringValue("AE"))));
        var ruleProjection = new RuleSetVersionProjection(point.pointCode(), code, request.ruleSetName(), 1, List.of(
            new RuleProjection(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), true, rule.reasonCode(), condition,
                new ActionProjection(action.actionCode(), 1, action.handlerCode(), action.implementationVersion(),
                    action.implementationHash(), action.actionCategory(), true, action.decisionCode(), action.mergePolicy(), true))));
        String ruleHash = canonicalizer.ruleSetVersion(ruleProjection).sha256LowerHex();
        String requestHash = canonicalizer.createRuleSetVersionCommand(new CreateRuleSetVersionCommandProjection(
            point.pointCode(), code, request.ruleSetName(), null, null, request.changeReason(), List.of(
                new CommandRuleProjection(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), true, rule.reasonCode(),
                    condition, new CommandActionProjection(action.actionCode(), 1), null, List.of())))).sha256LowerHex();
        long versionId = identifiers.nextIdentifier();
        long pointVersionId = identifiers.nextIdentifier();
        long ruleId = identifiers.nextIdentifier();
        long nodeId = identifiers.nextIdentifier();
        Instant time = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Timestamp timestamp = Timestamp.from(time);
        transaction.executeWithoutResult(ignored -> {
            Long pointId = jdbc.queryForObject("SELECT decision_point_id FROM t_decision_point WHERE point_code=? FOR UPDATE", Long.class, point.pointCode());
            Integer pointVersionNo = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM t_decision_point_version WHERE decision_point_id=?", Integer.class, pointId);
            String pointHash = canonicalizer.decisionPointVersion(new DecisionPointVersionProjection(point.pointCode(),
                DecisionBusinessDomain.CHECKOUT, pointVersionNo, point.inputContractCode(), point.inputContractVersion(),
                point.outputContractCode(), point.defaultDecision(), point.evaluationStrategy(), point.failureStrategy(), point.timeoutMillis(),
                catalog.fields().stream().map(f -> new InputFieldProjection(f.fieldCode(), f.valueType(), f.requiredFlag(),
                    f.dataClassification(), f.ruleReferenceFlag(), f.actionParameterFlag())).toList(),
                List.of(new RuleSetBindingProjection(1, code, 1, ruleHash)), List.of(), List.of(), List.of())).sha256LowerHex();
            jdbc.update("INSERT INTO t_rule_set_version(rule_set_version_id,decision_point_id,input_contract_code,input_contract_version,rule_set_code,rule_set_name,version_no,status,content_hash,content_format,created_by,change_reason,create_time,update_time) VALUES(?,?,?,?,?,?,1,'DRAFT',?,'LEGACY',?,?,?,?)",
                versionId, pointId, point.inputContractCode(), point.inputContractVersion(), code, request.ruleSetName(), ruleHash, actor, request.changeReason(), timestamp, timestamp);
            jdbc.update("INSERT INTO t_rule_set(rule_set_id,decision_point_id,rule_set_code,current_rule_set_version_id,status,version,create_time,update_time) VALUES(?,?,?,?,'CONFIGURABLE',1,?,?)",
                identifiers.nextIdentifier(), pointId, code, versionId, timestamp, timestamp);
            jdbc.update("INSERT INTO t_decision_point_version(point_version_id,decision_point_id,version_no,input_contract_code,input_contract_version,output_contract_code,default_decision,evaluation_strategy,failure_strategy,timeout_ms,status,content_hash,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)",
                pointVersionId, pointId, pointVersionNo, point.inputContractCode(), point.inputContractVersion(), point.outputContractCode(), point.defaultDecision().getCode(), point.evaluationStrategy().getCode(), point.failureStrategy().getCode(), point.timeoutMillis(), pointHash, timestamp, timestamp);
            for (var field : catalog.fields()) {
                jdbc.update("INSERT INTO t_decision_point_input_field(input_field_id,point_version_id,field_code,value_type,required_flag,data_classification,rule_reference_flag,action_parameter_flag,create_time) VALUES(?,?,?,?,?,?,?,?,?)",
                    identifiers.nextIdentifier(), pointVersionId, field.fieldCode(), field.valueType().getCode(), field.requiredFlag(), field.dataClassification().getCode(), field.ruleReferenceFlag(), field.actionParameterFlag(), timestamp);
            }
            jdbc.update("INSERT INTO t_decision_point_rule_set(binding_id,point_version_id,rule_set_version_id,sequence_no,create_time) VALUES(?,?,?,1,?)", identifiers.nextIdentifier(), pointVersionId, versionId, timestamp);
            jdbc.update("INSERT INTO t_risk_rule(rule_id,rule_set_version_id,rule_code,rule_name,priority_no,enabled_flag,reason_code,create_time,update_time) VALUES(?,?,?,?,?,1,?,?,?)",
                ruleId, versionId, rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.reasonCode(), timestamp, timestamp);
            jdbc.update("INSERT INTO t_rule_node(node_id,rule_id,node_type,comparison_operator,sequence_no,create_time,update_time) VALUES(?,?,'PREDICATE','EQ',1,?,?)", nodeId, ruleId, timestamp, timestamp);
            jdbc.update("INSERT INTO t_rule_operand(operand_id,node_id,operand_role,value_sequence,source_type,field_code,value_type,create_time,update_time) VALUES(?,?,'LEFT',1,'CONTEXT','country_code','STRING',?,?)", identifiers.nextIdentifier(), nodeId, timestamp, timestamp);
            jdbc.update("INSERT INTO t_rule_operand(operand_id,node_id,operand_role,value_sequence,source_type,value_type,string_value,create_time,update_time) VALUES(?,?,'RIGHT',1,'LITERAL','STRING','AE',?,?)", identifiers.nextIdentifier(), nodeId, timestamp, timestamp);
            jdbc.update("INSERT INTO t_rule_action(rule_action_id,rule_id,action_version_id,sequence_no,create_time) VALUES(?,?,240001,1,?)", identifiers.nextIdentifier(), ruleId, timestamp);
            owned.insertOwnedCommand(identifiers.nextIdentifier(), "OPERATIONS_ACCOUNT:" + actor, "CREATE_RULE_SET_VERSION",
                createKey, requestHash, "rule-set-version:" + versionId, "SUCCEEDED", time.plusSeconds(86400), time);
        });
        return new Version(Long.toString(versionId), ruleHash, Long.toString(pointVersionId));
    }

    public record Version(String ruleSetVersionId, String contentHash, String pointVersionId) { }
}
