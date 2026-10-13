package com.paytech.risk.ext.rest.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ConditionNodeRequestBody;
import com.paytech.risk.api.decision.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleActionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.ReleaseStateChange;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NullLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.CreatedCandidate;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.ValidatedCandidate;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.CreatedRuleSet;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.RuleAuthoringView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 规则集与发布领域模型和 HTTP 模型之间的纯转换测试。 */
class DecisionWireConverterAutoTest {

    @Test
    void authoringContextUsesOnlyProvidedDatabaseDefinitions() {
        var response = DecisionRuleSetWireConverter.toResponse(
            new RuleAuthoringView(CheckoutPrepareAuthoringDescriptor.v1(), List.of(input("OPERATOR_LOCALE", "locale"), input("OTHER_CURRENCY", "currency"))));

        assertEquals(List.of("currency", "locale"),
            response.getFields().stream().map(value -> value.getFieldCode()).toList());
        assertEquals(List.of("ADD_RISK_TAG", "ALLOW_CHECKOUT", "APPLY_COOLDOWN", "DENY_CHECKOUT", "REVIEW_CHECKOUT"),
            response.getActions().stream().map(value -> value.getActionCode()).toList());
    }

    private static com.paytech.risk.decision.domain.variable.RiskVariable.Entry input(String code, String field) {
        var d = new com.paytech.risk.decision.domain.variable.RiskVariable.Definition(code,
            com.paytech.risk.decision.domain.enums.VariableSource.INPUT, DecisionValueType.STRING,
            "CHECKOUT_PREPARE", "ORDER_PREPARATION", 4, field,
            java.util.Set.of(com.paytech.risk.decision.domain.enums.VariableUsage.RULE_CONDITION), true,
            com.paytech.risk.decision.domain.enums.VariableFormat.TEXT, null, null, null, null);
        return new com.paytech.risk.decision.domain.variable.RiskVariable.Entry(d, code, null, null,
            com.paytech.risk.decision.domain.enums.VariableStatus.ACTIVE, 1, 1, 1,
            Instant.parse("2026-09-13T00:00:00Z"), Instant.parse("2026-09-13T00:00:00Z"));
    }

    @Test
    void requestMappingPreservesLocalKeysAndUsesClosedLiteralTypes() {
        CreateRuleSetVersionRequestBody request = new CreateRuleSetVersionRequestBody(
            "CHECKOUT_PREPARE", "ORDER_LIMIT", " Order limit ", null, null, " create ", List.of(
            new RuleRequestBody("NET_AMOUNT", "Net amount", 1, true, null, List.of(
                new ConditionNodeRequestBody("root", null, 1, "PREDICATE", null, "EQ", List.of(
                    new OperandRequestBody("LEFT", 1, "CONTEXT", "net_amount", "NUMBER", null),
                    new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", "1.0")))),
                new RuleActionRequestBody("ALLOW_CHECKOUT", 1))));

        var domain = DecisionRuleSetWireConverter.toDomain(request);

        assertEquals("root", domain.rules().getFirst().conditionNodes().getFirst().nodeKey());
        assertEquals(NullLiteral.INSTANCE,
            domain.rules().getFirst().conditionNodes().getFirst().operands().getFirst().literalValue());
        assertEquals(new StringLiteral("1.0"),
            domain.rules().getFirst().conditionNodes().getFirst().operands().get(1).literalValue());
        assertEquals(new ActionKey("ALLOW_CHECKOUT", 1), domain.rules().getFirst().action());

        OperandRequestBody booleanOperand = new OperandRequestBody(
            "RIGHT", 1, "LITERAL", null, "BOOLEAN", true);
        assertEquals(new BooleanLiteral(true), DecisionRuleSetWireConverter.toDomain(booleanOperand).literalValue());
    }

    @Test
    void responseMappingUsesPositiveDecimalIdsAndFixedUtcMicroseconds() {
        Instant time = Instant.parse("2026-09-05T01:02:03.120000Z");
        CreatedRuleSet created = new CreatedRuleSet(301L, 202L, "ORDER_LIMIT", 1,
            RuleSetVersionStatus.DRAFT, "a".repeat(64), 11L, time);

        var response = DecisionRuleSetWireConverter.toResponse(created);

        assertEquals("301", response.getRuleSetVersionId());
        assertEquals("202", response.getPointVersionId());
        assertEquals("11", response.getCreatedBy());
        assertEquals("2026-09-05T01:02:03.120000Z", response.getCreateTime());
        assertThrows(ErrorException.class, () -> DecisionWireValues.positiveId(0L));
        CreatedRuleSet nanosecondTime = new CreatedRuleSet(301L, 202L, "ORDER_LIMIT", 1,
            RuleSetVersionStatus.DRAFT, "a".repeat(64), 11L,
            Instant.parse("2026-09-05T01:02:03.123456789Z"));
        assertThrows(ErrorException.class, () -> DecisionRuleSetWireConverter.toResponse(nanosecondTime));
    }

    @Test
    void candidateResponsesKeepInitialTriggerPrivateAndStaticCheckNullMatrix() {
        Instant time = Instant.parse("2026-09-05T01:02:03.123456Z");
        ReleaseStateChange initial = new ReleaseStateChange(811L, time, null, DecisionReleaseStatus.DRAFT,
            "CANDIDATE_CREATED", "OPERATIONS_COMMAND", "1".repeat(64), 11L, 1);
        CreatedCandidate created = new CreatedCandidate(801L, 1, 201L, "CHECKOUT_PREPARE", 202L, 301L,
            DecisionEnvironment.TEST, DecisionReleaseStatus.DRAFT, 1, "b".repeat(64), 11L, time, initial);

        var createResponse = DecisionReleaseWireConverter.toResponse(created);
        assertEquals("811", createResponse.getInitialStateChange().getAuditEventId());
        assertEquals("OPERATIONS_COMMAND", createResponse.getInitialStateChange().getTriggerType());
        assertEquals("11", createResponse.getInitialStateChange().getActorId());

        StaticCheckResult passed = new StaticCheckResult(StaticCheckCode.REFERENCE_INTEGRITY,
            StaticCheckStatus.PASSED, StaticCheckSeverity.INFO, null, null);
        ValidatedCandidate validated = new ValidatedCandidate(801L, 1, DecisionEnvironment.TEST,
            DecisionReleaseStatus.VALIDATING, 2, "b".repeat(64), true, List.of(passed));
        var validateResponse = DecisionReleaseWireConverter.toResponse(validated);
        assertEquals(Boolean.TRUE, validateResponse.getAllStaticChecksPassed());
        assertNull(validateResponse.getChecks().getFirst().getErrorCode());
        assertNull(validateResponse.getChecks().getFirst().getErrorSummary());
    }

    @Test
    void closedCodeParsersRejectUnknownOrNullInsteadOfUsingEnumValueOf() {
        assertEquals(RuleSetVersionStatus.FROZEN, DecisionRuleSetWireConverter.parseRuleSetStatus("FROZEN"));
        assertNull(DecisionRuleSetWireConverter.parseRuleSetStatus(null));
        assertThrows(com.paytech.foundation.common.error.InvalidParameterException.class,
            () -> DecisionRuleSetWireConverter.parseRuleSetStatus("frozen"));
        assertThrows(com.paytech.foundation.common.error.InvalidParameterException.class,
            () -> DecisionReleaseWireConverter.parseReleaseStatus("UNKNOWN"));
    }
}
