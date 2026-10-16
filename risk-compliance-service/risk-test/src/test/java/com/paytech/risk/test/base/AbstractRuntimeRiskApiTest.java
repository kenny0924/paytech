package com.paytech.risk.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.decision.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.api.decision.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.request.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.SaveFlowRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;

import tools.jackson.databind.JsonNode;

/**
 * 运行期风控点接口测试的公共入口辅助：统一评估、规则集冻结、决策流保存与执行发布
 * <li>规则、流程与发布都经真实配置入口准备；具体风控点由子类 {@link #pointCode()} 给出。</li>
 * <li>不含 @Test；数据由各风控点的 Mocker 在全局 AfterEach 回收。</li>
 *
 * @author Kenny
 */
public abstract class AbstractRuntimeRiskApiTest extends MockTestBase {
    /** 可信操作者 Account ID。 */
    protected static final long ACTOR_ID = 70001L;

    /** 统一评估入口。 */
    protected static final String ASSESSMENTS = "/risk-decisions/v1/evaluations";

    /** 执行发布入口。 */
    protected static final String EXECUTIONS = "/decision-releases/v1/executions";

    @Autowired
    protected RuleSetMocker ruleSets;

    /** 本测试的风控点编码。 */
    protected abstract String pointCode();

    /** Gateway 转发的 Operations 可信身份及新的 Request ID。 */
    protected static HttpHeaders headers() {
        HttpHeaders headers = TestRequestHeaders.internal("risk-runtime-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR_ID));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    /** 带随机后缀的大写编码。 */
    protected static String newCode(String prefix) {
        return prefix + "_" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
    }

    /** 调用统一评估入口。 */
    protected EvaluationResponseChecker assess(EvaluateDecisionRequestBody body) {
        HttpHeaders headers = TestRequestHeaders.internal("risk-assess-" + UUID.randomUUID());
        return new EvaluationResponseChecker(http.post(ASSESSMENTS, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** POST 并要求成功，返回响应体。 */
    protected JsonNode postOk(String path, Object body) {
        EntityExchangeResult<byte[]> result = http.post(path, headers(), body);
        assertTrue(result.getStatus().is2xxSuccessful(), () -> path + " 返回 " + result.getStatus() + ": "
            + new String(result.getResponseBody() == null ? new byte[0] : result.getResponseBody()));
        return objectMapper.readTree(result.getResponseBody());
    }

    /** 保存并冻结规则集，返回冻结的规则集版本 ID。 */
    protected long frozenRuleSet(CreateRuleSetVersionRequestBody body) {
        JsonNode created = postOk("/decision-rule-sets/v1/rule-sets", body);
        String versionId = created.path("ruleSetVersionId").asString();
        postOk("/decision-rule-sets/v1/rule-sets/" + versionId + "/freeze",
            new FreezeRuleSetVersionRequestBody(created.path("contentHash").asString()));
        return Long.parseLong(versionId);
    }

    /** 保存本风控点的决策流，返回流程版本 ID。 */
    protected long saveFlow(String flowCode, GraphSpec graph) {
        return Long.parseLong(postOk("/decision-flows/v1/flows", SaveFlowRequestBuilder.template(flowCode)
            .pointCode(pointCode()).graph(graph).build()).path("flowVersionId").asString());
    }

    /** 创建执行发布并完成静态校验，返回发布 ID；校验应通过。 */
    protected long validatedRelease(long flowVersionId) {
        CreateExecutionReleaseRequestBody body = new CreateExecutionReleaseRequestBody();
        body.setPointCode(pointCode());
        body.setFlowVersionId(Long.toString(flowVersionId));
        long releaseId = Long.parseLong(postOk(EXECUTIONS, body).path("releaseId").asString());
        JsonNode validated = postOk(EXECUTIONS + "/" + releaseId + "/validate", "{}");
        assertEquals(DecisionReleaseStatus.SHADOWING.getCode(), validated.path("status").asString(), "校验后状态");
        return releaseId;
    }

    /** 创建、校验并激活执行发布。 */
    protected long activeRelease(long flowVersionId) {
        long releaseId = validatedRelease(flowVersionId);
        JsonNode activated = postOk(EXECUTIONS + "/" + releaseId + "/activate", "{}");
        assertEquals(DecisionReleaseStatus.ACTIVE.getCode(), activated.path("status").asString(), "激活后状态");
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

    /** 普通数值字面量。 */
    protected static OperandRequestBody scalar(String value) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", value, null, "SCALAR", null, null);
    }

    /** 布尔字面量。 */
    protected static OperandRequestBody bool(boolean value) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "BOOLEAN", value, null, null, null, null);
    }
}
