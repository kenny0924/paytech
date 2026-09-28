package com.paytech.risk.auto.domain;

import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionPointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionReleaseProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.InputFieldProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.NullValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.OperandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.PredicateProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseActionVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseDecisionPointProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseInputFieldProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseNodeProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseOperandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleasePointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleSetBindingProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleSetVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetBindingProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.StringValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.UnsupportedProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.DecisionReleaseClosure;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticViolation;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidatorImpl;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidatorTestAccess;
import com.paytech.risk.decision.domain.release.StaticCheckErrorSummary;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionReleaseStaticValidatorAutoTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final DecisionReleaseStaticValidator validator = new DecisionReleaseStaticValidatorImpl();

    @Test
    void test_validClosureReturnsAllEightPassedChecksInFixedOrderAndImmutable() {
        List<StaticCheckResult> results = validator.validate(validClosure());

        assertEquals(List.of(
            StaticCheckCode.REFERENCE_INTEGRITY,
            StaticCheckCode.AST_SHAPE,
            StaticCheckCode.AST_TYPES,
            StaticCheckCode.FEATURE_DEPENDENCY,
            StaticCheckCode.HANDLER_HASH,
            StaticCheckCode.ACTION_PARAMETERS,
            StaticCheckCode.TERMINAL_ACTION,
            StaticCheckCode.SYSTEM_OBLIGATION
        ), results.stream().map(StaticCheckResult::checkCode).toList());
        assertEquals(8, results.size());
        results.forEach(result -> {
            assertEquals(StaticCheckStatus.PASSED, result.checkStatus());
            assertEquals(StaticCheckSeverity.INFO, result.severity());
            assertNull(result.errorCode());
            assertNull(result.errorSummary());
        });
        assertThrows(UnsupportedOperationException.class, () -> results.clear());
    }

    @Test
    void test_foundationCanonicalizationFailureRemainsUnexpected() {
        DecisionReleaseClosure valid = validClosure();
        RuleSetVersionProjection ruleSet = valid.ruleSet();
        RuleSetVersionProjection oversized = new RuleSetVersionProjection(ruleSet.pointCode(),
            ruleSet.ruleSetCode(), "x".repeat(262_145), ruleSet.versionNo(), ruleSet.rules());

        JsonCanonicalizationException error = assertThrows(JsonCanonicalizationException.class,
            () -> validator.validate(new DecisionReleaseClosure(valid.point(), oversized, valid.release(),
                valid.persistedActions())));

        assertEquals("JSON canonicalization failed", error.getMessage());
    }

    @Test
    void test_corruptClosureStillEvaluatesChecksAfterFirstFailure() {
        DecisionReleaseClosure valid = validClosure();
        DecisionReleaseProjection release = valid.release();
        ReleaseActionVersionProjection action = release.actionVersions().getFirst();
        DecisionReleaseProjection corrupt = new DecisionReleaseProjection(
            release.releaseId(), release.releaseNo(), "999999", release.pointVersionId(), release.environmentCode(),
            release.decisionPoint(), release.pointVersion(), release.inputFields(), release.ruleSetBindings(),
            release.ruleSetVersions(), release.rules(),
            List.of(new ReleaseNodeProjection("2", "2", "404", NodeType.PREDICATE, null,
                ComparisonOperator.GT, 1)),
            List.of(new ReleaseOperandProjection("2", "2", OperandRole.LEFT, 1, OperandSource.CONTEXT, null,
                "1", DecisionValueType.STRING, null, null, null, null, null)),
            List.of(new ReleaseActionVersionProjection(action.actionVersionId(), action.actionCode(),
                action.actionCategory(), action.versionNo(), action.handlerCode(), action.implementationVersion(),
                "0".repeat(64), false, action.decisionCode(), action.mergePolicy(), action.shadowSupportedFlag())),
            release.ruleActions(), List.of(new UnsupportedProjection("feature")),
            List.of(new UnsupportedProjection("dependency")), List.of(new UnsupportedProjection("definition")),
            List.of(new UnsupportedProjection("parameter")), List.of(new UnsupportedProjection("obligation")),
            List.of(new UnsupportedProjection("limit")));
        List<PersistedActionVersion> drifted = List.of(new PersistedActionVersion(240001, "ALLOW_CHECKOUT", 1,
            "DECISION_ALLOW_V1", "1.0.0", "0".repeat(64), ActionCategory.DECISION, true,
            DecisionValue.ALLOW, MergePolicy.MOST_RESTRICTIVE, true, ConfigurationStatus.ACTIVE));

        List<StaticCheckResult> results = validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), corrupt, drifted));

        assertEquals(8, results.size());
        assertEquals(StaticCheckStatus.FAILED, results.getFirst().checkStatus());
        assertTrue(results.stream().skip(1).anyMatch(result -> result.checkStatus() == StaticCheckStatus.FAILED));
        assertEquals(StaticCheckStatus.FAILED,
            result(results, StaticCheckCode.HANDLER_HASH).checkStatus());
        assertEquals(StaticCheckStatus.FAILED,
            result(results, StaticCheckCode.SYSTEM_OBLIGATION).checkStatus());
        results.stream().filter(result -> result.checkStatus() == StaticCheckStatus.FAILED).forEach(result -> {
            assertEquals(StaticCheckSeverity.ERROR, result.severity());
            assertTrue(result.errorSummary().startsWith("first="));
            assertTrue(result.errorSummary().contains("; violation_count="));
            assertTrue(result.errorSummary().length() <= 1000);
        });
    }

    @Test
    void test_violationTupleUsesFixtureOrderDedupAndSafeBoundedSummary() {
        JsonNode vector = discriminator("violation-order-null-dot-underscore-id");
        ArrayList<StaticViolation> violations = new ArrayList<>();
        for (JsonNode value : vector.get("violations")) {
            violations.add(new StaticViolation(value.get("errorCode").asText(), nullable(value, "path"),
                nullable(value, "ruleCode"), value.get("nodeId").isNull() ? null : value.get("nodeId").asLong(),
                nullable(value, "actionCode")));
        }
        StaticCheckResult result = DecisionReleaseStaticValidatorTestAccess.summarize(
            StaticCheckCode.AST_SHAPE, violations);
        JsonNode expected = vector.get("expected");

        assertEquals(StaticCheckStatus.FAILED, result.checkStatus());
        assertEquals(StaticCheckSeverity.ERROR, result.severity());
        assertEquals(expected.get("first").get("errorCode").asText(), result.errorCode());
        assertEquals(expected.get("summary").asText(), result.errorSummary());
        assertEquals(expected.get("uniqueCount").asInt(),
            Integer.parseInt(result.errorSummary().substring(result.errorSummary().lastIndexOf('=') + 1)));

        StaticCheckResult bounded = DecisionReleaseStaticValidatorTestAccess.summarize(StaticCheckCode.AST_SHAPE,
            List.of(new StaticViolation("VERY_LONG", "x".repeat(2000), null, 2L, null)));
        assertTrue(bounded.errorSummary().length() <= 1000);
        assertFalse(bounded.errorSummary().contains("SECRET_LITERAL"));
    }

    @Test
    void test_persistedSummaryContractAcceptsGoldenSafeDotPath() {
        assertTrue(StaticCheckErrorSummary.isCanonical(
            "AST_PATH", "first=AST_PATH@node.2; violation_count=5"));
    }

    @Test
    void test_handlerAstTypeAndTerminalChecksOwnTheirExactFailures() {
        DecisionReleaseClosure valid = validClosure();
        PersistedActionVersion persisted = valid.persistedActions().getFirst();
        List<StaticCheckResult> duplicateCatalog = validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), valid.release(), List.of(persisted, persisted)));
        assertEquals(StaticCheckStatus.FAILED,
            result(duplicateCatalog, StaticCheckCode.HANDLER_HASH).checkStatus());

        DecisionReleaseProjection release = valid.release();
        ReleaseRuleProjection disabled = new ReleaseRuleProjection(release.rules().getFirst().ruleId(),
            release.rules().getFirst().ruleSetVersionId(), release.rules().getFirst().ruleCode(),
            release.rules().getFirst().priorityNo(), false, release.rules().getFirst().reasonCode());
        DecisionReleaseProjection noAction = copyRelease(release, List.of(disabled), null, null, null, List.of());
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), noAction, valid.persistedActions())),
            StaticCheckCode.TERMINAL_ACTION).checkStatus());

        ReleaseNodeProjection node = release.nodes().getFirst();
        DecisionReleaseProjection orderedString = copyRelease(release, null,
            List.of(new ReleaseNodeProjection(node.nodeId(), node.ruleId(), node.parentNodeId(), node.nodeType(),
                node.groupOperator(), ComparisonOperator.GT, node.sequenceNo())), null, null, null);
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), orderedString, valid.persistedActions())),
            StaticCheckCode.AST_TYPES).checkStatus());
    }

    @Test
    void test_persistedLiteralValuesMustAlreadyBeCanonicalAndFieldValid() {
        DecisionReleaseClosure valid = validClosure();
        DecisionReleaseProjection release = valid.release();
        ReleaseNodeProjection node = release.nodes().getFirst();
        List<ReleaseOperandProjection> nonCanonicalNumber = List.of(
            new ReleaseOperandProjection("2", node.nodeId(), OperandRole.LEFT, 1, OperandSource.CONTEXT,
                null, "6", DecisionValueType.NUMBER, null, null, null, null, null),
            new ReleaseOperandProjection("3", node.nodeId(), OperandRole.RIGHT, 1, OperandSource.LITERAL,
                null, null, DecisionValueType.NUMBER, null, "1.0", null, null, null));
        DecisionReleaseProjection numeric = copyRelease(release, null, null, nonCanonicalNumber, null, null);
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), numeric, valid.persistedActions())), StaticCheckCode.AST_TYPES).checkStatus());

        ReleaseOperandProjection source = release.operands().getFirst();
        ReleaseOperandProjection literal = release.operands().get(1);
        DecisionReleaseProjection invalidCountry = copyRelease(release, null, null, List.of(source,
            new ReleaseOperandProjection(literal.operandId(), literal.nodeId(), literal.operandRole(),
                literal.valueSequence(), literal.sourceType(), literal.featureVersionId(), literal.inputFieldId(),
                literal.valueType(), "ae", null, null, null, null)), null, null);
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), invalidCountry, valid.persistedActions())),
            StaticCheckCode.AST_TYPES).checkStatus());
    }

    @Test
    void test_datetimeLiteralUsesSharedCanonicalUtcMicrosecondGoldenVector() {
        DecisionReleaseClosure valid = validClosure();
        JsonNode vector = discriminator("utc-duration");
        vector.get("acceptedUtc").forEach(value -> assertEquals(StaticCheckStatus.PASSED,
            result(validator.validate(new DecisionReleaseClosure(valid.point(), valid.ruleSet(),
                withDatetimeLiteral(valid.release(), value.asText()), valid.persistedActions())),
                StaticCheckCode.AST_TYPES).checkStatus()));
        vector.get("rejectedUtc").forEach(value -> assertEquals(StaticCheckStatus.FAILED,
            result(validator.validate(new DecisionReleaseClosure(valid.point(), valid.ruleSet(),
                withDatetimeLiteral(valid.release(), value.asText()), valid.persistedActions())),
                StaticCheckCode.AST_TYPES).checkStatus()));
    }

    private static DecisionReleaseProjection withDatetimeLiteral(DecisionReleaseProjection release, String value) {
        ReleaseInputFieldProjection sourceField = release.inputFields().getFirst();
        ArrayList<ReleaseInputFieldProjection> fields = new ArrayList<>(release.inputFields());
        fields.set(0, new ReleaseInputFieldProjection(sourceField.inputFieldId(), sourceField.pointVersionId(),
            sourceField.fieldCode(), DecisionValueType.DATETIME, sourceField.requiredFlag(),
            sourceField.dataClassification(), sourceField.ruleReferenceFlag(), sourceField.actionParameterFlag()));
        ReleaseNodeProjection node = release.nodes().getFirst();
        List<ReleaseOperandProjection> operands = List.of(
            new ReleaseOperandProjection("2", node.nodeId(), OperandRole.LEFT, 1, OperandSource.CONTEXT,
                null, sourceField.inputFieldId(), DecisionValueType.DATETIME, null, null, null, null, null),
            new ReleaseOperandProjection("3", node.nodeId(), OperandRole.RIGHT, 1, OperandSource.LITERAL,
                null, null, DecisionValueType.DATETIME, null, null, null, value, null));
        return new DecisionReleaseProjection(release.releaseId(), release.releaseNo(), release.decisionPointId(),
            release.pointVersionId(), release.environmentCode(), release.decisionPoint(), release.pointVersion(),
            fields, release.ruleSetBindings(), release.ruleSetVersions(), release.rules(), release.nodes(), operands,
            release.actionVersions(), release.ruleActions(), release.features(), release.featureDependencies(),
            release.actionParameterDefinitions(), release.ruleActionParameters(), release.obligations(),
            release.limitBindings());
    }

    @Test
    void test_astTypesRejectsLiteralWhoseTypeDiffersFromItsUniqueContextField() {
        DecisionReleaseClosure valid = validClosure();
        DecisionReleaseProjection release = valid.release();
        ReleaseOperandProjection literal = release.operands().get(1);
        List<ReleaseOperandProjection> mismatched = List.of(
            new ReleaseOperandProjection("2", "2", OperandRole.LEFT, 1, OperandSource.CONTEXT,
                null, "8", DecisionValueType.NUMBER, null, null, null, null, null),
            new ReleaseOperandProjection(literal.operandId(), literal.nodeId(), literal.operandRole(),
                literal.valueSequence(), literal.sourceType(), literal.featureVersionId(), literal.inputFieldId(),
                DecisionValueType.STRING, "AE", null, null, null, null));

        StaticCheckResult result = result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), copyRelease(release, null, null, mismatched, null, null),
            valid.persistedActions())), StaticCheckCode.AST_TYPES);

        assertEquals(StaticCheckStatus.FAILED, result.checkStatus());
        assertEquals("LITERAL_TYPE", result.errorCode());
    }

    @Test
    void test_astShapeOwnsCatalogUniquenessNullMetadataAndFrozenLimits() {
        DecisionReleaseClosure valid = validClosure();
        DecisionReleaseProjection release = valid.release();
        ReleaseRuleProjection firstRule = release.rules().getFirst();
        ReleaseNodeProjection firstNode = release.nodes().getFirst();
        ReleaseRuleProjection duplicateCode = new ReleaseRuleProjection("99", firstRule.ruleSetVersionId(),
            firstRule.ruleCode(), 2, true, null);
        ReleaseNodeProjection duplicateRoot = new ReleaseNodeProjection("99", "99", null, NodeType.PREDICATE,
            null, ComparisonOperator.IS_NULL, 1);
        DecisionReleaseProjection duplicate = copyRelease(release, List.of(firstRule, duplicateCode),
            List.of(firstNode, duplicateRoot), release.operands(), null, null);
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), duplicate, valid.persistedActions())),
            StaticCheckCode.AST_SHAPE).checkStatus());

        ReleaseOperandProjection operand = release.operands().getFirst();
        DecisionReleaseProjection nullMetadata = copyRelease(release, null, null, List.of(
            new ReleaseOperandProjection(operand.operandId(), operand.nodeId(), null, operand.valueSequence(),
                null, operand.featureVersionId(), operand.inputFieldId(), null, operand.stringValue(),
                operand.numberValue(), operand.booleanValue(), operand.datetimeValue(), operand.durationValue()),
            release.operands().get(1)), null, null);
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), nullMetadata, valid.persistedActions())),
            StaticCheckCode.AST_SHAPE).checkStatus());

        ArrayList<ReleaseNodeProjection> tooManyNodes = new ArrayList<>();
        tooManyNodes.add(new ReleaseNodeProjection("1", firstRule.ruleId(), null, NodeType.GROUP,
            GroupOperator.AND, null, 1));
        for (int index = 1; index <= 2000; index++) {
            tooManyNodes.add(new ReleaseNodeProjection(Integer.toString(index + 1), firstRule.ruleId(), "1",
                NodeType.PREDICATE, null, ComparisonOperator.IS_NULL, index));
        }
        DecisionReleaseProjection oversized = copyRelease(release, null, tooManyNodes, List.of(), null, null);
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), oversized, valid.persistedActions())),
            StaticCheckCode.AST_SHAPE).checkStatus());
    }

    @Test
    void test_referenceIntegrityRequiresFullPointTestHeaderNoOrphansAndSemanticClosure() {
        DecisionReleaseClosure valid = validClosure();
        DecisionReleaseProjection release = valid.release();

        DecisionReleaseProjection production = new DecisionReleaseProjection(release.releaseId(), 0,
            release.decisionPointId(), release.pointVersionId(), DecisionEnvironment.PRODUCTION, release.decisionPoint(),
            release.pointVersion(), release.inputFields(), release.ruleSetBindings(), release.ruleSetVersions(),
            release.rules(), release.nodes(), release.operands(), release.actionVersions(), release.ruleActions(),
            release.features(), release.featureDependencies(), release.actionParameterDefinitions(),
            release.ruleActionParameters(), release.obligations(), release.limitBindings());
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), production, valid.persistedActions())),
            StaticCheckCode.REFERENCE_INTEGRITY).checkStatus());

        ReleaseActionVersionProjection action = release.actionVersions().getFirst();
        ReleaseActionVersionProjection orphan = new ReleaseActionVersionProjection("240002", "REVIEW_CHECKOUT",
            ActionCategory.DECISION, 1, "DECISION_REVIEW_V1", "1.0.0",
            "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716", true,
            DecisionValue.REVIEW, MergePolicy.MOST_RESTRICTIVE, true);
        DecisionReleaseProjection orphanAction = copyRelease(release, null, null, null, List.of(action, orphan), null);
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), orphanAction, valid.persistedActions())),
            StaticCheckCode.REFERENCE_INTEGRITY).checkStatus());

        ReleaseOperandProjection source = release.operands().getFirst();
        ReleaseOperandProjection literal = release.operands().get(1);
        DecisionReleaseProjection semanticDrift = copyRelease(release, null, null, List.of(source,
            new ReleaseOperandProjection(literal.operandId(), literal.nodeId(), literal.operandRole(),
                literal.valueSequence(), literal.sourceType(), literal.featureVersionId(), literal.inputFieldId(),
                literal.valueType(), "US", null, null, null, null)), null, null);
        assertEquals(StaticCheckStatus.FAILED, result(validator.validate(new DecisionReleaseClosure(
            valid.point(), valid.ruleSet(), semanticDrift, valid.persistedActions())),
            StaticCheckCode.REFERENCE_INTEGRITY).checkStatus());
    }

    @Test
    void test_eightCheckMixedOutcomeDiscriminatorUsesFixedOrder() {
        JsonNode vector = discriminator("eight-static-check-order");
        ArrayList<StaticCheckResult> actual = new ArrayList<>();
        for (StaticCheckCode code : StaticCheckCode.values()) {
            JsonNode input = null;
            for (JsonNode candidate : vector.get("inputChecks")) {
                if (candidate.get("checkCode").asText().equals(code.getCode())) input = candidate;
            }
            ArrayList<StaticViolation> violations = new ArrayList<>();
            input.get("violations").forEach(value -> violations.add(new StaticViolation(
                value.get("errorCode").asText(), nullable(value, "path"), nullable(value, "ruleCode"),
                value.get("nodeId").isNull() ? null : value.get("nodeId").asLong(), nullable(value, "actionCode"))));
            actual.add(DecisionReleaseStaticValidatorTestAccess.summarize(code, violations));
        }
        JsonNode expected = vector.get("expected").get("results");
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < actual.size(); index++) {
            StaticCheckResult row = actual.get(index);
            JsonNode value = expected.get(index);
            assertEquals(value.get("checkCode").asText(), row.checkCode().getCode());
            assertEquals(value.get("checkStatus").asText(), row.checkStatus().getCode());
            assertEquals(value.get("severity").asText(), row.severity().getCode());
            assertEquals(nullable(value, "errorCode"), row.errorCode());
            assertEquals(nullable(value, "errorSummary"), row.errorSummary());
        }
    }

    private static DecisionReleaseProjection copyRelease(
        DecisionReleaseProjection release,
        List<ReleaseRuleProjection> rules,
        List<ReleaseNodeProjection> nodes,
        List<ReleaseOperandProjection> operands,
        List<ReleaseActionVersionProjection> actions,
        List<ReleaseRuleActionProjection> ruleActions
    ) {
        return new DecisionReleaseProjection(release.releaseId(), release.releaseNo(), release.decisionPointId(),
            release.pointVersionId(), release.environmentCode(), release.decisionPoint(), release.pointVersion(),
            release.inputFields(), release.ruleSetBindings(), release.ruleSetVersions(),
            rules == null ? release.rules() : rules, nodes == null ? release.nodes() : nodes,
            operands == null ? release.operands() : operands, actions == null ? release.actionVersions() : actions,
            ruleActions == null ? release.ruleActions() : ruleActions, release.features(), release.featureDependencies(),
            release.actionParameterDefinitions(), release.ruleActionParameters(), release.obligations(),
            release.limitBindings());
    }

    private static DecisionReleaseClosure validClosure() {
        CheckoutPrepareAuthoringDescriptor descriptor = CheckoutPrepareAuthoringDescriptor.v1();
        List<InputFieldProjection> pointFields = descriptor.fields().stream().map(field -> new InputFieldProjection(
            field.fieldCode(), field.valueType(), field.requiredFlag(), field.dataClassification(),
            field.ruleReferenceFlag(), field.actionParameterFlag())).toList();
        RuleSetVersionProjection ruleSet = new RuleSetVersionProjection("CHECKOUT_PREPARE", "CHECKOUT_DEFAULT",
            "Checkout rules", 1, List.of(new RuleProjection("COUNTRY_ALLOW", "Country allow", 1, true, null,
                new PredicateProjection(ComparisonOperator.EQ, List.of(
                    new OperandProjection(OperandRole.LEFT, 1, OperandSource.CONTEXT, "country_code",
                        DecisionValueType.STRING, NullValue.INSTANCE),
                    new OperandProjection(OperandRole.RIGHT, 1, OperandSource.LITERAL, null,
                        DecisionValueType.STRING, new StringValue("AE")))),
                new ActionProjection("ALLOW_CHECKOUT", 1, "DECISION_ALLOW_V1", "1.0.0",
                    "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1",
                    ActionCategory.DECISION, true, DecisionValue.ALLOW, MergePolicy.MOST_RESTRICTIVE, true))));
        RiskDecisionCanonicalizerImpl canonicalizer = new RiskDecisionCanonicalizerImpl();
        String ruleSetHash = canonicalizer.ruleSetVersion(ruleSet).sha256LowerHex();
        DecisionPointVersionProjection point = new DecisionPointVersionProjection("CHECKOUT_PREPARE",
            DecisionBusinessDomain.CHECKOUT, 1, "ORDER_PREPARATION", 4, "RISK_DECISION_RESULT",
            DecisionValue.REVIEW, EvaluationStrategy.FIRST_TERMINAL, FailureStrategy.FAIL_CLOSED, 500,
            pointFields, List.of(new RuleSetBindingProjection(1, "CHECKOUT_DEFAULT", 1, ruleSetHash)),
            List.of(), List.of(), List.of());
        String pointHash = canonicalizer.decisionPointVersion(point).sha256LowerHex();

        List<ReleaseInputFieldProjection> releaseFields = new ArrayList<>();
        for (int index = 0; index < pointFields.size(); index++) {
            InputFieldProjection field = pointFields.get(index);
            releaseFields.add(new ReleaseInputFieldProjection(Integer.toString(index + 1), "220001", field.fieldCode(),
                field.valueType(), field.requiredFlag(), field.dataClassification(), field.ruleReferenceFlag(),
                field.actionParameterFlag()));
        }
        DecisionReleaseProjection release = new DecisionReleaseProjection("900001", 1, "210001", "220001",
            DecisionEnvironment.TEST,
            new ReleaseDecisionPointProjection("210001", "CHECKOUT_PREPARE", DecisionBusinessDomain.CHECKOUT),
            new ReleasePointVersionProjection("210001", "CHECKOUT_PREPARE", "220001", 1,
                "ORDER_PREPARATION", 4, "RISK_DECISION_RESULT", DecisionValue.REVIEW,
                EvaluationStrategy.FIRST_TERMINAL, FailureStrategy.FAIL_CLOSED, 500, pointHash),
            releaseFields,
            List.of(new ReleaseRuleSetBindingProjection("300001", "220001", "230001", 1)),
            List.of(new ReleaseRuleSetVersionProjection("230001", "210001", "CHECKOUT_DEFAULT", 1,
                ruleSetHash)),
            List.of(new ReleaseRuleProjection("2", "230001", "COUNTRY_ALLOW", 1, true, null)),
            List.of(new ReleaseNodeProjection("2", "2", null, NodeType.PREDICATE, null,
                ComparisonOperator.EQ, 1)),
            List.of(
                new ReleaseOperandProjection("2", "2", OperandRole.LEFT, 1, OperandSource.CONTEXT, null, "1",
                    DecisionValueType.STRING, null, null, null, null, null),
                new ReleaseOperandProjection("3", "2", OperandRole.RIGHT, 1, OperandSource.LITERAL, null, null,
                    DecisionValueType.STRING, "AE", null, null, null, null)),
            List.of(new ReleaseActionVersionProjection("240001", "ALLOW_CHECKOUT", ActionCategory.DECISION, 1,
                "DECISION_ALLOW_V1", "1.0.0",
                "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1",
                true, DecisionValue.ALLOW, MergePolicy.MOST_RESTRICTIVE, true)),
            List.of(new ReleaseRuleActionProjection("4", "2", "240001", 1)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        List<PersistedActionVersion> persisted = List.of(new PersistedActionVersion(240001, "ALLOW_CHECKOUT", 1,
            "DECISION_ALLOW_V1", "1.0.0",
            "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1",
            ActionCategory.DECISION, true, DecisionValue.ALLOW, MergePolicy.MOST_RESTRICTIVE, true,
            ConfigurationStatus.ACTIVE));
        return new DecisionReleaseClosure(point, ruleSet, release, persisted);
    }

    private static StaticCheckResult result(List<StaticCheckResult> values, StaticCheckCode code) {
        return values.stream().filter(value -> value.checkCode() == code).findFirst().orElseThrow();
    }

    private static String nullable(JsonNode node, String field) {
        return node.get(field).isNull() ? null : node.get(field).asText();
    }

    private static JsonNode discriminator(String id) {
        JsonNode fixture;
        try (InputStream input = DecisionReleaseStaticValidatorAutoTest.class.getResourceAsStream(
            "/fixtures/risk-decision-v1-golden-vectors.json")) {
            if (input == null) {
                throw new IllegalStateException("缺少 Risk Decision 黄金向量");
            }
            fixture = JSON.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Risk Decision 黄金向量", exception);
        }
        for (JsonNode value : fixture.get("discriminators")) {
            if (value.get("id").asText().equals(id)) {
                return value;
            }
        }
        throw new IllegalArgumentException(id);
    }
}
