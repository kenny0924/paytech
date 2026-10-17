package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.domain.enums.ComparisonOperator.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 规则配置能力目录的 HTTP 响应 Checker
 * <li>期望值来自 CHECKOUT_PREPARE 当前合同（ORDER_PREPARATION v5）：决策点描述、种子系统 INPUT 变量、三个终态动作及当前 Client 主体绑定；结账已接入运行期，不再开放附加动作及其参数。</li>
 * <li>逐个顶层字段比较完整 JSON 结构，数组顺序即合同顺序。</li>
 *
 * @author Kenny
 */
public final class RuleAuthoringContextResponseChecker
    extends AbstractHttpResponseChecker<RuleAuthoringContextResponseChecker> {

    private final ObjectMapper mapper;

    public RuleAuthoringContextResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    @Override
    protected RuleAuthoringContextResponseChecker self() {
        return this;
    }

    /** CHECKOUT_PREPARE 的完整配置能力目录。 */
    public RuleAuthoringContextResponseChecker assertCheckoutPrepareCatalog() {
        assertStatus(200);
        JsonNode body = body();
        assertAll("配置能力目录",
            () -> assertEquals(Stream.of("actionParameters", "actions", "descriptorCode", "features", "fields",
                "limitBindings", "obligations", "operandFormat", "operators", "point", "targetBindings",
                "variableQueryContext").sorted().toList(), body.propertyNames().stream().sorted().toList(), "字段集合"),
            () -> assertEquals("RISK_VARIABLE_AUTHORING", body.path("descriptorCode").asString(), "descriptorCode"),
            () -> assertEquals(tree(point()), body.path("point"), "point"),
            () -> assertEquals(tree(fields()), body.path("fields"), "fields"),
            () -> assertEquals(tree(operators()), body.path("operators"), "operators"),
            () -> assertEquals(tree(actions()), body.path("actions"), "actions"),
            () -> assertEquals(tree(List.of()), body.path("features"), "features"),
            () -> assertEquals(tree(List.of()), body.path("actionParameters"), "actionParameters"),
            () -> assertEquals(tree(List.of()), body.path("obligations"), "obligations"),
            () -> assertEquals(tree(List.of()), body.path("limitBindings"), "limitBindings"),
            () -> assertEquals(tree(targetBindings()), body.path("targetBindings"), "targetBindings"),
            () -> assertEquals(RuleSetCreateRequestBuilder.VARIABLE_REFERENCE, body.path("operandFormat").asString(),
                "operandFormat"),
            () -> assertEquals(tree(object("pointCode", RuleSetBuilder.POINT_CODE, "inputContractCode",
                RuleSetBuilder.INPUT_CONTRACT_CODE, "inputContractVersion", RuleSetBuilder.CURRENT_CONTRACT_VERSION, "usage",
                VariableUsage.RULE_CONDITION.getCode())),
                body.path("variableQueryContext"), "variableQueryContext"));
        return this;
    }

    private static Map<String, Object> point() {
        return object("pointCode", RuleSetBuilder.POINT_CODE, "pointName", "Checkout Prepare", "businessDomain",
            DecisionBusinessDomain.CHECKOUT.getCode(), "inputContractCode", RuleSetBuilder.INPUT_CONTRACT_CODE,
            "inputContractVersion", RuleSetBuilder.CURRENT_CONTRACT_VERSION, "outputContractCode", "RISK_DECISION_RESULT",
            "defaultDecision",
            DecisionValue.REVIEW.getCode(), "evaluationStrategy", EvaluationStrategy.FIRST_TERMINAL.getCode(),
            "failureStrategy", FailureStrategy.FAIL_CLOSED.getCode(), "timeoutMillis", 500, "status",
            ConfigurationStatus.ACTIVE.getCode());
    }

    /** 种子系统 INPUT 变量按来源字段排序；client_id 同时可作条件与动作参数，local_partner_id 可选。 */
    private static List<Object> fields() {
        return List.of(field("client_id", DecisionValueType.STRING, "INPUT_CLIENT_ID", true, true, true),
            field("consumer_country_code", DecisionValueType.STRING, "INPUT_COUNTRY_CODE", true, true, false),
            field("currency", DecisionValueType.STRING, "INPUT_CURRENCY", true, true, false),
            field("discount_amount", DecisionValueType.NUMBER, "INPUT_DISCOUNT_AMOUNT", true, true, false),
            field("entity_no", DecisionValueType.STRING, "INPUT_ENTITY_NO", true, true, false),
            field("entity_source_type", DecisionValueType.STRING, "INPUT_ENTITY_SOURCE_TYPE", true, true, false),
            field("industry_code", DecisionValueType.STRING, "INPUT_INDUSTRY_CODE", true, true, false),
            field("line_count", DecisionValueType.NUMBER, "INPUT_LINE_COUNT", true, true, false),
            field("local_partner_id", DecisionValueType.STRING, "INPUT_LOCAL_PARTNER_ID", false, true, false),
            field("mcc_code", DecisionValueType.STRING, "INPUT_MCC_CODE", true, true, false),
            field("merchandise_amount", DecisionValueType.NUMBER, "INPUT_MERCHANDISE_AMOUNT", true, true, false),
            field("net_amount", DecisionValueType.NUMBER, "INPUT_NET_AMOUNT", true, true, false));
    }

    private static Map<String, Object> field(String fieldCode, DecisionValueType valueType, String variableCode,
        boolean required, boolean ruleReference, boolean actionParameter) {
        return object("fieldCode", fieldCode, "valueType", valueType.getCode(), "requiredFlag", required,
            "dataClassification", DataClassification.INTERNAL.getCode(), "ruleReferenceFlag", ruleReference,
            "actionParameterFlag", actionParameter, "variableCode", variableCode);
    }

    private static List<Object> operators() {
        return Stream.of(EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN, BETWEEN, IS_NULL, IS_NOT_NULL)
            .map(ComparisonOperator::getCode).map(Object.class::cast).toList();
    }

    /** 终态动作按动作编码排序；运行期决策点不再列出仅配置动作。 */
    private static List<Object> actions() {
        return List.of(terminalAction(RuleSetCreateRequestBuilder.ALLOW, "Allow Checkout", "DECISION_ALLOW_V1",
                "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1", DecisionValue.ALLOW),
            terminalAction(RuleSetCreateRequestBuilder.DENY, "Deny Checkout", "DECISION_DENY_V1",
                "8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7", DecisionValue.DENY),
            terminalAction(RuleSetCreateRequestBuilder.REVIEW, "Review Checkout", "DECISION_REVIEW_V1",
                "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716", DecisionValue.REVIEW));
    }

    private static Map<String, Object> terminalAction(String code, String name, String handler, String hash,
        DecisionValue decision) {
        return object("actionCode", code, "actionName", name, "actionCategory", ActionCategory.DECISION.getCode(),
            "versionNo", 1, "handlerCode", handler, "implementationVersion", "1.0.0", "implementationHash", hash,
            "terminalFlag", true, "decisionCode", decision.getCode(), "mergePolicy",
            MergePolicy.MOST_RESTRICTIVE.getCode(), "shadowSupportedFlag", true, "status",
            ConfigurationStatus.ACTIVE.getCode());
    }

    private static List<Object> targetBindings() {
        return List.of(object("bindingCode", RuleSetCreateRequestBuilder.CLIENT_TARGET, "displayName", "当前 Client",
            "subjectType", "CLIENT", "requiredFields", List.of(), "subjectSemanticRole", "CLIENT_ID"));
    }

    /** 允许 null 值的有序键值对。 */
    private static Map<String, Object> object(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String)keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    private JsonNode tree(Object value) {
        return mapper.valueToTree(value instanceof List<?> list ? new ArrayList<>(list) : value);
    }
}
