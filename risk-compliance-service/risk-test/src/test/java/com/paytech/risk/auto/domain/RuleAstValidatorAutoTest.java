package com.paytech.risk.auto.domain;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import com.paytech.risk.decision.domain.rule.ChangeReasonPolicy;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NodeDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedGroup;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedPredicate;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NullLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.OperandDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.RuleDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.RuleSetDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidatorImpl;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleAstValidatorAutoTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final JsonNode FIXTURE = loadFixture();

    private final CheckoutPrepareAuthoringDescriptor descriptor = CheckoutPrepareAuthoringDescriptor.v1();
    private final DecisionActionRegistry registry = DecisionActionRegistry.checkoutPrepareV1();
    private final RuleAstValidator validator = new RuleAstValidatorImpl(descriptor, registry);

    @Test
    void test_configurationAllowsNoActionsWhileLegacyStillRequiresAnAction() {
        RuleSetDraft draft = validDraft();
        RuleDraft rule = draft.rules().getFirst();
        RuleDraft optional = new RuleDraft(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabled(),
            null, rule.conditionNodes(), null, null, List.of());
        var result = validator.validateAndNormalize(replaceRules(draft, List.of(optional)));
        assertEquals(null, result.rules().getFirst().action());
        assertEquals(List.of(), result.rules().getFirst().additionalActions());
        RuleDraft legacy = new RuleDraft(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabled(),
            null, rule.conditionNodes(), null);
        assertBusiness(replaceRules(draft, List.of(legacy)));
    }

    @Test
    void test_descriptorAndRegistryMatchFrozenCheckoutDirectory() {
        assertSame(descriptor, CheckoutPrepareAuthoringDescriptor.v1());
        assertEquals("CHECKOUT_PREPARE_AUTHORING_V1", descriptor.descriptorCode());
        assertEquals("CHECKOUT_PREPARE", descriptor.point().pointCode());
        assertEquals("Checkout Prepare", descriptor.point().pointName());
        assertEquals("CHECKOUT", descriptor.point().businessDomain());
        assertEquals("ORDER_PREPARATION", descriptor.point().inputContractCode());
        assertEquals(4, descriptor.point().inputContractVersion());
        assertEquals("RISK_DECISION_RESULT", descriptor.point().outputContractCode());
        assertEquals(500, descriptor.point().timeoutMillis());
        assertEquals(ConfigurationStatus.ACTIVE, descriptor.point().status());

        assertEquals(List.of(
            "country_code", "locale", "industry_code", "mcc_code", "currency",
            "merchandise_amount", "discount_amount", "net_amount", "entity_source_type"
        ), descriptor.fields().stream().map(CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor::fieldCode).toList());
        assertEquals(List.of(
            ComparisonOperator.EQ, ComparisonOperator.NE, ComparisonOperator.GT, ComparisonOperator.GTE,
            ComparisonOperator.LT, ComparisonOperator.LTE, ComparisonOperator.IN, ComparisonOperator.NOT_IN,
            ComparisonOperator.BETWEEN, ComparisonOperator.IS_NULL, ComparisonOperator.IS_NOT_NULL
        ), descriptor.operators());
        assertEquals(List.of("ALLOW_CHECKOUT", "REVIEW_CHECKOUT", "DENY_CHECKOUT"),
            descriptor.actions().stream().map(CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor::actionCode).toList());
        assertEquals(List.of(
            "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1",
            "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716",
            "8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7"
        ), descriptor.actions().stream()
            .map(CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor::implementationHash).toList());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.fields().clear());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.operators().clear());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.actions().clear());

        assertSame(registry, DecisionActionRegistry.checkoutPrepareV1());
        assertEquals(descriptor.actions(), registry.authoringActions());
        assertThrows(InvalidParameterException.class,
            () -> registry.require(new ActionKey("UNKNOWN_ACTION", 1)));
        assertThrows(InvalidParameterException.class,
            () -> registry.require(new ActionKey("ALLOW_CHECKOUT", 2)));

        List<PersistedActionVersion> exact = descriptor.actions().stream().map(action -> new PersistedActionVersion(
            240001L + descriptor.actions().indexOf(action), action.actionCode(), action.versionNo(), action.handlerCode(),
            action.implementationVersion(), action.implementationHash(), action.actionCategory(), action.terminalFlag(),
            action.decisionCode(), action.mergePolicy(), action.shadowSupportedFlag(), ConfigurationStatus.ACTIVE)).toList();
        registry.verifyPersistedCatalog(exact);
        ArrayList<PersistedActionVersion> drifted = new ArrayList<>(exact);
        PersistedActionVersion allow = drifted.getFirst();
        drifted.set(0, new PersistedActionVersion(allow.actionVersionId(), allow.actionCode(), allow.versionNo(),
            allow.handlerCode(), allow.implementationVersion(), "0".repeat(64), allow.actionCategory(),
            allow.terminalFlag(), allow.decisionCode(), allow.mergePolicy(), allow.shadowSupportedFlag(), allow.status()));
        assertThrows(IllegalStateException.class, () -> registry.verifyPersistedCatalog(drifted));
        ArrayList<PersistedActionVersion> semanticDrift = new ArrayList<>(exact);
        semanticDrift.set(0, new PersistedActionVersion(allow.actionVersionId(), allow.actionCode(), allow.versionNo(),
            allow.handlerCode(), allow.implementationVersion(), allow.implementationHash(), ActionCategory.RESTRICTION,
            false, DecisionValue.DENY, MergePolicy.LAST, false, allow.status()));
        assertThrows(IllegalStateException.class, () -> registry.verifyPersistedCatalog(semanticDrift));
        assertThrows(IllegalStateException.class, () -> registry.verifyPersistedCatalog(exact.subList(0, 2)));
        assertThrows(IllegalStateException.class, () -> registry.verifyPersistedCatalog(List.of(exact.getFirst(), exact.getFirst(), exact.get(2))));
    }

    @Test
    void test_validDraftNormalizesNfcNamesDecimalTimeDurationAndCollections() {
        RuleSetDraft draft = validDraft();
        RuleAstValidator.NormalizedRuleSet normalized = validator.validateAndNormalize(draft);

        assertEquals("Café Rules", normalized.ruleSetName());
        assertEquals("Initial Café policy", normalized.changeReason());
        assertEquals(List.of("COUNTRY_ALLOW", "AMOUNT_REVIEW"),
            normalized.rules().stream().map(RuleAstValidator.NormalizedRule::ruleCode).toList());
        assertTrue(normalized.rules().get(1).condition() instanceof NormalizedGroup);
        NormalizedGroup group = (NormalizedGroup) normalized.rules().get(1).condition();
        assertEquals(2, group.children().size());
        NormalizedPredicate amount = (NormalizedPredicate) group.children().get(1);
        assertEquals("1.00000000", ((StringLiteral) amount.operands().get(1).literalValue()).value());
        assertNotSame(draft.rules(), normalized.rules());
        assertThrows(UnsupportedOperationException.class, () -> normalized.rules().clear());
        assertThrows(UnsupportedOperationException.class, () -> group.children().clear());
        assertThrows(UnsupportedOperationException.class, () -> amount.operands().clear());

        assertEquals("0.00000000", validator.normalizeDecimal("0"));
        assertEquals("1.00000000", validator.normalizeDecimal("1"));
        assertEquals("1.00000000", validator.normalizeDecimal("1.0"));
        assertEquals("2026-09-05T00:00:00.123456Z",
            validator.normalizeUtcMicrosecond("2026-09-05T00:00:00.123456Z"));
        assertEquals("0", validator.normalizeDurationMillis("0"));
        assertEquals("1000", validator.normalizeDurationMillis("1000"));
        assertEquals("-1.00000000", validator.normalizeDecimal("-1"));
        discriminator("decimal-normalization").get("accepted").forEach(value ->
            assertEquals(value.get("output").asText(), validator.normalizeDecimal(value.get("input").asText())));
        JsonNode time = discriminator("utc-duration");
        assertEquals(time.get("accepted").get("utc").asText(),
            validator.normalizeUtcMicrosecond(time.get("accepted").get("utc").asText()));
        time.get("acceptedUtc").forEach(value ->
            assertEquals(value.asText(), validator.normalizeUtcMicrosecond(value.asText())));
        time.get("accepted").get("durations").forEach(value ->
            assertEquals(value.asText(), validator.normalizeDurationMillis(value.asText())));
        RuleAstValidator.NormalizedRuleSet locale = validator.validateAndNormalize(replaceRule(draft,
            singlePredicateRule("LOCALE", 10, "locale", "en-us")));
        NormalizedPredicate localePredicate = (NormalizedPredicate) locale.rules().getFirst().condition();
        assertEquals("en-US", ((StringLiteral) localePredicate.operands().get(1).literalValue()).value());
    }

    @Test
    void test_invalidScalarAndDirectoryValuesFailWithoutEchoingLiteral() {
        for (String decimal : List.of("-0", "-0.0", "-0.00000000", "01", "+1", "1e0", "1.000000001",
            "10000000000000000.00000000", "-10000000000000000.00000000")) {
            InvalidParameterException error = assertThrows(InvalidParameterException.class,
                () -> validator.normalizeDecimal(decimal));
            assertFalse(error.getMessage().contains(decimal));
        }
        discriminator("utc-duration").get("rejectedUtc").forEach(value ->
            assertSafeFailure(() -> validator.normalizeUtcMicrosecond(value.asText()), value.asText()));
        for (String duration : List.of("00", "01", "-1")) {
            assertSafeFailure(() -> validator.normalizeDurationMillis(duration), duration);
        }
        assertSafeFailure(() -> validator.normalizeDurationMillis("9223372036854775808"),
            "9223372036854775808");
        assertBusiness(replaceRule(validDraft(), singlePredicateRule("BAD_FIELD", 10, "unknown_field", "x")));
        assertBusiness(replaceRule(validDraft(), singlePredicateRule("COUNTRY", 10, "country_code", "ae")));
        assertBusiness(replaceRule(validDraft(), singlePredicateRule("CURRENCY", 10, "currency", "usd")));
        assertBusiness(replaceRule(validDraft(), singlePredicateRule("MCC", 10, "mcc_code", "12A4")));
        assertBusiness(replaceRule(validDraft(), singlePredicateRule("INDUSTRY", 10, "industry_code", "games")));
        assertBusiness(replaceRule(validDraft(), singlePredicateRule("SOURCE", 10, "entity_source_type", "MERCHANT")));
        assertBusiness(replaceRule(validDraft(), singlePredicateRule("LOCALE", 10, "locale", "not_a_locale")));
    }

    @Test
    void test_changeReasonPolicyRejectsOnlyNarrowUnsafeShapesWithoutEchoingRawValue() {
        List<String> rejected = List.of(
            "release\u0000reason",
            "release\u200Breason",
            "release\uE000reason",
            "release\uD800reason",
            "{}",
            "[]",
            "{\"ticket\":\"OPS-123\"}",
            "{\"nested\":[true,null,-1.2e+3,\"x\\u0041\"]}",
            "[\"rotate\",\"key\"]",
            "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature",
            "Authorization: Bearer x",
            "Bearer opaque123",
            "Bearer abcdefghijklmnop",
            "api_key=sk_test_1234567890",
            "client-secret: value123",
            "password = hunter2",
            "access_token='opaque-value'",
            "card candidate 4222222222222",
            "card candidate 4111-1111-1111-1111",
            "card candidate 4111\u00A01111\u00A01111\u00A01111",
            "card candidate 4111\u20111111\u20111111\u20111111",
            "card candidate 4111\u20131111\u20131111\u20131111",
            "card candidate 4000000000000000006",
            "contact alice@example.com",
            "notify user+tag@example.co.uk",
            "phone=0501234567",
            "Mobile: +971 50 123 4567",
            "电话: 0501234567",
            "手机=13800138000",
            "手机号: +86 138 0013 8000",
            "call +14155552671",
            "call 050-123-4567",
            "call (415) 555-2671"
        );
        for (String raw : rejected) {
            InvalidParameterException error = assertThrows(InvalidParameterException.class,
                () -> ChangeReasonPolicy.normalize(raw));
            assertFalse(error.getMessage().contains(raw));
            InvalidParameterException domainError = assertThrows(InvalidParameterException.class,
                () -> validator.validateAndNormalize(withChangeReason(validDraft(), raw)));
            assertFalse(domainError.getMessage().contains(raw));
        }

        assertEquals("Rotate token validation rules after provider change",
            ChangeReasonPolicy.normalize(" Rotate token validation rules after provider change "));
        assertEquals("JSON object parsing tightened", ChangeReasonPolicy.normalize("JSON object parsing tightened"));
        assertEquals("[Maintenance] rollout approved", ChangeReasonPolicy.normalize("[Maintenance] rollout approved"));
        assertEquals("{maintenance}", ChangeReasonPolicy.normalize("{maintenance}"));
        assertEquals("{\"n\":1e+-2}", ChangeReasonPolicy.normalize("{\"n\":1e+-2}"));
        assertEquals("[1,]", ChangeReasonPolicy.normalize("[1,]"));
        assertEquals("{\"unterminated\":true", ChangeReasonPolicy.normalize("{\"unterminated\":true"));
        assertEquals("\"maintenance\"", ChangeReasonPolicy.normalize("\"maintenance\""));
        assertEquals("Bearer-token authentication was enabled",
            ChangeReasonPolicy.normalize("Bearer-token authentication was enabled"));
        assertEquals("Ticket 1234567890123 was reviewed", ChangeReasonPolicy.normalize(
            "Ticket 1234567890123 was reviewed"));
        assertEquals("Ticket OPS-123456789012 remains open",
            ChangeReasonPolicy.normalize("Ticket OPS-123456789012 remains open"));
        assertEquals("Release 2026-09-06 version 1.2.3",
            ChangeReasonPolicy.normalize("Release 2026-09-06 version 1.2.3"));
        assertEquals("Rule owner alias user@v1 updated",
            ChangeReasonPolicy.normalize("Rule owner alias user@v1 updated"));
        assertEquals("Phone validation rule changed", ChangeReasonPolicy.normalize("Phone validation rule changed"));
        assertEquals("Limit +100 basis points", ChangeReasonPolicy.normalize("Limit +100 basis points"));
        assertEquals("Café policy ✅", ChangeReasonPolicy.normalize(" Cafe\u0301 policy ✅ "));
    }

    @Test
    void test_invalidBaseGraphNodeAndOperandMatricesFailClosed() {
        RuleSetDraft valid = validDraft();
        assertInvalid(new RuleSetDraft(valid.pointCode(), valid.ruleSetCode(), valid.ruleSetName(), 1L, null,
            valid.changeReason(), valid.rules()));
        assertInvalid(new RuleSetDraft(valid.pointCode(), valid.ruleSetCode(), valid.ruleSetName(), null, "a".repeat(64),
            valid.changeReason(), valid.rules()));
        assertInvalid(new RuleSetDraft("OTHER_POINT", valid.ruleSetCode(), valid.ruleSetName(), null, null,
            valid.changeReason(), valid.rules()));

        RuleDraft base = valid.rules().getFirst();
        assertBusiness(replaceRules(valid, List.of(base, new RuleDraft(base.ruleCode(), "duplicate", 20, true, null,
            base.conditionNodes(), base.action()))));
        assertBusiness(replaceRules(valid, List.of(base, new RuleDraft("OTHER", "duplicate priority", base.priorityNo(), true,
            null, base.conditionNodes(), base.action()))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(
            predicate("a", null, 1, ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE"),
            predicate("b", null, 2, ComparisonOperator.EQ, "currency", DecisionValueType.STRING, "USD")))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(
            group("root", null, 1, GroupOperator.AND),
            predicate("orphan", "missing", 1, ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE"),
            predicate("child", "root", 2, ComparisonOperator.EQ, "currency", DecisionValueType.STRING, "USD")))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(
            group("root", "loop", 1, GroupOperator.NOT),
            group("loop", "root", 1, GroupOperator.NOT)))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(
            group("root", null, 1, GroupOperator.AND),
            predicate("one", "root", 1, ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE"),
            predicate("three", "root", 3, ComparisonOperator.EQ, "currency", DecisionValueType.STRING, "USD")))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(
            new NodeDraft("root", null, 1, NodeType.GROUP, GroupOperator.AND, ComparisonOperator.EQ, List.of()),
            predicate("one", "root", 1, ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE"),
            predicate("two", "root", 2, ComparisonOperator.EQ, "currency", DecisionValueType.STRING, "USD")))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(
            group("root", null, 1, GroupOperator.NOT),
            predicate("one", "root", 1, ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE"),
            predicate("two", "root", 2, ComparisonOperator.EQ, "currency", DecisionValueType.STRING, "USD")))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(
            group("root", null, 1, GroupOperator.AND),
            predicate("only", "root", 1, ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE")))));
        assertInvalid(replaceRule(valid, withNodes(base, List.of(
            predicate("bad key", null, 1, ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE")))));
        assertInvalid(replaceRule(valid, withNodes(base, List.of(
            predicate("x".repeat(65), null, 1, ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE")))));

        NodeDraft wrongPredicate = new NodeDraft("root", null, 1, NodeType.PREDICATE, GroupOperator.AND,
            ComparisonOperator.EQ, scalarOperands("country_code", DecisionValueType.STRING, "AE"));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(wrongPredicate))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(new NodeDraft("root", null, 1, NodeType.PREDICATE,
            null, ComparisonOperator.IS_NULL, scalarOperands("country_code", DecisionValueType.STRING, "AE"))))));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(new NodeDraft("root", null, 1, NodeType.PREDICATE,
            null, ComparisonOperator.GT, scalarOperands("country_code", DecisionValueType.STRING, "AE"))))));
        List<OperandDraft> wrongSequence = List.of(
            new OperandDraft(OperandRole.LEFT, 1, OperandSource.CONTEXT, "country_code",
                DecisionValueType.STRING, NullLiteral.INSTANCE),
            new OperandDraft(OperandRole.RIGHT, 2, OperandSource.LITERAL, null,
                DecisionValueType.STRING, new StringLiteral("AE")));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(new NodeDraft("root", null, 1,
            NodeType.PREDICATE, null, ComparisonOperator.EQ, wrongSequence)))));

        List<OperandDraft> feature = List.of(new OperandDraft(OperandRole.LEFT, 1, OperandSource.FEATURE,
            null, DecisionValueType.STRING, NullLiteral.INSTANCE));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(new NodeDraft("root", null, 1, NodeType.PREDICATE,
            null, ComparisonOperator.IS_NULL, feature)))));
        assertBusiness(replaceRule(valid, new RuleDraft(base.ruleCode(), base.ruleName(), base.priorityNo(), true, null,
            base.conditionNodes(), new ActionKey("UNKNOWN_ACTION", 1))));
        assertInvalid(new RuleSetDraft(valid.pointCode(), valid.ruleSetCode(), "x".repeat(256), null, null,
            valid.changeReason(), valid.rules()));
        assertInvalid(new RuleSetDraft(valid.pointCode(), valid.ruleSetCode(), valid.ruleSetName(), null, null,
            "x".repeat(501), valid.rules()));
        assertInvalid(new RuleSetDraft(valid.pointCode(), valid.ruleSetCode(), valid.ruleSetName(), null, null,
            valid.changeReason(), null));
        assertInvalid(replaceRules(valid, java.util.Arrays.asList(base, null)));
    }

    @Test
    void test_semanticAstAndDirectoryRejectionsUseBusinessCheckWhileRequestShapeUsesInvalidParameter() {
        RuleSetDraft valid = validDraft();
        RuleDraft base = valid.rules().getFirst();

        assertBusiness(replaceRule(valid, singlePredicateRule("BAD_FIELD", 10, "unknown_field", "x")));
        assertBusiness(replaceRule(valid, withNodes(base, List.of(
            group("root", null, 1, GroupOperator.AND),
            predicate("only", "root", 1, ComparisonOperator.EQ,
                "country_code", DecisionValueType.STRING, "AE")))));
        assertBusiness(replaceRule(valid, new RuleDraft(base.ruleCode(), base.ruleName(), base.priorityNo(),
            true, null, base.conditionNodes(), new ActionKey("UNKNOWN_ACTION", 1))));

        assertInvalid(new RuleSetDraft(valid.pointCode(), valid.ruleSetCode(), "x".repeat(256), null, null,
            valid.changeReason(), valid.rules()));
    }

    @Test
    void test_contextAndLiteralMayUseEitherOppositeSideWithPerRoleSequences() {
        RuleSetDraft valid = validDraft();
        RuleDraft base = valid.rules().getFirst();
        List<NodeDraft> accepted = List.of(
            new NodeDraft("root", null, 1, NodeType.PREDICATE, null, ComparisonOperator.EQ, List.of(
                context(OperandRole.RIGHT, "country_code", DecisionValueType.STRING),
                literal(OperandRole.LEFT, 1, DecisionValueType.STRING, "AE"))),
            new NodeDraft("root", null, 1, NodeType.PREDICATE, null, ComparisonOperator.IN, List.of(
                context(OperandRole.RIGHT, "country_code", DecisionValueType.STRING),
                literal(OperandRole.LEFT, 1, DecisionValueType.STRING, "AE"),
                literal(OperandRole.LEFT, 2, DecisionValueType.STRING, "US"))),
            new NodeDraft("root", null, 1, NodeType.PREDICATE, null, ComparisonOperator.BETWEEN, List.of(
                context(OperandRole.RIGHT, "net_amount", DecisionValueType.NUMBER),
                literal(OperandRole.LEFT, 1, DecisionValueType.NUMBER, "1"),
                literal(OperandRole.LEFT, 2, DecisionValueType.NUMBER, "10"))),
            new NodeDraft("root", null, 1, NodeType.PREDICATE, null, ComparisonOperator.IS_NULL, List.of(
                context(OperandRole.RIGHT, "country_code", DecisionValueType.STRING)))
        );

        for (NodeDraft node : accepted) {
            NormalizedPredicate predicate = (NormalizedPredicate) validator.validateAndNormalize(
                replaceRule(valid, withNodes(base, List.of(node)))).rules().getFirst().condition();
            assertEquals(OperandRole.RIGHT, predicate.operands().getLast().operandRole());
            assertEquals(OperandSource.CONTEXT, predicate.operands().getLast().sourceType());
            assertEquals(1, predicate.operands().getLast().valueSequence());
            assertTrue(predicate.operands().stream().filter(value -> value.sourceType() == OperandSource.LITERAL)
                .allMatch(value -> value.operandRole() == OperandRole.LEFT));
        }
    }

    @Test
    void test_ruleNodeOperandDepthAndInLimitsAreEnforcedAtBoundaries() {
        RuleSetDraft valid = validDraft();
        RuleDraft sample = valid.rules().getFirst();

        ArrayList<RuleDraft> twoHundred = new ArrayList<>();
        for (int index = 1; index <= 200; index++) {
            twoHundred.add(singlePredicateRule("RULE_" + index, index, "country_code", "AE"));
        }
        validator.validateAndNormalize(replaceRules(valid, twoHundred));
        twoHundred.add(singlePredicateRule("RULE_201", 201, "country_code", "AE"));
        assertInvalid(replaceRules(valid, twoHundred));

        ArrayList<NodeDraft> twoThousand = new ArrayList<>();
        twoThousand.add(group("root", null, 1, GroupOperator.AND));
        for (int index = 1; index < 2000; index++) {
            twoThousand.add(predicate("node-" + index, "root", index, ComparisonOperator.EQ,
                "country_code", DecisionValueType.STRING, "AE"));
        }
        validator.validateAndNormalize(replaceRule(valid, withNodes(sample, twoThousand)));
        twoThousand.add(predicate("node-2000", "root", 2000, ComparisonOperator.EQ,
            "country_code", DecisionValueType.STRING, "AE"));
        assertInvalid(replaceRule(valid, withNodes(sample, twoThousand)));

        ArrayList<NodeDraft> depthTwelve = nestedNotTree(12);
        validator.validateAndNormalize(replaceRule(valid, withNodes(sample, depthTwelve)));
        assertInvalid(replaceRule(valid, withNodes(sample, nestedNotTree(13))));

        validator.validateAndNormalize(replaceRule(valid, withNodes(sample,
            List.of(inNode("root", 100)))));
        assertInvalid(replaceRule(valid, withNodes(sample, List.of(inNode("root", 101)))));

        ArrayList<NodeDraft> tenThousandPlus = new ArrayList<>();
        tenThousandPlus.add(group("root", null, 1, GroupOperator.AND));
        for (int index = 1; index <= 100; index++) {
            tenThousandPlus.add(inNode("in-" + index, "root", index, 100));
        }
        assertInvalid(replaceRule(valid, withNodes(sample, tenThousandPlus)));
    }

    private static JsonNode discriminator(String id) {
        for (JsonNode value : FIXTURE.get("discriminators")) {
            if (value.get("id").asText().equals(id)) return value;
        }
        throw new IllegalArgumentException(id);
    }

    private static JsonNode loadFixture() {
        try (InputStream input = RuleAstValidatorAutoTest.class.getResourceAsStream(
            "/fixtures/risk-decision-v1-golden-vectors.json")) {
            if (input == null) throw new IllegalStateException("缺少 Risk Decision 黄金向量");
            return JSON.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Risk Decision 黄金向量", exception);
        }
    }

    private static RuleSetDraft validDraft() {
        RuleDraft amount = new RuleDraft("AMOUNT_REVIEW", "Cafe\u0301 amount", 20, true, null, List.of(
            predicate("node-10", "root", 2, ComparisonOperator.GTE, "net_amount", DecisionValueType.NUMBER, "1.0"),
            group("root", null, 1, GroupOperator.AND),
            predicate("node-2", "root", 1, ComparisonOperator.EQ, "currency", DecisionValueType.STRING, "USD")
        ), new ActionKey("ALLOW_CHECKOUT", 1));
        RuleDraft country = singlePredicateRule("COUNTRY_ALLOW", 10, "country_code", "AE");
        country = new RuleDraft(country.ruleCode(), country.ruleName(), country.priorityNo(), true, "ALLOW_REASON",
            country.conditionNodes(), country.action());
        return new RuleSetDraft("CHECKOUT_PREPARE", "CHECKOUT_DEFAULT", "Cafe\u0301 Rules", null, null,
            " Initial Cafe\u0301 policy ", List.of(amount, country));
    }

    private static RuleDraft singlePredicateRule(String code, int priority, String fieldCode, String literal) {
        DecisionValueType type = Set.of("merchandise_amount", "discount_amount", "net_amount").contains(fieldCode)
            ? DecisionValueType.NUMBER : DecisionValueType.STRING;
        return new RuleDraft(code, code.replace('_', ' '), priority, true, null,
            List.of(predicate("root", null, 1, ComparisonOperator.EQ, fieldCode, type, literal)),
            new ActionKey("ALLOW_CHECKOUT", 1));
    }

    private static NodeDraft group(String key, String parent, int sequence, GroupOperator operator) {
        return new NodeDraft(key, parent, sequence, NodeType.GROUP, operator, null, List.of());
    }

    private static NodeDraft predicate(
        String key,
        String parent,
        int sequence,
        ComparisonOperator operator,
        String fieldCode,
        DecisionValueType type,
        String literal
    ) {
        return new NodeDraft(key, parent, sequence, NodeType.PREDICATE, null, operator,
            scalarOperands(fieldCode, type, literal));
    }

    private static List<OperandDraft> scalarOperands(String fieldCode, DecisionValueType type, String literal) {
        return List.of(
            new OperandDraft(OperandRole.LEFT, 1, OperandSource.CONTEXT, fieldCode, type, NullLiteral.INSTANCE),
            new OperandDraft(OperandRole.RIGHT, 1, OperandSource.LITERAL, null, type,
                type == DecisionValueType.BOOLEAN ? new BooleanLiteral(Boolean.parseBoolean(literal)) : new StringLiteral(literal))
        );
    }

    private static OperandDraft context(OperandRole role, String fieldCode, DecisionValueType type) {
        return new OperandDraft(role, 1, OperandSource.CONTEXT, fieldCode, type, NullLiteral.INSTANCE);
    }

    private static OperandDraft literal(OperandRole role, int sequence, DecisionValueType type, String value) {
        return new OperandDraft(role, sequence, OperandSource.LITERAL, null, type,
            type == DecisionValueType.BOOLEAN ? new BooleanLiteral(Boolean.parseBoolean(value))
                : new StringLiteral(value));
    }

    private static NodeDraft inNode(String key, int literals) {
        return inNode(key, null, 1, literals);
    }

    private static NodeDraft inNode(String key, String parent, int sequence, int literals) {
        ArrayList<OperandDraft> operands = new ArrayList<>();
        operands.add(new OperandDraft(OperandRole.LEFT, 1, OperandSource.CONTEXT, "country_code",
            DecisionValueType.STRING, NullLiteral.INSTANCE));
        for (int index = 1; index <= literals; index++) {
            operands.add(new OperandDraft(OperandRole.RIGHT, index, OperandSource.LITERAL, null,
                DecisionValueType.STRING, new StringLiteral("AE")));
        }
        return new NodeDraft(key, parent, sequence, NodeType.PREDICATE, null, ComparisonOperator.IN, operands);
    }

    private static ArrayList<NodeDraft> nestedNotTree(int depth) {
        ArrayList<NodeDraft> nodes = new ArrayList<>();
        for (int level = 1; level < depth; level++) {
            nodes.add(group("not-" + level, level == 1 ? null : "not-" + (level - 1), 1, GroupOperator.NOT));
        }
        nodes.add(predicate("leaf", depth == 1 ? null : "not-" + (depth - 1), 1,
            ComparisonOperator.EQ, "country_code", DecisionValueType.STRING, "AE"));
        return nodes;
    }

    private static RuleDraft withNodes(RuleDraft rule, List<NodeDraft> nodes) {
        return new RuleDraft(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabled(), rule.reasonCode(),
            nodes, rule.action());
    }

    private static RuleSetDraft replaceRule(RuleSetDraft draft, RuleDraft rule) {
        return replaceRules(draft, List.of(rule));
    }

    private static RuleSetDraft replaceRules(RuleSetDraft draft, List<RuleDraft> rules) {
        return new RuleSetDraft(draft.pointCode(), draft.ruleSetCode(), draft.ruleSetName(), draft.baseRuleSetVersionId(),
            draft.baseContentHash(), draft.changeReason(), rules);
    }

    private static RuleSetDraft withChangeReason(RuleSetDraft draft, String changeReason) {
        return new RuleSetDraft(draft.pointCode(), draft.ruleSetCode(), draft.ruleSetName(),
            draft.baseRuleSetVersionId(), draft.baseContentHash(), changeReason, draft.rules());
    }

    private void assertInvalid(RuleSetDraft draft) {
        InvalidParameterException error = assertThrows(InvalidParameterException.class,
            () -> validator.validateAndNormalize(draft));
        assertFalse(error.getMessage().contains("1.00000000"));
        assertFalse(error.getMessage().contains("SECRET"));
    }

    private void assertBusiness(RuleSetDraft draft) {
        BizCheckFailException error = assertThrows(BizCheckFailException.class,
            () -> validator.validateAndNormalize(draft));
        assertEquals("BUSINESS_CHECK_FAILED", error.getCode());
        assertFalse(error.getMessage().contains("1.00000000"));
        assertFalse(error.getMessage().contains("SECRET"));
    }

    private static void assertSafeFailure(Runnable operation, String rawValue) {
        InvalidParameterException error = assertThrows(InvalidParameterException.class, operation::run);
        assertFalse(error.getMessage().contains(rawValue));
    }
}
