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
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableUsage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 变量编排能力的 HTTP 响应 Checker
 * <li>期望值是本期已接通的工程合同：CHECKOUT_PREPARE 的 ORDER_PREPARATION v4 输入及 RULE_SET_NODE_RESULT v1 节点输出。</li>
 * <li>整份响应按 JSON 树精确比较，字段、顺序与类型均不得偏离。</li>
 *
 * @author Kenny
 */
public final class VariableAuthoringContextResponseChecker
    extends AbstractHttpResponseChecker<VariableAuthoringContextResponseChecker> {

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

    /** 返回完整编排能力：两个来源合同、全部来源/类型/格式/量纲及容量上限。 */
    public VariableAuthoringContextResponseChecker assertContext() {
        assertStatus(200);
        JsonNode expected = mapper.valueToTree(expected());
        JsonNode actual = body();
        assertAll(() -> assertEquals(expected.propertyNames(), actual.propertyNames(), "字段集合"),
            () -> assertEquals(expected.get("supportedPoints"), actual.get("supportedPoints"), "supportedPoints"),
            () -> assertEquals(expected.get("sources"), actual.get("sources"), "sources"),
            () -> assertEquals(expected.get("valueTypes"), actual.get("valueTypes"), "valueTypes"),
            () -> assertEquals(expected.get("formatProfiles"), actual.get("formatProfiles"), "formatProfiles"),
            () -> assertEquals(expected.get("numericUnits"), actual.get("numericUnits"), "numericUnits"),
            () -> assertEquals(expected.get("limits"), actual.get("limits"), "limits"),
            () -> assertEquals(expected.get("contracts"), actual.get("contracts"), "contracts"));
        return this;
    }

    private static Map<String, Object> expected() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supportedPoints", List.of(Map.of("pointCode", "CHECKOUT_PREPARE", "pointName", "CHECKOUT_PREPARE")));
        body.put("sources", Stream.of(VariableSource.INPUT, VariableSource.CONSTANT, VariableSource.COLLECTION,
            VariableSource.NODE_OUTPUT).map(source -> Map.of("sourceType", source.getCode(),
                "createAllowed", source != VariableSource.NODE_OUTPUT)).toList());
        body.put("valueTypes", Stream.of(STRING, NUMBER, BOOLEAN, DATETIME, DURATION).map(DecisionValueType::getCode).toList());
        body.put("formatProfiles", List.of(format(VariableFormat.TEXT, "文本，保留大小写和前导零"),
            format(VariableFormat.UPPER_ALPHA_2, "两位大写字母"), format(VariableFormat.UPPER_ALPHA_3, "三位大写字母"),
            format(VariableFormat.DIGITS_4, "四位数字字符串"), format(VariableFormat.TOKEN, "大写业务编码"),
            format(VariableFormat.BCP47, "语言地区标签"), format(VariableFormat.POSITIVE_ID, "正整数标识字符串"),
            format(VariableFormat.NONE, "无字符串格式")));
        body.put("numericUnits", List.of(SCALAR.getCode(), MONEY.getCode()));
        body.put("limits", Map.of("memberCount", 1000, "memberBodyBytes", 65536, "literalSetCount", 100));
        body.put("contracts", List.of(contract("ORDER_PREPARATION", 4, VariableSource.INPUT, null, List.of(
            condition("country_code", STRING, null, null, null), condition("locale", STRING, null, null, null),
            condition("industry_code", STRING, null, null, null), condition("mcc_code", STRING, null, null, null),
            condition("currency", STRING, null, null, "CURRENCY"),
            condition("merchandise_amount", NUMBER, MONEY, "currency", null),
            condition("discount_amount", NUMBER, MONEY, "currency", null),
            condition("net_amount", NUMBER, MONEY, "currency", null),
            condition("entity_source_type", STRING, null, null, null),
            field("client_id", STRING, true, List.of(ACTION_PARAMETER), null, null, "CLIENT_ID"))),
            contract("RULE_SET_NODE_RESULT", 1, VariableSource.NODE_OUTPUT, "RULE_SET", List.of(
                field("matched", BOOLEAN, true, List.of(FLOW_CONDITION), null, null, null),
                field("decision", STRING, false, List.of(FLOW_CONDITION), null, null, null)))));
        return body;
    }

    private static Map<String, Object> format(VariableFormat format, String label) {
        return Map.of("formatCode", format.getCode(), "label", label, "help", label);
    }

    private static Map<String, Object> contract(String code, int version, VariableSource source, String nodeType,
        List<Map<String, Object>> fields) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractCode", code);
        contract.put("contractVersion", version);
        contract.put("pointCode", "CHECKOUT_PREPARE");
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
