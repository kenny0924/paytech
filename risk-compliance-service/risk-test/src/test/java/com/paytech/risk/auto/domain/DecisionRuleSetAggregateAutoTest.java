package com.paytech.risk.auto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionPointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.InputFieldProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.OperandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.PredicateProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetBindingProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.StringValue;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.DecisionPointView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.InputFieldView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.NodeView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.OperandView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.PointVersionView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleActionView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleSetVersionView;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate.RuleView;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rule Set 持久化闭包的纯领域不变量测试。 */
class DecisionRuleSetAggregateAutoTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void test_acceptsOneRootTreeAndCopiesEveryCollection() {
        List<OperandView> operands = new ArrayList<>(validOperands());
        NodeView root = predicate(501L, null, 1, operands);
        List<NodeView> nodes = new ArrayList<>(List.of(root));
        List<RuleView> rules = new ArrayList<>(List.of(rule(401L, "NET_AMOUNT_LIMIT", nodes)));
        List<InputFieldView> fields = new ArrayList<>(List.of(field()));
        List<PersistedActionVersion> actions = new ArrayList<>(List.of(actionVersion()));

        DecisionRuleSetAggregate aggregate = aggregate(fields, rules, actions);
        operands.clear();
        nodes.clear();
        rules.clear();
        fields.clear();
        actions.clear();

        assertEquals(1, aggregate.rules().size());
        assertEquals(1, aggregate.rules().getFirst().conditionNodes().size());
        assertEquals(2, aggregate.rules().getFirst().conditionNodes().getFirst().operands().size());
        assertEquals(1, aggregate.inputFields().size());
        assertEquals(1, aggregate.persistedActions().size());
        assertThrows(UnsupportedOperationException.class, () -> aggregate.rules().clear());
    }

    @Test
    void test_rejectsMissingDuplicateOrDisconnectedRoots() {
        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(rule(401L, "RULE_A", List.of(
                predicate(501L, null, 1, validOperands()),
                predicate(502L, null, 2, validOperands())))), List.of(actionVersion())));

        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(rule(401L, "RULE_A", List.of(
                predicate(501L, null, 1, validOperands()),
                predicate(502L, 999L, 1, validOperands())))), List.of(actionVersion())));

        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(rule(401L, "RULE_A", List.of(
                predicate(502L, 501L, 1, validOperands()),
                predicate(501L, null, 1, validOperands())))), List.of(actionVersion())));
    }

    @Test
    void test_rejectsCrossRuleNodeAndNonCanonicalOperandOrder() {
        RuleView first = rule(401L, "RULE_A", List.of(
            predicate(501L, null, 1, validOperands()),
            predicate(502L, 601L, 1, validOperands())));
        RuleView second = rule(402L, "RULE_B", List.of(predicate(601L, null, 1, validOperands())));
        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(first, second), List.of(actionVersion())));

        List<OperandView> reversed = List.of(validOperands().get(1), validOperands().get(0));
        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(rule(401L, "RULE_A",
                List.of(predicate(501L, null, 1, reversed)))), List.of(actionVersion())));
    }

    @Test
    void test_rejectsPredicateChildrenAndInvalidGroupArity() {
        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(rule(401L, "RULE_A", List.of(
                predicate(501L, null, 1, validOperands()),
                predicate(502L, 501L, 1, validOperands())))), List.of(actionVersion())));

        NodeView emptyAnd = new NodeView(501L, null, 1, NodeType.GROUP, GroupOperator.AND, null, List.of());
        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(rule(401L, "RULE_A", List.of(emptyAnd))),
                List.of(actionVersion())));
    }

    @Test
    void test_rejectsBrokenActionAndTopLevelCoordinates() {
        RuleView wrongSequence = new RuleView(401L, "RULE_A", "Rule A", 1, true, "RULE_A",
            List.of(predicate(501L, null, 1, validOperands())),
            new RuleActionView(701L, 240001L, 2, new ActionKey("ALLOW_CHECKOUT", 1)));
        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(wrongSequence), List.of(actionVersion())));

        RuleSetVersionView wrongPoint = new RuleSetVersionView(301L, 999L, "RULE_SET", "Rule Set", 1,
            RuleSetVersionStatus.DRAFT, HASH, 11L, "create", null, null, Instant.parse("2026-09-05T00:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> new DecisionRuleSetAggregate(
            wrongPoint, point(), pointVersion(), List.of(field()), List.of(rule(401L, "RULE_A",
            List.of(predicate(501L, null, 1, validOperands())))), ruleSetProjection(), pointProjection(),
            List.of(actionVersion())));
    }

    @Test
    void test_reasonCodeMayBeNullButNeverBlank() {
        RuleView nullableReason = new RuleView(401L, "RULE_A", "Rule A", 1, true, null,
            List.of(predicate(501L, null, 1, validOperands())),
            new RuleActionView(701L, 240001L, 1, new ActionKey("ALLOW_CHECKOUT", 1)));
        assertEquals(null, aggregate(List.of(field()), List.of(nullableReason), List.of(actionVersion()))
            .rules().getFirst().reasonCode());

        RuleView blankReason = new RuleView(401L, "RULE_A", "Rule A", 1, true, " ",
            List.of(predicate(501L, null, 1, validOperands())),
            new RuleActionView(701L, 240001L, 1, new ActionKey("ALLOW_CHECKOUT", 1)));
        assertThrows(IllegalArgumentException.class,
            () -> aggregate(List.of(field()), List.of(blankReason), List.of(actionVersion())));
    }

    private static DecisionRuleSetAggregate aggregate(
        List<InputFieldView> fields,
        List<RuleView> rules,
        List<PersistedActionVersion> actions
    ) {
        return new DecisionRuleSetAggregate(version(), point(), pointVersion(), fields, rules,
            ruleSetProjection(), pointProjection(), actions);
    }

    private static RuleSetVersionView version() {
        return new RuleSetVersionView(301L, 201L, "RULE_SET", "Rule Set", 1,
            RuleSetVersionStatus.DRAFT, HASH, 11L, "create", null, null,
            Instant.parse("2026-09-05T00:00:00Z"));
    }

    private static DecisionPointView point() {
        return new DecisionPointView(201L, "CHECKOUT_PREPARE", "Checkout Prepare",
            DecisionBusinessDomain.CHECKOUT, DecisionPointStatus.ACTIVE);
    }

    private static PointVersionView pointVersion() {
        return new PointVersionView(202L, 201L, 1, DecisionPointVersionStatus.DRAFT, HASH);
    }

    private static InputFieldView field() {
        return new InputFieldView(211L, "net_amount", DecisionValueType.NUMBER, true,
            DataClassification.INTERNAL, true, false);
    }

    private static RuleView rule(long id, String code, List<NodeView> nodes) {
        return new RuleView(id, code, "Rule " + code, 1, true, code, nodes,
            new RuleActionView(id + 300L, 240001L, 1, new ActionKey("ALLOW_CHECKOUT", 1)));
    }

    private static NodeView predicate(long id, Long parentId, int sequence, List<OperandView> operands) {
        return new NodeView(id, parentId, sequence, NodeType.PREDICATE, null, ComparisonOperator.GTE, operands);
    }

    private static List<OperandView> validOperands() {
        return List.of(
            new OperandView(601L, OperandRole.LEFT, 1, OperandSource.CONTEXT, "net_amount",
                DecisionValueType.NUMBER, null),
            new OperandView(602L, OperandRole.RIGHT, 1, OperandSource.LITERAL, null,
                DecisionValueType.NUMBER, new StringLiteral("100.00000000"))
        );
    }

    private static PersistedActionVersion actionVersion() {
        return new PersistedActionVersion(240001L, "ALLOW_CHECKOUT", 1, "DECISION_ALLOW_V1", "1.0.0",
            "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1",
            ActionCategory.DECISION, true, DecisionValue.ALLOW, MergePolicy.MOST_RESTRICTIVE, true,
            ConfigurationStatus.ACTIVE);
    }

    private static RuleSetVersionProjection ruleSetProjection() {
        PredicateProjection condition = new PredicateProjection(ComparisonOperator.GTE, List.of(
            new OperandProjection(OperandRole.LEFT, 1, OperandSource.CONTEXT, "net_amount",
                DecisionValueType.NUMBER, null),
            new OperandProjection(OperandRole.RIGHT, 1, OperandSource.LITERAL, null,
                DecisionValueType.NUMBER, new StringValue("100.00000000"))));
        ActionProjection action = new ActionProjection("ALLOW_CHECKOUT", 1, "DECISION_ALLOW_V1", "1.0.0",
            "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1",
            ActionCategory.DECISION, true, DecisionValue.ALLOW, MergePolicy.MOST_RESTRICTIVE, true);
        return new RuleSetVersionProjection("CHECKOUT_PREPARE", "RULE_SET", "Rule Set", 1,
            List.of(new RuleProjection("RULE_A", "Rule RULE_A", 1, true, "RULE_A", condition, action)));
    }

    private static DecisionPointVersionProjection pointProjection() {
        return new DecisionPointVersionProjection("CHECKOUT_PREPARE", DecisionBusinessDomain.CHECKOUT, 1,
            "ORDER_PREPARATION", 4, "RISK_DECISION_RESULT", DecisionValue.REVIEW,
            EvaluationStrategy.FIRST_TERMINAL, FailureStrategy.FAIL_CLOSED, 500,
            List.of(new InputFieldProjection("net_amount", DecisionValueType.NUMBER, true,
                DataClassification.INTERNAL, true, false)),
            List.of(new RuleSetBindingProjection(1, "RULE_SET", 1, HASH)), List.of(), List.of(), List.of());
    }
}
