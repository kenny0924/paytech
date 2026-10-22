package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.domain.enums.DataClassification.INTERNAL;
import static com.paytech.risk.decision.domain.enums.DecisionValueType.*;
import static com.paytech.risk.decision.domain.enums.NumericUnit.MONEY;
import static com.paytech.risk.decision.domain.enums.NumericUnit.SCALAR;
import static com.paytech.risk.decision.domain.enums.VariableUsage.ACTION_PARAMETER;
import static com.paytech.risk.decision.domain.enums.VariableUsage.FLOW_CONDITION;
import static com.paytech.risk.decision.domain.enums.VariableUsage.RULE_CONDITION;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 变量编排能力的 HTTP 响应 Checker
 * <li>期望值是已接通的工程合同：CHECKOUT_PREPARE 的 ORDER_PREPARATION v5 输入（v4 旧合同只读，不在目录中）及 RULE_SET_NODE_RESULT v1 节点输出，
 * 支付尝试的 PAYMENT_ATTEMPT v1 输入与节点输出。</li>
 * <li>两个决策点都支持实时累计与名单命中；Feature 目录按点列出事件、主体、统计方式与窗口，Client、运营主体只开放次数、最近一次（≤ 1 天）与金额合计（≤ 1 小时）。</li>
 * <li>整份响应按 JSON 树精确比较，字段、顺序与类型均不得偏离。</li>
 *
 * @author Kenny
 */
public final class VariableAuthoringContextResponseChecker
    extends AbstractHttpResponseChecker<VariableAuthoringContextResponseChecker> {

    /** 结账准备决策点。 */
    private static final String CHECKOUT = "CHECKOUT_PREPARE";

    /** 支付尝试决策点。 */
    private static final String PAYMENT = "PAYMENT_ATTEMPT_RISK";

    private final ObjectMapper mapper;

    public VariableAuthoringContextResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    @Override
    protected VariableAuthoringContextResponseChecker self() {
        return this;
    }

    /** 不限决策点：返回结账与支付两个决策点的全部来源合同、全部来源/类型/格式/量纲及容量上限。 */
    public VariableAuthoringContextResponseChecker assertContext() {
        return assertContext(null);
    }

    /**
     * 返回编排能力
     *
     * @param pointCode 查询的决策点；为空时来源合同不按决策点过滤
     */
    public VariableAuthoringContextResponseChecker assertContext(String pointCode) {
        assertStatus(200);
        JsonNode expected = mapper.valueToTree(expected(pointCode));
        JsonNode actual = body();
        assertAll(() -> assertEquals(expected.propertyNames(), actual.propertyNames(), "字段集合"),
            () -> assertEquals(expected.get("supportedPoints"), actual.get("supportedPoints"), "supportedPoints"),
            () -> assertEquals(expected.get("sources"), actual.get("sources"), "sources"),
            () -> assertEquals(expected.get("valueTypes"), actual.get("valueTypes"), "valueTypes"),
            () -> assertEquals(expected.get("formatProfiles"), actual.get("formatProfiles"), "formatProfiles"),
            () -> assertEquals(expected.get("numericUnits"), actual.get("numericUnits"), "numericUnits"),
            () -> assertEquals(expected.get("limits"), actual.get("limits"), "limits"),
            () -> assertEquals(expected.get("contracts"), actual.get("contracts"), "contracts"),
            () -> assertEquals(expected.get("featureCatalogs"), actual.get("featureCatalogs"), "featureCatalogs"));
        return this;
    }

    private static Map<String, Object> expected(String pointCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supportedPoints", List.of(Map.of("pointCode", CHECKOUT, "pointName", CHECKOUT),
            Map.of("pointCode", PAYMENT, "pointName", PAYMENT)));
        body.put("sources", Stream.of(VariableSource.INPUT, VariableSource.CONSTANT, VariableSource.COLLECTION,
            VariableSource.NODE_OUTPUT, VariableSource.VELOCITY, VariableSource.LIST).map(source -> Map.of("sourceType",
                source.getCode(), "createAllowed", createAllowed(source))).toList());
        body.put("valueTypes", Stream.of(STRING, NUMBER, BOOLEAN, DATETIME, DURATION).map(DecisionValueType::getCode).toList());
        body.put("formatProfiles", List.of(format(VariableFormat.TEXT, "文本，保留大小写和前导零"),
            format(VariableFormat.UPPER_ALPHA_2, "两位大写字母"), format(VariableFormat.UPPER_ALPHA_3, "三位大写字母"),
            format(VariableFormat.DIGITS_4, "四位数字字符串"), format(VariableFormat.TOKEN, "大写业务编码"),
            format(VariableFormat.BCP47, "语言地区标签"), format(VariableFormat.POSITIVE_ID, "正整数标识字符串"),
            format(VariableFormat.NONE, "无字符串格式")));
        body.put("numericUnits", List.of(SCALAR.getCode(), MONEY.getCode()));
        body.put("limits", Map.of("memberCount", 1000, "memberBodyBytes", 65536, "literalSetCount", 100));
        List<Map<String, Object>> contracts = new java.util.ArrayList<>(List.of(contract(CHECKOUT, RuleSetBuilder.INPUT_CONTRACT_CODE,
            RuleSetBuilder.CURRENT_CONTRACT_VERSION, VariableSource.INPUT, null, List.of(
            field("client_id", STRING, true, List.of(ACTION_PARAMETER, FLOW_CONDITION, RULE_CONDITION), null, null,
                "CLIENT_ID"),
            condition("entity_no", STRING, null, null, null), condition("entity_source_type", STRING, null, null, null),
            field("local_partner_id", STRING, false, List.of(FLOW_CONDITION, RULE_CONDITION), null, null, null),
            condition("consumer_country_code", STRING, null, null, null),
            condition("currency", STRING, null, null, "CURRENCY"),
            condition("merchandise_amount", NUMBER, MONEY, "currency", null),
            condition("discount_amount", NUMBER, MONEY, "currency", null),
            condition("net_amount", NUMBER, MONEY, "currency", null),
            condition("industry_code", STRING, null, null, null), condition("mcc_code", STRING, null, null, null),
            condition("line_count", NUMBER, SCALAR, null, null))),
            nodeResult(CHECKOUT)));
        if (pointCode == null || PAYMENT.equals(pointCode)) {
            contracts.add(contract(PAYMENT, "PAYMENT_ATTEMPT", 1, VariableSource.INPUT, null, List.of(
                condition("client_id", STRING, null, null, "CLIENT_ID"), condition("entity_no", STRING, null, null, null),
                condition("payment_method_code", STRING, null, null, null), condition("capture_mode", STRING, null, null, null),
                condition("amount", NUMBER, MONEY, "currency", null), condition("currency", STRING, null, null, "CURRENCY"),
                condition("industry_code", STRING, null, null, null), condition("mcc_code", STRING, null, null, null),
                field("consumer_country_code", STRING, false, List.of(FLOW_CONDITION, RULE_CONDITION), null, null, null),
                field("card_bin", STRING, false, List.of(FLOW_CONDITION, RULE_CONDITION), null, null, null),
                field("issuer_country_code", STRING, false, List.of(FLOW_CONDITION, RULE_CONDITION), null, null, null),
                field("card_brand", STRING, false, List.of(FLOW_CONDITION, RULE_CONDITION), null, null, null),
                field("funding_type", STRING, false, List.of(FLOW_CONDITION, RULE_CONDITION), null, null, null))));
            contracts.add(nodeResult(PAYMENT));
        }
        if (PAYMENT.equals(pointCode)) {
            contracts.removeIf(contract -> CHECKOUT.equals(contract.get("pointCode")));
        }
        body.put("contracts", contracts);
        List<Map<String, Object>> catalogs = new java.util.ArrayList<>();
        if (pointCode == null || CHECKOUT.equals(pointCode)) {
            catalogs.add(catalog(CHECKOUT, "INPUT_CURRENCY",
                List.of(event(VelocityEventCode.CHECKOUT_ASSESSED, "结账评估", true),
                    event(VelocityEventCode.CHECKOUT_REJECTED, "结账被拦截（审核或拒绝）", false)),
                List.of(concentrated(RiskSubjectType.CLIENT), concentrated(RiskSubjectType.OPERATING_ENTITY)),
                List.of(), List.of(RiskSubjectType.CLIENT, RiskSubjectType.OPERATING_ENTITY)));
        }
        if (pointCode == null || PAYMENT.equals(pointCode)) {
            List<RiskSubjectType> velocity = List.of(RiskSubjectType.CLIENT, RiskSubjectType.OPERATING_ENTITY,
                RiskSubjectType.CONSUMER, RiskSubjectType.IP_ADDRESS, RiskSubjectType.CARD_FINGERPRINT);
            catalogs.add(catalog(PAYMENT, "PAY_INPUT_CURRENCY",
                List.of(event(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, "支付尝试评估", true),
                    event(VelocityEventCode.PAYMENT_ATTEMPT_REJECTED, "支付尝试被拦截（审核或拒绝）", false)),
                List.of(concentrated(RiskSubjectType.CLIENT), concentrated(RiskSubjectType.OPERATING_ENTITY),
                    open(RiskSubjectType.CONSUMER), open(RiskSubjectType.IP_ADDRESS), open(RiskSubjectType.CARD_FINGERPRINT)),
                velocity, List.of(RiskSubjectType.CLIENT, RiskSubjectType.OPERATING_ENTITY, RiskSubjectType.CONSUMER,
                    RiskSubjectType.IP_ADDRESS, RiskSubjectType.CARD_FINGERPRINT, RiskSubjectType.CARD_BIN)));
        }
        body.put("featureCatalogs", catalogs);
        return body;
    }

    /** 一个决策点的 Feature 目录。 */
    private static Map<String, Object> catalog(String pointCode, String currencyVariableCode,
        List<Map<String, Object>> events, List<Map<String, Object>> anchors, List<RiskSubjectType> distinct,
        List<RiskSubjectType> lists) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("pointCode", pointCode);
        catalog.put("currencyVariableCode", currencyVariableCode);
        catalog.put("velocityEvents", events);
        catalog.put("velocityAnchors", anchors);
        catalog.put("distinctSubjects", distinct.stream().map(RiskSubjectType::getCode).toList());
        catalog.put("listSubjects", lists.stream().map(RiskSubjectType::getCode).toList());
        return catalog;
    }

    private static Map<String, Object> event(VelocityEventCode code, String name, boolean includeCurrent) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventCode", code.getCode());
        event.put("eventName", name);
        event.put("includeCurrentAllowed", includeCurrent);
        return event;
    }

    /** Client、运营主体：次数与最近一次 ≤ 1 天，金额合计 ≤ 1 小时，无不同主体数。 */
    private static Map<String, Object> concentrated(RiskSubjectType subject) {
        List<Integer> day = List.of(600, 3600, 86_400);
        return anchor(subject, List.of(metric(VelocityMetric.COUNT, day, true),
            metric(VelocityMetric.SUM_AMOUNT, List.of(600, 3600), true), metric(VelocityMetric.LAST_AMOUNT, day, false),
            metric(VelocityMetric.SECONDS_SINCE_LAST, day, false)));
    }

    /** 其他主体：五种统计方式 × 四个窗口。 */
    private static Map<String, Object> open(RiskSubjectType subject) {
        List<Integer> all = List.of(600, 3600, 86_400, 604_800);
        return anchor(subject, List.of(metric(VelocityMetric.COUNT, all, true),
            metric(VelocityMetric.SUM_AMOUNT, all, true), metric(VelocityMetric.DISTINCT_COUNT, all, false),
            metric(VelocityMetric.LAST_AMOUNT, all, false), metric(VelocityMetric.SECONDS_SINCE_LAST, all, false)));
    }

    private static Map<String, Object> anchor(RiskSubjectType subject, List<Map<String, Object>> metrics) {
        Map<String, Object> anchor = new LinkedHashMap<>();
        anchor.put("subjectType", subject.getCode());
        anchor.put("metrics", metrics);
        return anchor;
    }

    private static Map<String, Object> metric(VelocityMetric metric, List<Integer> windows, boolean includeCurrent) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("metric", metric.getCode());
        value.put("windowSeconds", windows);
        value.put("includeCurrentAllowed", includeCurrent);
        return value;
    }

    /** 运营可创建的来源：节点输出只由工程登记；两个决策点都支持实时累计与名单命中。 */
    private static boolean createAllowed(VariableSource source) {
        return source != VariableSource.NODE_OUTPUT;
    }

    /** 规则集节点结果合同。 */
    private static Map<String, Object> nodeResult(String pointCode) {
        return contract(pointCode, "RULE_SET_NODE_RESULT", 1, VariableSource.NODE_OUTPUT, "RULE_SET", List.of(
            field("matched", BOOLEAN, true, List.of(FLOW_CONDITION), null, null, null),
            field("decision", STRING, false, List.of(FLOW_CONDITION), null, null, null)));
    }

    private static Map<String, Object> format(VariableFormat format, String label) {
        return Map.of("formatCode", format.getCode(), "label", label, "help", label);
    }

    private static Map<String, Object> contract(String pointCode, String code, int version, VariableSource source,
        String nodeType, List<Map<String, Object>> fields) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractCode", code);
        contract.put("contractVersion", version);
        contract.put("pointCode", pointCode);
        contract.put("sourceType", source.getCode());
        contract.put("nodeType", nodeType);
        contract.put("fields", fields);
        return contract;
    }

    /** 输入合同的条件字段：必填、内部分级，可用于规则与流程条件。 */
    private static Map<String, Object> condition(String key, DecisionValueType type, NumericUnit unit, String currencyField,
        String role) {
        return field(key, type, true, List.of(FLOW_CONDITION, RULE_CONDITION), unit, currencyField, role);
    }

    /** 用途按 Code 升序输出。 */
    private static Map<String, Object> field(String key, DecisionValueType type, boolean required,
        List<VariableUsage> usages, NumericUnit unit, String currencyField, String role) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldKey", key);
        field.put("valueType", type.getCode());
        field.put("required", required);
        field.put("dataClassification", INTERNAL.getCode());
        field.put("allowedUsages", usages.stream().map(VariableUsage::getCode).toList());
        field.put("numericUnit", unit == null ? null : unit.getCode());
        field.put("currencyFieldKey", currencyField);
        field.put("semanticRole", role);
        return field;
    }
}
