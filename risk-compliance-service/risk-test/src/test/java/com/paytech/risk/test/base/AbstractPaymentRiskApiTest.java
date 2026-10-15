package com.paytech.risk.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.control.request.AddRiskListEntryRequestBody;
import com.paytech.risk.api.control.request.CreateRiskListRequestBody;
import com.paytech.risk.api.decision.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.api.decision.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.decision.request.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.request.ListFeatureParameters;
import com.paytech.risk.api.decision.request.VelocityFeatureParameters;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.builder.decision.SaveFlowRequestBuilder;
import com.paytech.risk.test.checker.evaluation.PaymentRiskResponseChecker;
import com.paytech.risk.test.mapper.evaluation.PaymentRiskTestMapper;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.evaluation.PaymentRiskMocker;

import tools.jackson.databind.JsonNode;

/**
 * 支付风控接口测试的公共入口辅助
 * <li>评估场景经真实配置入口准备规则：变量、名单、规则集（冻结）、决策流与执行发布（校验并激活），再调用支付评估入口。</li>
 * <li>不含 @Test；全部数据由 {@link PaymentRiskMocker} 在全局 AfterEach 回收。</li>
 *
 * @author Kenny
 */
public abstract class AbstractPaymentRiskApiTest extends MockTestBase {
    /** 支付决策点。 */
    protected static final String PAYMENT = "PAYMENT_ATTEMPT_RISK";

    /** 可信操作者 Account ID。 */
    protected static final long ACTOR_ID = 70001L;

    /** 统一评估入口。 */
    protected static final String ASSESSMENTS = "/risk-decisions/v1/evaluations";

    /** 执行发布入口。 */
    protected static final String EXECUTIONS = "/decision-releases/v1/executions";

    @Autowired
    protected PaymentRiskMocker payments;

    @Autowired
    protected PaymentRiskTestMapper store;

    @Autowired
    protected RuleSetMocker ruleSets;

    /** Gateway 转发的 Operations 可信身份及新的 Request ID。 */
    protected static HttpHeaders headers() {
        HttpHeaders headers = TestRequestHeaders.internal("risk-payment-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR_ID));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    /** 带随机后缀的大写编码。 */
    protected static String newCode(String prefix) {
        return prefix + "_" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
    }

    /** 调用统一评估入口。 */
    protected PaymentRiskResponseChecker assess(EvaluateDecisionRequestBody body) {
        HttpHeaders headers = TestRequestHeaders.internal("risk-assess-" + UUID.randomUUID());
        return new PaymentRiskResponseChecker(http.post(ASSESSMENTS, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** POST 并要求成功，返回响应体。 */
    protected JsonNode postOk(String path, Object body) {
        EntityExchangeResult<byte[]> result = http.post(path, headers(), body);
        assertTrue(result.getStatus().is2xxSuccessful(), () -> path + " 返回 " + result.getStatus() + ": "
            + new String(result.getResponseBody() == null ? new byte[0] : result.getResponseBody()));
        return objectMapper.readTree(result.getResponseBody());
    }

    /** 创建名单。 */
    protected void createList(String listCode, String subjectType, String purpose) {
        CreateRiskListRequestBody body = new CreateRiskListRequestBody();
        body.setListCode(listCode);
        body.setListName("测试名单");
        body.setSubjectType(subjectType);
        body.setPurpose(purpose);
        postOk("/risk-lists/v1/lists", body);
    }

    /** 添加长期有效的名单成员，返回成员 ID。 */
    protected long addListEntry(String listCode, String subjectValue) {
        AddRiskListEntryRequestBody body = new AddRiskListEntryRequestBody();
        body.setSubjectValue(subjectValue);
        body.setReason("测试加入");
        return Long.parseLong(postOk("/risk-lists/v1/lists/" + listCode + "/entries", body).path("entryId").asString());
    }

    /** 创建实时累计变量；金额类算法以支付币种为币种来源。 */
    protected void createVelocityVariable(String code, String eventCode, String anchor, String metric, String distinct,
        int windowSeconds, boolean includeCurrent) {
        boolean money = "SUM_AMOUNT".equals(metric) || "LAST_AMOUNT".equals(metric);
        CreateVariableRequestBody body = featureVariable(code, "VELOCITY", "NUMBER");
        body.setNumericUnit(money ? "MONEY" : "SCALAR");
        body.setCurrencyVariableCode(money ? "PAY_INPUT_CURRENCY" : null);
        VelocityFeatureParameters velocity = new VelocityFeatureParameters();
        velocity.setEventCode(eventCode);
        velocity.setAnchorSubjectType(anchor);
        velocity.setMetric(metric);
        velocity.setDistinctSubjectType(distinct);
        velocity.setWindowSeconds(windowSeconds);
        velocity.setIncludeCurrent(includeCurrent);
        body.setVelocity(velocity);
        postOk("/decision-variables/v1/variables", body);
    }

    /** 创建名单命中变量。 */
    protected void createListVariable(String code, String listCode, String subjectType) {
        CreateVariableRequestBody body = featureVariable(code, "LIST", "BOOLEAN");
        ListFeatureParameters list = new ListFeatureParameters();
        list.setListCode(listCode);
        list.setSubjectType(subjectType);
        body.setList(list);
        postOk("/decision-variables/v1/variables", body);
    }

    /** Feature 变量的公共结构。 */
    protected static CreateVariableRequestBody featureVariable(String code, String sourceType, String valueType) {
        CreateVariableRequestBody body = new CreateVariableRequestBody();
        body.setVariableCode(code);
        body.setVariableName(code);
        body.setSourceType(sourceType);
        body.setValueType(valueType);
        body.setPointCode(PAYMENT);
        body.setUsages(List.of("RULE_CONDITION", "FLOW_CONDITION"));
        body.setFormatCode("NONE");
        body.setStatus("ACTIVE");
        return body;
    }

    /** 保存并冻结支付规则集，返回冻结的规则集版本 ID。 */
    protected long frozenRuleSet(CreateRuleSetVersionRequestBody body) {
        JsonNode created = postOk("/decision-rule-sets/v1/rule-sets", body);
        String versionId = created.path("ruleSetVersionId").asString();
        postOk("/decision-rule-sets/v1/rule-sets/" + versionId + "/freeze",
            new FreezeRuleSetVersionRequestBody(created.path("contentHash").asString()));
        return Long.parseLong(versionId);
    }

    /** 保存支付决策流，返回流程版本 ID。 */
    protected long saveFlow(String flowCode, GraphSpec graph) {
        return Long.parseLong(postOk("/decision-flows/v1/flows", SaveFlowRequestBuilder.template(flowCode)
            .pointCode(PAYMENT).graph(graph).build()).path("flowVersionId").asString());
    }

    /** 创建执行发布并完成静态校验，返回发布 ID；校验应通过。 */
    protected long validatedRelease(long flowVersionId) {
        CreateExecutionReleaseRequestBody body = new CreateExecutionReleaseRequestBody();
        body.setPointCode(PAYMENT);
        body.setFlowVersionId(Long.toString(flowVersionId));
        long releaseId = Long.parseLong(postOk(EXECUTIONS, body).path("releaseId").asString());
        JsonNode validated = postOk(EXECUTIONS + "/" + releaseId + "/validate", "{}");
        assertEquals("SHADOWING", validated.path("status").asString(), "校验后状态");
        return releaseId;
    }

    /** 创建、校验并激活执行发布。 */
    protected long activeRelease(long flowVersionId) {
        long releaseId = validatedRelease(flowVersionId);
        JsonNode activated = postOk(EXECUTIONS + "/" + releaseId + "/activate", "{}");
        assertEquals("ACTIVE", activated.path("status").asString(), "激活后状态");
        return releaseId;
    }

    /** 新的生效拒绝原因编码。 */
    protected String rejectionReason() {
        return ruleSets.mockRejectionReason(DictionaryStatus.ACTIVE);
    }

    /** 统一变量引用操作数。 */
    protected static OperandRequestBody variableOperand(String code, String valueType) {
        return new OperandRequestBody("LEFT", 1, "VARIABLE", null, valueType, null, code, null, null, null);
    }

    /** 以支付币种为币种坐标的金额字面量。 */
    protected static OperandRequestBody money(String amount) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", amount, null, "MONEY", null,
            "PAY_INPUT_CURRENCY");
    }

    /** 普通数值字面量。 */
    protected static OperandRequestBody scalar(String value) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", value, null, "SCALAR", null, null);
    }

    /** 布尔字面量。 */
    protected static OperandRequestBody bool(boolean value) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "BOOLEAN", value, null, null, null, null);
    }

    /** 流程连线条件：支付节点处理结果等于给定决定。 */
    protected static String nodeDecision(DecisionValue decision) {
        return "{\"root\":{\"kind\":\"PREDICATE\",\"operands\":["
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":null,"
            + "\"numericUnit\":null,\"operandRole\":\"LEFT\",\"sourceType\":\"VARIABLE\",\"valueSequence\":1,"
            + "\"valueType\":\"STRING\",\"variableCode\":\"PAY_NODE_DECISION\"},"
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":\"" + decision.getCode()
            + "\",\"numericUnit\":null,\"operandRole\":\"RIGHT\",\"sourceType\":\"LITERAL\",\"valueSequence\":1,"
            + "\"valueType\":\"STRING\",\"variableCode\":null}],\"operator\":\"EQ\"},\"schemaVersion\":2}";
    }

    /** 流程连线条件：上一规则集节点有规则命中。 */
    protected static String nodeMatched() {
        return "{\"root\":{\"kind\":\"PREDICATE\",\"operands\":["
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":null,"
            + "\"numericUnit\":null,\"operandRole\":\"LEFT\",\"sourceType\":\"VARIABLE\",\"valueSequence\":1,"
            + "\"valueType\":\"BOOLEAN\",\"variableCode\":\"PAY_NODE_MATCHED\"},"
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":true,"
            + "\"numericUnit\":null,\"operandRole\":\"RIGHT\",\"sourceType\":\"LITERAL\",\"valueSequence\":1,"
            + "\"valueType\":\"BOOLEAN\",\"variableCode\":null}],\"operator\":\"EQ\"},\"schemaVersion\":2}";
    }

    /** 要求 3DS 的放行结束节点输出。 */
    protected static Map<String, String> threeDsRequired() {
        return Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT, AuthenticationRequirement.THREE_DS_REQUIRED.getCode());
    }

    /**
     * 标准两段流程：拦截规则集 → 节点拒绝走拒绝结束、节点审核走审核结束，否则 → 认证规则集 → 有命中走“放行（需要 3DS）”结束，否则放行结束
     *
     * @param blockRuleSet 拦截规则集版本
     * @param authRuleSet 认证要求规则集版本
     * @param reasonCode 拒绝结束的原因编码
     * @return 配置图
     */
    protected static GraphSpec twoStageGraph(long blockRuleSet, long authRuleSet, String reasonCode) {
        return new GraphSpec(List.of(
            new NodeSpec("start", FlowNodeType.START, "Start", 0, 0, null, null, null, null),
            new NodeSpec("block", FlowNodeType.RULE_SET, "Block", 120, 0, blockRuleSet, null, null, null),
            new NodeSpec("auth", FlowNodeType.RULE_SET, "Auth", 240, 0, authRuleSet, null, null, null),
            new NodeSpec("deny", FlowNodeType.END, "Deny", 240, 120, null, DecisionValue.DENY, reasonCode, "支付被风控拒绝"),
            new NodeSpec("review", FlowNodeType.END, "Review", 240, -120, null, DecisionValue.REVIEW, null, null),
            new NodeSpec("allow", FlowNodeType.END, "Allow", 360, 0, null, DecisionValue.ALLOW, null, null),
            new NodeSpec("allow_3ds", FlowNodeType.END, "Allow with 3DS", 360, 120, null, DecisionValue.ALLOW, null, null,
                threeDsRequired())),
            List.of(new EdgeSpec("enter", "start", "block", FlowEdgeType.UNCONDITIONAL, 0, "Enter", null),
                new EdgeSpec("to_deny", "block", "deny", FlowEdgeType.CONDITIONAL, 1, "Denied",
                    nodeDecision(DecisionValue.DENY)),
                new EdgeSpec("to_review", "block", "review", FlowEdgeType.CONDITIONAL, 2, "Reviewed",
                    nodeDecision(DecisionValue.REVIEW)),
                new EdgeSpec("to_auth", "block", "auth", FlowEdgeType.ELSE, 0, "Otherwise", null),
                new EdgeSpec("to_3ds", "auth", "allow_3ds", FlowEdgeType.CONDITIONAL, 1, "Authentication", nodeMatched()),
                new EdgeSpec("finish", "auth", "allow", FlowEdgeType.ELSE, 0, "Finish", null)));
    }
}
