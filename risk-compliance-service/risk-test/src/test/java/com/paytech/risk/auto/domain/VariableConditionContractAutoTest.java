package com.paytech.risk.auto.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 通过共用纯配置合同验证两种编辑器的类型矩阵，不模拟 Service 或数据库。 */
class VariableConditionContractAutoTest {
    private static final String POINT = "CHECKOUT_PREPARE";
    private static final Instant TIME = Instant.parse("2026-09-12T00:00:00Z");
    private static final Set<VariableUsage> CONDITIONS = Set.of(VariableUsage.RULE_CONDITION, VariableUsage.FLOW_CONDITION);

    @Test
    void arbitraryCodesWorkOnBothSidesAndUseAccurateCurrencyIdentity() {
        var context = context(VariableUsage.RULE_CONDITION);
        var amount = variable("ORDER_TOTAL_ALIAS", DecisionValueType.NUMBER, OperandRole.LEFT);
        var limit = variable("CUSTOM_LIMIT", DecisionValueType.NUMBER, OperandRole.RIGHT);
        assertEquals(List.of(amount, limit), check(ComparisonOperator.GT, context, amount, limit));
        var reverseAmount = variable("ORDER_TOTAL_ALIAS", DecisionValueType.NUMBER, OperandRole.RIGHT);
        assertEquals("12.00000000", ((StringLiteral) check(ComparisonOperator.LT, context,
            literal(OperandRole.LEFT, DecisionValueType.NUMBER, "12", NumericUnit.MONEY, null, "CURRENCY_ALIAS"), reverseAmount)
            .getFirst().literalValue()).value());
        assertEquals(2, check(ComparisonOperator.EQ, context,
            literal(OperandRole.LEFT, DecisionValueType.NUMBER, "1", NumericUnit.SCALAR, null, null),
            literal(OperandRole.RIGHT, DecisionValueType.NUMBER, "1.00000000", NumericUnit.SCALAR, null, null)).size());
        assertThrows(RiskVariableException.class, () -> check(ComparisonOperator.GT, context, amount,
            literal(OperandRole.RIGHT, DecisionValueType.NUMBER, "12", NumericUnit.MONEY, "USD", null)));
        assertThrows(RiskVariableException.class, () -> check(ComparisonOperator.GT, context, amount,
            literal(OperandRole.RIGHT, DecisionValueType.NUMBER, "12", NumericUnit.SCALAR, null, null)));
    }

    @Test
    void nodeDecisionKeepsMatchedAndCanBranchOnDenyWithoutExecuting() {
        var flow = context(VariableUsage.FLOW_CONDITION);
        assertEquals(2, check(ComparisonOperator.EQ, flow, variable("OUTPUT_DECISION", DecisionValueType.STRING, OperandRole.LEFT),
            literal(OperandRole.RIGHT, DecisionValueType.STRING, "DENY", null, null, null)).size());
        assertEquals(1, check(ComparisonOperator.IS_NULL, flow, variable("OUTPUT_DECISION", DecisionValueType.STRING, OperandRole.LEFT)).size());
        assertEquals(2, check(ComparisonOperator.EQ, flow, variable("OUTPUT_MATCHED", DecisionValueType.BOOLEAN, OperandRole.LEFT),
            literal(OperandRole.RIGHT, DecisionValueType.BOOLEAN, true, null, null, null)).size());
        assertThrows(RiskVariableException.class, () -> check(ComparisonOperator.EQ, flow,
            variable("OUTPUT_DECISION", DecisionValueType.STRING, OperandRole.LEFT), literal(OperandRole.RIGHT, DecisionValueType.STRING, "REJECT", null, null, null)));
        assertThrows(RiskVariableException.class, () -> check(ComparisonOperator.IS_NULL, context(VariableUsage.RULE_CONDITION),
            variable("OUTPUT_DECISION", DecisionValueType.STRING, OperandRole.LEFT)));
        var start = new VariableConditionValidator.Context(POINT, VariableUsage.FLOW_CONDITION, "START", flow.variables(), flow.collections());
        assertThrows(RiskVariableException.class, () -> check(ComparisonOperator.IS_NULL, start,
            variable("OUTPUT_MATCHED", DecisionValueType.BOOLEAN, OperandRole.LEFT)));
    }

    @Test
    void collectionDomainsAndLiteralFormatsFollowCurrentDefinitions() {
        var context = context(VariableUsage.RULE_CONDITION);
        assertEquals(2, check(ComparisonOperator.IN, context, variable("COUNTRY_ALIAS", DecisionValueType.STRING, OperandRole.LEFT),
            variable("COUNTRIES", DecisionValueType.STRING, OperandRole.RIGHT)).size());
        assertThrows(RiskVariableException.class, () -> check(ComparisonOperator.EQ, context,
            variable("COUNTRY_ALIAS", DecisionValueType.STRING, OperandRole.LEFT), literal(OperandRole.RIGHT, DecisionValueType.STRING, "usd", null, null, null)));
        assertThrows(RiskVariableException.class, () -> check(ComparisonOperator.IN, context,
            variable("COUNTRY_ALIAS", DecisionValueType.STRING, OperandRole.LEFT), variable("COUNTRY_ALIAS", DecisionValueType.STRING, OperandRole.RIGHT)));
        var normalized = check(ComparisonOperator.EQ, context, variable("LOCALE_ALIAS", DecisionValueType.STRING, OperandRole.LEFT),
            literal(OperandRole.RIGHT, DecisionValueType.STRING, "en-us", null, null, null));
        assertEquals(new StringLiteral("en-US"), normalized.getLast().literalValue());
        assertThrows(RiskVariableException.class, () -> check(ComparisonOperator.BETWEEN, context,
            variable("ORDER_TOTAL_ALIAS", DecisionValueType.NUMBER, OperandRole.LEFT),
            literal(OperandRole.RIGHT, DecisionValueType.NUMBER, "20", NumericUnit.MONEY, null, "CURRENCY"),
            new VariableOperand(OperandRole.RIGHT, 2, OperandSource.LITERAL, null, null, DecisionValueType.NUMBER,
                new StringLiteral("10"), NumericUnit.MONEY, null, "CURRENCY")));
    }

    @Test
    void currentValuesDoNotEnterVariableRuleHashAndOriginalLiteralRetainsItsIdentity() {
        var context = context(VariableUsage.RULE_CONDITION);
        var operands = check(ComparisonOperator.GT, context, variable("ORDER_TOTAL_ALIAS", DecisionValueType.NUMBER, OperandRole.LEFT),
            variable("CUSTOM_LIMIT", DecisionValueType.NUMBER, OperandRole.RIGHT));
        var changed = new HashMap<>(context.variables());
        Entry old = changed.get("CUSTOM_LIMIT");
        changed.put("CUSTOM_LIMIT", new Entry(old.definition(), "Renamed", null, "9000.00000000", old.status(), 2, 1, 1, TIME, TIME));
        var after = VariableConditionValidator.validate(ComparisonOperator.GT, operands,
            new VariableConditionValidator.Context(POINT, VariableUsage.RULE_CONDITION, null, changed, context.collections()));
        assertEquals(hash(operands), hash(after));
        var literal = List.of(operands.getFirst(), literal(OperandRole.RIGHT, DecisionValueType.NUMBER, "8000.00000000", NumericUnit.MONEY, null, "CURRENCY"));
        assertNotEquals(hash(operands), hash(literal));
        assertThrows(RiskVariableException.class, () -> VariableOperandJson.validateShape(
            literal(OperandRole.LEFT, DecisionValueType.STRING, "value", NumericUnit.MONEY, "USD", null)));
    }

    private static String hash(List<VariableOperand> operands) {
        var projection = operands.stream().map(o -> new OperandProjection(o.operandRole(), o.valueSequence(), o.sourceType(), null,
            o.valueType(), o.literalValue() instanceof StringLiteral s ? new StringValue(s.value()) : NullValue.INSTANCE,
            o.variableCode(), o.numericUnit(), o.currencyCode(), o.currencyVariableCode())).toList();
        var rule = new RuleProjection("RULE", "rule", 1, true, null, new PredicateProjection(ComparisonOperator.GT, projection), null, null, List.of());
        return new RiskDecisionCanonicalizerImpl().ruleSetVersion(new RuleSetVersionProjection(POINT, "RULES", "rules", 1, List.of(rule),
            VariableRuleConfiguration.CONTENT_FORMAT)).sha256LowerHex();
    }

    private static List<VariableOperand> check(ComparisonOperator op, VariableConditionValidator.Context context, VariableOperand... operands) {
        return VariableConditionValidator.validate(op, List.of(operands), context);
    }

    private static VariableOperand variable(String code, DecisionValueType type, OperandRole role) {
        return new VariableOperand(role, 1, OperandSource.VARIABLE, code, null, type, NullLiteral.INSTANCE, null, null, null);
    }

    private static VariableOperand literal(OperandRole role, DecisionValueType type, Object value, NumericUnit unit, String currency, String currencyVariable) {
        return new VariableOperand(role, 1, OperandSource.LITERAL, null, null, type, VariableConditionValidator.literal(value), unit, currency, currencyVariable);
    }

    private static VariableConditionValidator.Context context(VariableUsage usage) {
        var entries = List.of(
            entry("CURRENCY", VariableSource.INPUT, DecisionValueType.STRING, "currency", VariableFormat.UPPER_ALPHA_3, null, null, null, null),
            entry("CURRENCY_ALIAS", VariableSource.INPUT, DecisionValueType.STRING, "currency", VariableFormat.UPPER_ALPHA_3, null, null, null, null),
            entry("ORDER_TOTAL_ALIAS", VariableSource.INPUT, DecisionValueType.NUMBER, "net_amount", VariableFormat.NONE, NumericUnit.MONEY, "CURRENCY", null, null),
            entry("CUSTOM_LIMIT", VariableSource.CONSTANT, DecisionValueType.NUMBER, null, VariableFormat.NONE, NumericUnit.MONEY, "CURRENCY_ALIAS", null, "8000.00000000"),
            entry("COUNTRY_ALIAS", VariableSource.INPUT, DecisionValueType.STRING, "country_code", VariableFormat.UPPER_ALPHA_2, null, null, null, null),
            entry("LOCALE_ALIAS", VariableSource.INPUT, DecisionValueType.STRING, "locale", VariableFormat.BCP47, null, null, null, null),
            entry("COUNTRIES", VariableSource.COLLECTION, DecisionValueType.STRING, null, VariableFormat.UPPER_ALPHA_2, null, null, null, null),
            entry("OUTPUT_DECISION", VariableSource.NODE_OUTPUT, DecisionValueType.STRING, "decision", VariableFormat.TOKEN, null, null, "DECISIONS", null),
            entry("OUTPUT_MATCHED", VariableSource.NODE_OUTPUT, DecisionValueType.BOOLEAN, "matched", VariableFormat.NONE, null, null, null, null),
            entry("DECISIONS", VariableSource.COLLECTION, DecisionValueType.STRING, null, VariableFormat.TOKEN, null, null, null, null));
        Map<String, Entry> variables = new HashMap<>(); entries.forEach(e -> variables.put(e.definition().variableCode(), e));
        return new VariableConditionValidator.Context(POINT, usage, usage == VariableUsage.FLOW_CONDITION ? "RULE_SET" : null,
            variables, Map.of("COUNTRIES", List.of(new Member("IR", null, 1)), "DECISIONS",
                List.of(new Member("ALLOW", "PASS", 1), new Member("REVIEW", "REVIEW", 2), new Member("DENY", "REJECT", 3))));
    }

    private static Entry entry(String code, VariableSource source, DecisionValueType type, String field, VariableFormat format,
        NumericUnit unit, String currency, String options, Object value) {
        var definition = new Definition(code, source, type, POINT,
            source == VariableSource.INPUT ? "ORDER_PREPARATION" : source == VariableSource.NODE_OUTPUT ? "RULE_SET_NODE_RESULT" : null,
            source == VariableSource.INPUT ? Integer.valueOf(4) : source == VariableSource.NODE_OUTPUT ? Integer.valueOf(1) : null, field,
            source == VariableSource.NODE_OUTPUT ? Set.of(VariableUsage.FLOW_CONDITION) : CONDITIONS, source == VariableSource.NODE_OUTPUT,
            format, unit, null, currency, options);
        return new Entry(definition, code, null, value, VariableStatus.ACTIVE, 1, 1, 1, TIME, TIME);
    }
}
