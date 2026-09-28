package com.paytech.risk.auto.domain;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DataClassification;
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
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.BooleanValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.CanonicalHash;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.CommandActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.CommandRuleProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.CreateRuleSetVersionCommandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.CreateTestReleaseCommandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionPointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionReleaseProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.FreezeRuleSetVersionCommandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.GroupProjection;
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
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ValidateTestReleaseCommandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerTestAccess;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskDecisionCanonicalizerAutoTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final JsonNode FIXTURE = loadFixture();

    private final RiskDecisionCanonicalizer canonicalizer = new RiskDecisionCanonicalizerImpl();

    @Test
    void test_allSevenSchemaBoundPrimaryTriplesMatchFixtureBytesAndHashes() {
        assertExpected(canonicalizer.ruleSetVersion(mapRuleSet(projection("rule-set-version-primary").get("input"))),
            expected("rule-set-version-primary"));
        assertExpected(canonicalizer.decisionPointVersion(mapPointVersion(
            projection("decision-point-version-primary").get("input"))), expected("decision-point-version-primary"));
        assertExpected(canonicalizer.decisionRelease(mapRelease(projection("decision-release-primary").get("input"))),
            expected("decision-release-primary"));

        JsonNode createInput = projection("create-rule-set-primary").get("input");
        assertExpected(canonicalizer.createRuleSetVersionCommand(mapCreate(createInput)), expected("create-rule-set-primary"));
        assertExpected(canonicalizer.freezeRuleSetVersionCommand(mapFreeze(
            projection("freeze-rule-set-primary").get("input"))), expected("freeze-rule-set-primary"));
        assertExpected(canonicalizer.createTestReleaseCommand(mapCreateRelease(
            projection("create-test-release-primary").get("input"))), expected("create-test-release-primary"));
        assertExpected(canonicalizer.validateTestReleaseCommand(mapValidate(
            projection("validate-test-release-primary").get("input"))), expected("validate-test-release-primary"));
    }

    @Test
    void test_createSemanticPermutationsNormalizeNfcAndChangedMeaningChangesHash() {
        JsonNode discriminator = discriminator("semantic-array-permutation");
        CanonicalHash first = canonicalizer.createRuleSetVersionCommand(mapCreate(
            discriminator.get("equivalentInputs").get(0)));
        CanonicalHash second = canonicalizer.createRuleSetVersionCommand(mapCreate(
            discriminator.get("equivalentInputs").get(1)));
        CanonicalHash changed = canonicalizer.createRuleSetVersionCommand(mapCreate(discriminator.get("changedInput")));

        assertEquals(discriminator.get("expectedSameHash").asText(), first.sha256LowerHex());
        assertEquals(first.sha256LowerHex(), second.sha256LowerHex());
        assertArrayEquals(first.canonicalUtf8(), second.canonicalUtf8());
        assertEquals(discriminator.get("expectedDifferentHash").asText(), changed.sha256LowerHex());
        assertNotEquals(first.sha256LowerHex(), changed.sha256LowerHex());
        assertEquals(expected("create-rule-set-primary").get("canonicalJson").asText(),
            new String(first.canonicalUtf8(), StandardCharsets.UTF_8));
    }

    @Test
    void test_writerUsesUtf16KeyOrderStrictSurrogatesAndDefensiveBytes() {
        JsonNode vector = discriminator("jcs-utf16-key-order");
        CanonicalHash hash = RiskDecisionCanonicalizerTestAccess.canonicalizeKeyValueObject(List.of(
            new RiskDecisionCanonicalizerTestAccess.KeyValue("\uE000", "private-use"),
            new RiskDecisionCanonicalizerTestAccess.KeyValue("\uD800\uDC00", "supplementary")
        ));
        assertExpected(hash, vector.get("expected"));

        byte[] first = hash.canonicalUtf8();
        first[0] = 0;
        assertEquals('{', hash.canonicalUtf8()[0]);
        assertThrows(InvalidParameterException.class,
            () -> RiskDecisionCanonicalizerTestAccess.canonicalizeKeyValueObject(List.of(
                new RiskDecisionCanonicalizerTestAccess.KeyValue("value", "\uD800"))));
        assertThrows(InvalidParameterException.class,
            () -> RiskDecisionCanonicalizerTestAccess.canonicalizeKeyValueObject(List.of(
                new RiskDecisionCanonicalizerTestAccess.KeyValue("value", "\uDC00"))));
    }

    @Test
    void test_nfcAndUnpairedSurrogateDiscriminatorsMatchFixture() {
        JsonNode nfc = discriminator("nfc-equivalence");
        CanonicalHash first = RiskDecisionCanonicalizerTestAccess.canonicalizeKeyValueObject(List.of(
            new RiskDecisionCanonicalizerTestAccess.KeyValue("schema", "NFC_EQUIVALENCE_V1"),
            new RiskDecisionCanonicalizerTestAccess.KeyValue("name", nfc.get("inputs").get(0).get("name").asText())));
        CanonicalHash second = RiskDecisionCanonicalizerTestAccess.canonicalizeKeyValueObject(List.of(
            new RiskDecisionCanonicalizerTestAccess.KeyValue("schema", "NFC_EQUIVALENCE_V1"),
            new RiskDecisionCanonicalizerTestAccess.KeyValue("name", nfc.get("inputs").get(1).get("name").asText())));
        assertExpected(first, nfc.get("expected"));
        assertArrayEquals(first.canonicalUtf8(), second.canonicalUtf8());

        JsonNode surrogate = discriminator("unpaired-surrogate-rejected");
        surrogate.get("inputs").forEach(value -> assertThrows(InvalidParameterException.class,
            () -> RiskDecisionCanonicalizerTestAccess.canonicalizeKeyValueObject(List.of(
                new RiskDecisionCanonicalizerTestAccess.KeyValue("value", value.asText())))));
    }

    @Test
    void test_duplicateDecodedObjectPropertyFailsWithFoundationException() {
        JsonCanonicalizationException error = assertThrows(JsonCanonicalizationException.class,
            () -> RiskDecisionCanonicalizerTestAccess.canonicalizeKeyValueObject(List.of(
                new RiskDecisionCanonicalizerTestAccess.KeyValue("a", "literal"),
                new RiskDecisionCanonicalizerTestAccess.KeyValue("\u0061", "escaped"))));

        assertEquals("JSON canonicalization failed", error.getMessage());
    }

    @Test
    void test_foundationStringLimitFailureIsNotMappedToInvalidParameter() {
        RuleSetVersionProjection source = mapRuleSet(projection("rule-set-version-primary").get("input"));
        RuleSetVersionProjection oversized = new RuleSetVersionProjection(source.pointCode(), source.ruleSetCode(),
            "x".repeat(262_145), source.versionNo(), source.rules());

        JsonCanonicalizationException error = assertThrows(JsonCanonicalizationException.class,
            () -> canonicalizer.ruleSetVersion(oversized));

        assertEquals("JSON canonicalization failed", error.getMessage());
    }

    @Test
    void test_releaseIdsSortNumericallyAndExplicitNullPropertiesRemainPresent() {
        CanonicalHash release = canonicalizer.decisionRelease(mapRelease(projection("decision-release-primary").get("input")));
        String json = new String(release.canonicalUtf8(), StandardCharsets.UTF_8);
        assertEquals(expected("decision-release-primary").get("canonicalJson").asText(), json);
        assertEquals(discriminator("numeric-id-sort-2-before-10").get("expectedIds").get(0).asText(), "2");
        assertEquals(discriminator("numeric-id-sort-2-before-10").get("expectedIds").get(1).asText(), "10");
        assertEquals(true, json.indexOf("\"nodeId\":\"2\"") < json.indexOf("\"nodeId\":\"10\""));
        assertEquals(true, json.contains("\"parentNodeId\":null"));
        assertEquals(true, json.contains("\"numberValue\":null"));
        assertEquals(true, json.contains("\"featureVersionId\":null"));
    }

    @Test
    void test_duplicateNumericIdsUseFullCanonicalRowTieBreakInsteadOfArrivalOrder() {
        DecisionReleaseProjection release = mapRelease(projection("decision-release-primary").get("input"));
        ReleaseNodeProjection first = release.nodes().getFirst();
        ReleaseNodeProjection sameId = new ReleaseNodeProjection(first.nodeId(), first.ruleId(), first.parentNodeId(),
            first.nodeType(), first.groupOperator(), first.comparisonOperator(), first.sequenceNo() + 1);
        ArrayList<ReleaseNodeProjection> forward = new ArrayList<>(release.nodes());
        forward.add(sameId);
        ArrayList<ReleaseNodeProjection> reversed = new ArrayList<>(forward);
        java.util.Collections.reverse(reversed);

        CanonicalHash firstHash = canonicalizer.decisionRelease(withNodes(release, forward));
        CanonicalHash secondHash = canonicalizer.decisionRelease(withNodes(release, reversed));
        assertArrayEquals(firstHash.canonicalUtf8(), secondHash.canonicalUtf8());
        assertEquals(firstHash.sha256LowerHex(), secondHash.sha256LowerHex());
    }

    @Test
    void test_invalidPublicProjectionInputsFailClosedWithDomainException() {
        assertThrows(InvalidParameterException.class, () -> canonicalizer.ruleSetVersion(null));
        assertThrows(InvalidParameterException.class,
            () -> new RuleSetVersionProjection("CHECKOUT_PREPARE", "RULE_SET", "Rule Set", 1, null));

        RuleSetVersionProjection source = mapRuleSet(projection("rule-set-version-primary").get("input"));
        RuleProjection rule = source.rules().getFirst();
        ActionProjection action = rule.action();
        ActionProjection missingCategory = new ActionProjection(action.actionCode(), action.actionVersionNo(),
            action.handlerCode(), action.implementationVersion(), action.implementationHash(), null,
            action.terminalFlag(), action.decisionCode(), action.mergePolicy(), action.shadowSupportedFlag());
        RuleProjection invalidRule = new RuleProjection(rule.ruleCode(), rule.ruleName(), rule.priorityNo(),
            rule.enabledFlag(), rule.reasonCode(), rule.condition(), missingCategory);

        assertThrows(InvalidParameterException.class, () -> canonicalizer.ruleSetVersion(
            new RuleSetVersionProjection(source.pointCode(), source.ruleSetCode(), source.ruleSetName(),
                source.versionNo(), List.of(invalidRule))));
    }

    @Test
    void test_datetimeLiteralUsesSharedCanonicalUtcMicrosecondGoldenVector() {
        JsonNode vector = discriminator("utc-duration");
        vector.get("acceptedUtc").forEach(value ->
            canonicalizer.ruleSetVersion(withDatetimeLiteral(value.asText())));
        vector.get("rejectedUtc").forEach(value -> {
            InvalidParameterException error = assertThrows(InvalidParameterException.class,
                () -> canonicalizer.ruleSetVersion(withDatetimeLiteral(value.asText())));
            assertFalse(error.getMessage().contains(value.asText()));
        });
    }

    private static RuleSetVersionProjection withDatetimeLiteral(String value) {
        RuleSetVersionProjection source = mapRuleSet(projection("rule-set-version-primary").get("input"));
        RuleProjection sourceRule = source.rules().getFirst();
        PredicateProjection condition = new PredicateProjection(ComparisonOperator.EQ, List.of(
            new OperandProjection(OperandRole.LEFT, 1, OperandSource.CONTEXT, "event_time",
                DecisionValueType.DATETIME, NullValue.INSTANCE),
            new OperandProjection(OperandRole.RIGHT, 1, OperandSource.LITERAL, null,
                DecisionValueType.DATETIME, new StringValue(value))));
        RuleProjection rule = new RuleProjection(sourceRule.ruleCode(), sourceRule.ruleName(), sourceRule.priorityNo(),
            sourceRule.enabledFlag(), sourceRule.reasonCode(), condition, sourceRule.action());
        return new RuleSetVersionProjection(source.pointCode(), source.ruleSetCode(), source.ruleSetName(),
            source.versionNo(), List.of(rule));
    }

    private static DecisionReleaseProjection withNodes(
        DecisionReleaseProjection release,
        List<ReleaseNodeProjection> nodes
    ) {
        return new DecisionReleaseProjection(release.releaseId(), release.releaseNo(), release.decisionPointId(),
            release.pointVersionId(), release.environmentCode(), release.decisionPoint(), release.pointVersion(),
            release.inputFields(), release.ruleSetBindings(), release.ruleSetVersions(), release.rules(), nodes,
            release.operands(), release.actionVersions(), release.ruleActions(), release.features(),
            release.featureDependencies(), release.actionParameterDefinitions(), release.ruleActionParameters(),
            release.obligations(), release.limitBindings());
    }

    private static RuleSetVersionProjection mapRuleSet(JsonNode node) {
        return new RuleSetVersionProjection(text(node, "pointCode"), text(node, "ruleSetCode"),
            text(node, "ruleSetName"), integer(node, "versionNo"), mapList(node.get("rules"), value -> new RuleProjection(
                text(value, "ruleCode"), text(value, "ruleName"), integer(value, "priorityNo"),
                value.get("enabledFlag").asBoolean(), nullableText(value, "reasonCode"), mapCondition(value.get("condition")),
                mapAction(value.get("action")))));
    }

    private static DecisionPointVersionProjection mapPointVersion(JsonNode node) {
        return new DecisionPointVersionProjection(text(node, "pointCode"),
            DecisionBusinessDomain.valueOf(text(node, "businessDomain")), integer(node, "pointVersionNo"),
            text(node, "inputContractCode"), integer(node, "inputContractVersion"), text(node, "outputContractCode"),
            DecisionValue.valueOf(text(node, "defaultDecision")),
            EvaluationStrategy.valueOf(text(node, "evaluationStrategy")),
            FailureStrategy.valueOf(text(node, "failureStrategy")), integer(node, "timeoutMillis"),
            mapList(node.get("inputFields"), RiskDecisionCanonicalizerAutoTest::mapInputField),
            mapList(node.get("ruleSets"), value -> new RuleSetBindingProjection(integer(value, "sequenceNo"),
                text(value, "ruleSetCode"), integer(value, "ruleSetVersionNo"), text(value, "ruleSetContentHash"))),
            unsupported(node, "features"), unsupported(node, "obligations"), unsupported(node, "limitBindings"));
    }

    private static DecisionReleaseProjection mapRelease(JsonNode node) {
        JsonNode point = node.get("decisionPoint");
        JsonNode pointVersion = node.get("pointVersion");
        return new DecisionReleaseProjection(text(node, "releaseId"), integer(node, "releaseNo"),
            text(node, "decisionPointId"), text(node, "pointVersionId"),
            DecisionEnvironment.valueOf(text(node, "environmentCode")),
            new ReleaseDecisionPointProjection(text(point, "decisionPointId"), text(point, "pointCode"),
                DecisionBusinessDomain.valueOf(text(point, "businessDomain"))),
            new ReleasePointVersionProjection(text(pointVersion, "decisionPointId"), text(pointVersion, "pointCode"),
                text(pointVersion, "pointVersionId"), integer(pointVersion, "pointVersionNo"),
                text(pointVersion, "inputContractCode"), integer(pointVersion, "inputContractVersion"),
                text(pointVersion, "outputContractCode"), DecisionValue.valueOf(text(pointVersion, "defaultDecision")),
                EvaluationStrategy.valueOf(text(pointVersion, "evaluationStrategy")),
                FailureStrategy.valueOf(text(pointVersion, "failureStrategy")), integer(pointVersion, "timeoutMillis"),
                text(pointVersion, "contentHash")),
            mapList(node.get("inputFields"), value -> new ReleaseInputFieldProjection(text(value, "inputFieldId"),
                text(value, "pointVersionId"), text(value, "fieldCode"),
                DecisionValueType.valueOf(text(value, "valueType")), value.get("requiredFlag").asBoolean(),
                DataClassification.valueOf(text(value, "dataClassification")),
                value.get("ruleReferenceFlag").asBoolean(), value.get("actionParameterFlag").asBoolean())),
            mapList(node.get("ruleSetBindings"), value -> new ReleaseRuleSetBindingProjection(text(value, "bindingId"),
                text(value, "pointVersionId"), text(value, "ruleSetVersionId"), integer(value, "sequenceNo"))),
            mapList(node.get("ruleSetVersions"), value -> new ReleaseRuleSetVersionProjection(
                text(value, "ruleSetVersionId"), text(value, "decisionPointId"), text(value, "ruleSetCode"),
                integer(value, "versionNo"), text(value, "contentHash"))),
            mapList(node.get("rules"), value -> new ReleaseRuleProjection(text(value, "ruleId"),
                text(value, "ruleSetVersionId"), text(value, "ruleCode"), integer(value, "priorityNo"),
                value.get("enabledFlag").asBoolean(), nullableText(value, "reasonCode"))),
            mapList(node.get("nodes"), value -> new ReleaseNodeProjection(text(value, "nodeId"), text(value, "ruleId"),
                nullableText(value, "parentNodeId"), NodeType.valueOf(text(value, "nodeType")),
                nullableEnum(value, "groupOperator", GroupOperator.class),
                nullableEnum(value, "comparisonOperator", ComparisonOperator.class), integer(value, "sequenceNo"))),
            mapList(node.get("operands"), RiskDecisionCanonicalizerAutoTest::mapReleaseOperand),
            mapList(node.get("actionVersions"), value -> new ReleaseActionVersionProjection(
                text(value, "actionVersionId"), text(value, "actionCode"),
                ActionCategory.valueOf(text(value, "actionCategory")), integer(value, "versionNo"),
                text(value, "handlerCode"), text(value, "implementationVersion"), text(value, "implementationHash"),
                value.get("terminalFlag").asBoolean(), DecisionValue.valueOf(text(value, "decisionCode")),
                MergePolicy.valueOf(text(value, "mergePolicy")), value.get("shadowSupportedFlag").asBoolean())),
            mapList(node.get("ruleActions"), value -> new ReleaseRuleActionProjection(text(value, "ruleActionId"),
                text(value, "ruleId"), text(value, "actionVersionId"), integer(value, "sequenceNo"))),
            unsupported(node, "features"), unsupported(node, "featureDependencies"),
            unsupported(node, "actionParameterDefinitions"), unsupported(node, "ruleActionParameters"),
            unsupported(node, "obligations"), unsupported(node, "limitBindings"));
    }

    private static ReleaseOperandProjection mapReleaseOperand(JsonNode value) {
        return new ReleaseOperandProjection(text(value, "operandId"), text(value, "nodeId"),
            OperandRole.valueOf(text(value, "operandRole")), integer(value, "valueSequence"),
            OperandSource.valueOf(text(value, "sourceType")), nullableText(value, "featureVersionId"),
            nullableText(value, "inputFieldId"), DecisionValueType.valueOf(text(value, "valueType")),
            nullableText(value, "stringValue"), nullableText(value, "numberValue"),
            value.get("booleanValue").isNull() ? null : value.get("booleanValue").asBoolean(),
            nullableText(value, "datetimeValue"), nullableText(value, "durationValue"));
    }

    private static CreateRuleSetVersionCommandProjection mapCreate(JsonNode node) {
        return new CreateRuleSetVersionCommandProjection(text(node, "pointCode"), text(node, "ruleSetCode"),
            text(node, "ruleSetName"), nullableText(node, "baseRuleSetVersionId"),
            nullableText(node, "baseContentHash"), text(node, "changeReason"),
            mapList(node.get("rules"), value -> new CommandRuleProjection(text(value, "ruleCode"),
                text(value, "ruleName"), integer(value, "priorityNo"), value.get("enabledFlag").asBoolean(),
                nullableText(value, "reasonCode"), mapFlatCondition(value.get("conditionNodes")),
                new CommandActionProjection(text(value.get("action"), "actionCode"),
                    integer(value.get("action"), "actionVersionNo")))));
    }

    private static RiskDecisionCanonicalizer.ConditionProjection mapFlatCondition(JsonNode nodes) {
        List<JsonNode> all = new ArrayList<>();
        nodes.forEach(all::add);
        JsonNode root = all.stream().filter(value -> value.get("parentNodeKey").isNull()).findFirst().orElseThrow();
        return mapFlatNode(root, all);
    }

    private static RiskDecisionCanonicalizer.ConditionProjection mapFlatNode(JsonNode node, List<JsonNode> all) {
        if (text(node, "nodeType").equals("GROUP")) {
            List<JsonNode> children = all.stream()
                .filter(value -> !value.get("parentNodeKey").isNull()
                    && value.get("parentNodeKey").asText().equals(text(node, "nodeKey")))
                .sorted((left, right) -> Integer.compare(integer(left, "sequenceNo"), integer(right, "sequenceNo")))
                .toList();
            return new GroupProjection(GroupOperator.valueOf(text(node, "groupOperator")),
                children.stream().map(value -> mapFlatNode(value, all)).toList());
        }
        return new PredicateProjection(ComparisonOperator.valueOf(text(node, "comparisonOperator")),
            mapList(node.get("operands"), RiskDecisionCanonicalizerAutoTest::mapOperand));
    }

    private static RiskDecisionCanonicalizer.ConditionProjection mapCondition(JsonNode node) {
        if (text(node, "nodeType").equals("GROUP")) {
            return new GroupProjection(GroupOperator.valueOf(text(node, "groupOperator")),
                mapList(node.get("children"), RiskDecisionCanonicalizerAutoTest::mapCondition));
        }
        return new PredicateProjection(ComparisonOperator.valueOf(text(node, "comparisonOperator")),
            mapList(node.get("operands"), RiskDecisionCanonicalizerAutoTest::mapOperand));
    }

    private static OperandProjection mapOperand(JsonNode node) {
        JsonNode literal = node.get("literalValue");
        RiskDecisionCanonicalizer.LiteralProjection value = literal.isNull() ? NullValue.INSTANCE
            : literal.isBoolean() ? new BooleanValue(literal.asBoolean()) : new StringValue(literal.asText());
        return new OperandProjection(OperandRole.valueOf(text(node, "operandRole")), integer(node, "valueSequence"),
            OperandSource.valueOf(text(node, "sourceType")), nullableText(node, "fieldCode"),
            DecisionValueType.valueOf(text(node, "valueType")), value);
    }

    private static ActionProjection mapAction(JsonNode node) {
        return new ActionProjection(text(node, "actionCode"), integer(node, "actionVersionNo"),
            text(node, "handlerCode"), text(node, "implementationVersion"), text(node, "implementationHash"),
            ActionCategory.valueOf(text(node, "actionCategory")), node.get("terminalFlag").asBoolean(),
            DecisionValue.valueOf(text(node, "decisionCode")), MergePolicy.valueOf(text(node, "mergePolicy")),
            node.get("shadowSupportedFlag").asBoolean());
    }

    private static InputFieldProjection mapInputField(JsonNode node) {
        return new InputFieldProjection(text(node, "fieldCode"), DecisionValueType.valueOf(text(node, "valueType")),
            node.get("requiredFlag").asBoolean(), DataClassification.valueOf(text(node, "dataClassification")),
            node.get("ruleReferenceFlag").asBoolean(), node.get("actionParameterFlag").asBoolean());
    }

    private static FreezeRuleSetVersionCommandProjection mapFreeze(JsonNode node) {
        return new FreezeRuleSetVersionCommandProjection(text(node, "ruleSetVersionId"),
            text(node, "expectedContentHash"));
    }

    private static CreateTestReleaseCommandProjection mapCreateRelease(JsonNode node) {
        return new CreateTestReleaseCommandProjection(text(node, "ruleSetVersionId"),
            text(node, "expectedRuleSetContentHash"));
    }

    private static ValidateTestReleaseCommandProjection mapValidate(JsonNode node) {
        return new ValidateTestReleaseCommandProjection(text(node, "releaseId"), integer(node, "expectedVersion"));
    }

    private static List<UnsupportedProjection> unsupported(JsonNode parent, String name) {
        return mapList(parent.get(name), value -> new UnsupportedProjection(value.toString()));
    }

    private static <T> List<T> mapList(JsonNode array, java.util.function.Function<JsonNode, T> mapper) {
        ArrayList<T> values = new ArrayList<>();
        array.forEach(value -> values.add(mapper.apply(value)));
        return values;
    }

    private static <E extends Enum<E>> E nullableEnum(JsonNode node, String name, Class<E> type) {
        return node.get(name).isNull() ? null : Enum.valueOf(type, node.get(name).asText());
    }

    private static String text(JsonNode node, String name) {
        return node.get(name).asText();
    }

    private static String nullableText(JsonNode node, String name) {
        return node.get(name).isNull() ? null : node.get(name).asText();
    }

    private static int integer(JsonNode node, String name) {
        return node.get(name).asInt();
    }

    private static JsonNode projection(String id) {
        for (JsonNode value : FIXTURE.get("canonicalProjections")) {
            if (value.get("id").asText().equals(id)) {
                return value;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static JsonNode expected(String id) {
        return projection(id).get("expected");
    }

    private static JsonNode discriminator(String id) {
        for (JsonNode value : FIXTURE.get("discriminators")) {
            if (value.get("id").asText().equals(id)) {
                return value;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static void assertExpected(CanonicalHash actual, JsonNode expected) {
        String canonical = expected.get("canonicalJson").asText();
        int schemaProperty = canonical.indexOf("\"schema\":\"");
        if (schemaProperty >= 0) {
            int schemaStart = schemaProperty + 10;
            int schemaEnd = canonical.indexOf('"', schemaStart);
            assertEquals(canonical.substring(schemaStart, schemaEnd), actual.schema());
        }
        assertEquals(canonical,
            new String(actual.canonicalUtf8(), StandardCharsets.UTF_8));
        assertEquals(expected.get("canonicalUtf8Hex").asText(), HexFormat.of().formatHex(actual.canonicalUtf8()));
        assertEquals(expected.get("sha256").asText(), actual.sha256LowerHex());
    }

    private static JsonNode loadFixture() {
        try (InputStream input = RiskDecisionCanonicalizerAutoTest.class.getResourceAsStream(
            "/fixtures/risk-decision-v1-golden-vectors.json")) {
            if (input == null) {
                throw new IllegalStateException("缺少 Risk Decision 黄金向量");
            }
            return JSON.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Risk Decision 黄金向量", exception);
        }
    }
}
