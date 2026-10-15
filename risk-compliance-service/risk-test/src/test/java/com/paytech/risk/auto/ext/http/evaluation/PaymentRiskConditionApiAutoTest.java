package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.group;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.literal;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.predicate;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.rule;
import static com.paytech.risk.test.builder.decision.VariableRequestBuilder.member;
import static com.paytech.risk.test.builder.decision.VariableRequestBuilder.members;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ConditionNodeRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.evaluation.RiskDecisionApi;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.PaymentRiskResponseChecker.Assessed;

/**
 * 运行期条件求值：经真实配置入口保存规则与流程，经支付评估入口执行，按规则轨迹逐条核对命中
 * <li>规则集只含无终态动作的规则，每条覆盖一种“运算符 × 值类型 × 命中与否”，一次评估核对全部结果，同时证明配置保存格式与运行期解析一致。</li>
 * <li>运算符：EQ、NE、GT、GTE、LT、LTE、BETWEEN（闭区间）、IN、NOT_IN（字面量与集合变量）、IS_NULL、IS_NOT_NULL。</li>
 * <li>值类型：字符串、金额、普通数值、时间、时长；来源：输入、常量、集合；可选输入为空时比较为假。</li>
 * <li>条件组合：AND、OR、NOT 及嵌套。</li>
 * <li>决策流连线：节点是否命中与金额的 AND 组合条件决定走审核还是放行结束。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class PaymentRiskConditionApiAutoTest extends AbstractPaymentRiskApiTest {
    /** 评估请求：金额 100 USD、MCC 5816、消费者国家 AE、无卡 BIN。 */
    private static final String AMOUNT = "100";

    private final Map<String, Boolean> expected = new TreeMap<>();

    private final List<RuleRequestBody> rules = new ArrayList<>();

    private String timeConstant;

    private String durationConstant;

    private String numberConstant;

    private String countries;

    /** 每例创建时间、时长、数值常量与国家集合变量。 */
    @BeforeEach
    void setUpClassMocks() {
        expected.clear();
        rules.clear();
        timeConstant = constant("T_CUTOFF_TIME", "DATETIME", null, "2026-03-04T05:06:07.000001Z");
        durationConstant = constant("T_COOLING", "DURATION", null, "600000");
        numberConstant = constant("T_LIMIT", "NUMBER", "SCALAR", "100");
        countries = newCode("T_COUNTRIES");
        CreateVariableRequestBody collection = featureVariable(countries, "COLLECTION", "STRING");
        collection.setFormatCode("UPPER_ALPHA_2");
        postOk("/decision-variables/v1/variables", collection);
        putOk("/decision-variables/v1/variables/" + countries + "/members",
            members("1", member("AE", "UAE"), member("SA", "Saudi")));
    }

    /** 全部运算符、值类型、变量来源与条件组合按规则逐条核对命中；流程走组合条件命中的审核结束。 */
    @Test
    void test_normalFlow_conditionMatrix() {
        stringCases();
        moneyCases();
        membershipCases();
        nullCases();
        constantCases();
        groupCases();

        Assessed assessed = assess(request()).assertResult(DecisionValue.REVIEW.getCode(), null);

        assertEquals(expected, store.ruleHits(assessed.evaluationId()));
    }

    /** 同一流程金额 10：组合条件中金额不成立，走放行结束。 */
    @Test
    void test_edgeCase_flowGroupConditionFalse() {
        stringCases();
        activate();

        assess(PaymentRiskRequestBuilder.template().amount("10").cardBin(null).build())
            .assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());
    }

    /** 字符串 EQ/NE。 */
    private void stringCases() {
        expect("STR_EQ_HIT", true, cmp("EQ", input("PAY_INPUT_MCC_CODE", "STRING"), text("5816")));
        expect("STR_EQ_MISS", false, cmp("EQ", input("PAY_INPUT_MCC_CODE", "STRING"), text("0000")));
        expect("STR_NE_HIT", true, cmp("NE", input("PAY_INPUT_MCC_CODE", "STRING"), text("0000")));
        expect("STR_NE_MISS", false, cmp("NE", input("PAY_INPUT_MCC_CODE", "STRING"), text("5816")));
    }

    /** 金额六种比较与 BETWEEN 闭区间；跨币种比较在配置保存时即被拒绝，运行期不会出现。 */
    private void moneyCases() {
        expect("AMT_EQ", true, cmp("EQ", amount(), money(AMOUNT)));
        expect("AMT_NE", false, cmp("NE", amount(), money(AMOUNT)));
        expect("AMT_GT", false, cmp("GT", amount(), money(AMOUNT)));
        expect("AMT_GTE", true, cmp("GTE", amount(), money(AMOUNT)));
        expect("AMT_LT", false, cmp("LT", amount(), money(AMOUNT)));
        expect("AMT_LTE", true, cmp("LTE", amount(), money(AMOUNT)));
        expect("AMT_BETWEEN_EDGE", true, cmp("BETWEEN", amount(), money("50", 1), money(AMOUNT, 2)));
        expect("AMT_BETWEEN_OUT", false, cmp("BETWEEN", amount(), money("100.01", 1), money("200", 2)));
    }

    /** IN/NOT_IN：多个字面量与集合变量。 */
    private void membershipCases() {
        expect("IN_LITERAL_HIT", true, cmp("IN", input("PAY_INPUT_MCC_CODE", "STRING"), text("5815", 1),
            text("5816", 2)));
        expect("NOT_IN_LITERAL_MISS", false, cmp("NOT_IN", input("PAY_INPUT_MCC_CODE", "STRING"), text("5815", 1),
            text("5816", 2)));
        expect("IN_COLLECTION_HIT", true, cmp("IN", input("PAY_INPUT_CONSUMER_COUNTRY", "STRING"),
            ref(countries, "STRING", "RIGHT")));
        expect("NOT_IN_COLLECTION_MISS", false, cmp("NOT_IN", input("PAY_INPUT_CONSUMER_COUNTRY", "STRING"),
            ref(countries, "STRING", "RIGHT")));
    }

    /** 可选输入为空：IS_NULL/IS_NOT_NULL 判断，其余比较为假。 */
    private void nullCases() {
        expect("BIN_IS_NULL", true, cmp("IS_NULL", input("PAY_INPUT_CARD_BIN", "STRING")));
        expect("BIN_IS_NOT_NULL", false, cmp("IS_NOT_NULL", input("PAY_INPUT_CARD_BIN", "STRING")));
        expect("COUNTRY_IS_NOT_NULL", true, cmp("IS_NOT_NULL", input("PAY_INPUT_CONSUMER_COUNTRY", "STRING")));
        expect("BIN_EQ_WHEN_NULL", false, cmp("EQ", input("PAY_INPUT_CARD_BIN", "STRING"), text("411111")));
        expect("BIN_NE_WHEN_NULL", false, cmp("NE", input("PAY_INPUT_CARD_BIN", "STRING"), text("411111")));
    }

    /** 常量：时间、时长、普通数值。 */
    private void constantCases() {
        expect("TIME_GT", true, cmp("GT", ref(timeConstant, "DATETIME", "LEFT"),
            literal("RIGHT", 1, "DATETIME", "2026-01-01T00:00:00.000000Z")));
        expect("TIME_LT", false, cmp("LT", ref(timeConstant, "DATETIME", "LEFT"),
            literal("RIGHT", 1, "DATETIME", "2026-01-01T00:00:00.000000Z")));
        expect("DURATION_LTE", true, cmp("LTE", ref(durationConstant, "DURATION", "LEFT"),
            literal("RIGHT", 1, "DURATION", "600000")));
        expect("DURATION_GT", false, cmp("GT", ref(durationConstant, "DURATION", "LEFT"),
            literal("RIGHT", 1, "DURATION", "600000")));
        expect("NUMBER_EQ", true, cmp("EQ", ref(numberConstant, "NUMBER", "LEFT"), literal("RIGHT", 1, "NUMBER", "100.00")));
        expect("NUMBER_NE", false, cmp("NE", ref(numberConstant, "NUMBER", "LEFT"), literal("RIGHT", 1, "NUMBER", "100")));
    }

    /** AND、OR、NOT 及嵌套。 */
    private void groupCases() {
        expect("AND_HIT", true, group("root", null, 1, "AND"), leaf("a", "root", 1, "GT", "50"),
            leaf("b", "root", 2, "LT", "200"));
        expect("AND_MISS", false, group("root", null, 1, "AND"), leaf("a", "root", 1, "GT", "50"),
            leaf("b", "root", 2, "GT", "200"));
        expect("OR_HIT", true, group("root", null, 1, "OR"), leaf("a", "root", 1, "GT", "1000"),
            leaf("b", "root", 2, "LT", "200"));
        expect("OR_MISS", false, group("root", null, 1, "OR"), leaf("a", "root", 1, "GT", "1000"),
            leaf("b", "root", 2, "GT", "200"));
        expect("NOT_HIT", true, group("root", null, 1, "NOT"), leaf("a", "root", 1, "GT", "1000"));
        expect("NESTED", true, group("root", null, 1, "OR"), group("and", "root", 1, "AND"),
            leaf("a", "and", 1, "GT", "1000"), leaf("b", "and", 2, "LT", "200"), group("not", "root", 2, "NOT"),
            leaf("c", "not", 1, "GT", "1000"));
    }

    /** 保存规则集、流程并激活后的评估请求。 */
    private EvaluateDecisionRequestBody request() {
        activate();
        return PaymentRiskRequestBuilder.template().amount(AMOUNT).cardBin(null).build();
    }

    /** 测试流程：条件规则集 → （节点命中 AND 金额 > 50）走审核结束，否则放行结束。 */
    private void activate() {
        long ruleSet = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_COND")).pointCode(PAYMENT)
            .rules(rules.toArray(RuleRequestBody[]::new)).build());
        GraphSpec graph = new GraphSpec(List.of(
            new NodeSpec("start", FlowNodeType.START, "Start", 0, 0, null, null, null, null),
            new NodeSpec("cond", FlowNodeType.RULE_SET, "Conditions", 120, 0, ruleSet, null, null, null),
            new NodeSpec("review", FlowNodeType.END, "Review", 240, -120, null, DecisionValue.REVIEW, null, null),
            new NodeSpec("allow", FlowNodeType.END, "Allow", 240, 0, null, DecisionValue.ALLOW, null, null)),
            List.of(new EdgeSpec("enter", "start", "cond", FlowEdgeType.UNCONDITIONAL, 0, "Enter", null),
                new EdgeSpec("to_review", "cond", "review", FlowEdgeType.CONDITIONAL, 1, "Matched and large",
                    matchedAndAmountAbove("50")),
                new EdgeSpec("to_allow", "cond", "allow", FlowEdgeType.ELSE, 0, "Otherwise", null)));
        activeRelease(saveFlow(newCode("T_FLOW"), graph));
    }

    /** 记录一条无终态动作规则及其期望命中。 */
    private void expect(String code, boolean hit, ConditionNodeRequestBody... nodes) {
        rules.add(rule(code, code, (rules.size() + 1) * 10, null, null, null, List.of(), nodes));
        expected.put(code, hit);
    }

    /** 单谓词根节点。 */
    private static ConditionNodeRequestBody cmp(String operator, OperandRequestBody... operands) {
        return predicate("root", null, 1, operator, operands);
    }

    /** 金额与字面量比较的子谓词。 */
    private static ConditionNodeRequestBody leaf(String key, String parent, int sequence, String operator, String value) {
        return predicate(key, parent, sequence, operator, amount(), money(value));
    }

    /** 支付输入变量（左值）。 */
    private static OperandRequestBody input(String code, String valueType) {
        return variableOperand(code, valueType);
    }

    /** 支付金额（左值）。 */
    private static OperandRequestBody amount() {
        return variableOperand("PAY_INPUT_AMOUNT", "NUMBER");
    }

    /** 指定角色的变量引用。 */
    private static OperandRequestBody ref(String code, String valueType, String role) {
        return new OperandRequestBody(role, 1, "VARIABLE", null, valueType, null, code, null, null, null);
    }

    /** 字符串字面量。 */
    private static OperandRequestBody text(String value) {
        return text(value, 1);
    }

    /** 指定序号的字符串字面量。 */
    private static OperandRequestBody text(String value, int sequence) {
        return literal("RIGHT", sequence, "STRING", value);
    }

    /** 指定序号、以支付币种为坐标的金额字面量。 */
    private static OperandRequestBody money(String amount, int sequence) {
        return new OperandRequestBody("RIGHT", sequence, "LITERAL", null, "NUMBER", amount, null, "MONEY", null,
            "PAY_INPUT_CURRENCY");
    }

    /** 创建支付决策点的常量变量，返回编码。 */
    private String constant(String prefix, String valueType, String numericUnit, String value) {
        String code = newCode(prefix);
        CreateVariableRequestBody body = featureVariable(code, "CONSTANT", valueType);
        body.setNumericUnit(numericUnit);
        body.setCurrentValue(value);
        postOk("/decision-variables/v1/variables", body);
        return code;
    }

    /** PUT 并要求成功。 */
    private void putOk(String path, Object body) {
        EntityExchangeResult<byte[]> result = http.exchange(HttpMethod.PUT, path, headers(), body);
        assertTrue(result.getStatus().is2xxSuccessful(), () -> path + " 返回 " + result.getStatus() + ": "
            + new String(result.getResponseBody() == null ? new byte[0] : result.getResponseBody()));
    }

    /** 流程连线条件：节点有命中且金额大于阈值。 */
    private static String matchedAndAmountAbove(String threshold) {
        return "{\"root\":{\"children\":[{\"kind\":\"PREDICATE\",\"operands\":["
            + operandJson("LEFT", "VARIABLE", "BOOLEAN", null, "PAY_NODE_MATCHED", null, null) + ","
            + operandJson("RIGHT", "LITERAL", "BOOLEAN", "true", null, null, null)
            + "],\"operator\":\"EQ\"},{\"kind\":\"PREDICATE\",\"operands\":["
            + operandJson("LEFT", "VARIABLE", "NUMBER", null, "PAY_INPUT_AMOUNT", null, null) + ","
            + operandJson("RIGHT", "LITERAL", "NUMBER", "\"" + threshold + "\"", null, "MONEY", "PAY_INPUT_CURRENCY")
            + "],\"operator\":\"GT\"}],\"kind\":\"GROUP\",\"operator\":\"AND\"},\"schemaVersion\":2}";
    }

    /** 统一操作数 JSON；字段按键名排序。 */
    private static String operandJson(String role, String source, String valueType, String literal, String variable,
        String unit, String currencyVariable) {
        return "{\"currencyCode\":null,\"currencyVariableCode\":" + quote(currencyVariable) + ",\"fieldCode\":null,"
            + "\"literalValue\":" + (literal == null ? "null" : literal) + ",\"numericUnit\":" + quote(unit)
            + ",\"operandRole\":\"" + role + "\",\"sourceType\":\"" + source + "\",\"valueSequence\":1,"
            + "\"valueType\":\"" + valueType + "\",\"variableCode\":" + quote(variable) + "}";
    }

    /** 可空字符串的 JSON 表示。 */
    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
