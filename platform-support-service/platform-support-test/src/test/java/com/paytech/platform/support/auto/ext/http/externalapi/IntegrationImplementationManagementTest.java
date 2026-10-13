package com.paytech.platform.support.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.externalapi.IntegrationImplementationManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationListResponseBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationSummary;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiRequests;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 渠道标准 API 实现：合法流程保存与摘要、流程静态校验矩阵、重复实现、版本冲突、校验接口的版本提示与删除。
 *
 * @see IntegrationImplementationManagementApi#listImplementations(String, String, String)
 * @see IntegrationImplementationManagementApi#getImplementation(String, String, String, String)
 * @see IntegrationImplementationManagementApi#createImplementation(String, String, String,
 *      CreateImplementationRequestBody)
 * @see IntegrationImplementationManagementApi#updateImplementation(String, String, String, String,
 *      UpdateImplementationRequestBody)
 * @see IntegrationImplementationManagementApi#deleteImplementation(String, String, String, String,
 *      DeleteExternalApiConfigRequestBody)
 * @see IntegrationImplementationManagementApi#validateImplementation(String, String, String,
 *      ValidateImplementationRequestBody)
 */
class IntegrationImplementationManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/integrations/DEMO/implementations";

    private Operator admin;
    private Integration demo;
    private StandardApi pay;

    /** 本类公共：一名平台管理员、标准 API PAY、接入方 DEMO 及其下单原子 API。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = administrator();
        pay = externalApi.standardApi("PAY");
        demo = externalApi.integration("DEMO");
        externalApi.createOrderApi(demo);
    }

    /** 合法流程保存后详情原样返回，列表给出节点数、出口数、初始化项与总预算，并写入成功审计。 */
    @Test
    void test_createAndGet() {
        var flow = ExternalApiSamples.payFlow();

        assertMutation(post(PATH, admin, ExternalApiRequests.createImplementation("PAY", flow)),
            "INTEGRATION_IMPLEMENTATION", "DEMO/PAY", 1);
        auditChecker.assertEvent(requestId, "OPERATIONS_INTEGRATION_IMPLEMENTATION_CREATE", AuditModels.Result.SUCCEEDED.getCode(), admin.actor(),
            "INTEGRATION_IMPLEMENTATION", "DEMO/PAY");

        var detail = get(PATH + "/PAY", admin).assertOk().as(ImplementationResponseBody.class);
        var items = get(PATH, admin).assertOk().as(ImplementationListResponseBody.class).getItems();

        assertEquals(1, items.size());
        ImplementationSummary summary = items.getFirst();
        assertAll(() -> assertEquals(flow, detail.getFlowDefinition()), () -> assertEquals(1, detail.getVersion()),
            () -> assertEquals("PAY", summary.getStandardApiCode()), () -> assertEquals(1, summary.getNodeCount()),
            () -> assertEquals(3, summary.getReturnCount()),
            () -> assertEquals(List.of("channelAccount"), summary.getInitializerIds()),
            () -> assertEquals(20000, summary.getTimeoutMs()));
    }

    /** 同一接入方重复实现、引用不存在的标准 API 都是业务校验失败。 */
    @Test
    void test_duplicateAndMissingStandardApi() {
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());

        post(PATH, admin, ExternalApiRequests.createImplementation("PAY", ExternalApiSamples.payFlow()))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(PATH, admin, ExternalApiRequests.createImplementation("REFUND", ExternalApiSamples.payFlow()))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        get(PATH + "/REFUND", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 每类流程错误：校验接口在 issues 中给出编码与位置，保存返回 422 且字段错误指向同一位置，不落库。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFlows")
    void test_invalidFlow(String scenario, Consumer<ObjectNode> mutation, String code, String location) {
        ObjectNode flow = ExternalApiSamples.payFlow();
        mutation.accept(flow);

        var result = post(PATH + "/validate", admin, ExternalApiRequests.validateImplementation("PAY", flow, null))
            .assertOk().as(ValidationResponseBody.class);
        post(PATH, admin, ExternalApiRequests.createImplementation("PAY", flow)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError(location);

        assertFalse(result.isValid());
        assertTrue(result.getIssues().stream().map(ExternalApiTestBase::issue).toList().contains(code + "@" + location),
            () -> "缺少 " + code + "@" + location + "，实际："
                + result.getIssues().stream().map(ExternalApiTestBase::issue).toList());
        get(PATH + "/PAY", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    static Stream<Arguments> invalidFlows() {
        return Stream.of(
            Arguments.of("缺少兜底连线", (Consumer<ObjectNode>)flow -> edges(flow).remove(2), "DEFAULT_EDGE_COUNT",
                "flowDefinition.edges"),
            Arguments.of("同一起点优先级重复", (Consumer<ObjectNode>)flow -> edge(flow, 1).put("priority", 1),
                "PRIORITY_DUPLICATED", "flowDefinition.edges[1].priority"),
            Arguments.of("必填请求字段未赋值", (Consumer<ObjectNode>)flow -> request(flow).remove("BODY:amount"),
                "REQUEST_FIELD_UNCOVERED", "flowDefinition.nodes[0].request"),
            Arguments.of("固定值字段被节点赋值",
                (Consumer<ObjectNode>)flow -> request(flow).set("HEADER:x-api-version", constant("'3'")),
                "REQUEST_FIELD_FIXED", "flowDefinition.nodes[0].request.HEADER:x-api-version"),
            Arguments.of("出口缺必填输出", (Consumer<ObjectNode>)flow -> outputs(flow, 1).remove("paymentStatus"),
                "OUTPUT_FIELD_UNCOVERED", "flowDefinition.returns[1].outputs"),
            Arguments.of("ERROR 出口写输出",
                (Consumer<ObjectNode>)flow -> ((ObjectNode)returns(flow).get(2)).set("outputs",
                    ExternalApiSamples.object("{}")),
                "ERROR_OUTPUTS_NOT_ALLOWED", "flowDefinition.returns[2].outputs"),
            Arguments.of("受控句柄绑定常量",
                (Consumer<ObjectNode>)flow -> params(flow).set("signingKey", constant("'raw-key'")),
                "HANDLE_BINDING_INVALID", "flowDefinition.nodes[0].params.signingKey"),
            Arguments.of("凭据写入普通请求字段",
                (Consumer<ObjectNode>)flow -> request(flow).set("BODY:amount",
                    path("resources.channelAccount.credentials.apiKey")),
                "REFERENCE_NOT_ALLOWED", "flowDefinition.nodes[0].request.BODY:amount.path"),
            Arguments.of("出口读取非必经节点", (Consumer<ObjectNode>)FlowVariants::readQueryOnSuccess, "STEP_NOT_ON_ALL_PATHS",
                "flowDefinition.returns[0].outputs.transactionRef"),
            Arguments.of("节点之间成环", (Consumer<ObjectNode>)FlowVariants::loopBack, "FLOW_CYCLE", "flowDefinition.edges"),
            Arguments.of("出口不可达",
                (Consumer<ObjectNode>)flow -> returns(flow)
                    .add(ExternalApiSamples.object("{\"id\":\"orphan\",\"executeStatus\":\"ERROR\"}")),
                "UNREACHABLE", "flowDefinition"),
            Arguments.of("原子 API 不属于本接入方", (Consumer<ObjectNode>)flow -> node(flow, 0).put("integrationApi", "NOPE"),
                "INTEGRATION_API_NOT_FOUND", "flowDefinition.nodes[0].integrationApi"));
    }

    /** 修改按版本替换流程；旧版本返回 409 且保留新流程。 */
    @Test
    void test_updateAndStaleVersion() {
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());
        ObjectNode faster = ExternalApiSamples.payFlow();
        faster.put("timeoutMs", 8000);

        assertMutation(put(PATH + "/PAY", admin, ExternalApiRequests.updateImplementation(faster, 1)),
            "INTEGRATION_IMPLEMENTATION", "DEMO/PAY", 2);
        put(PATH + "/PAY", admin, ExternalApiRequests.updateImplementation(ExternalApiSamples.payFlow(), 1))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);

        var detail = get(PATH + "/PAY", admin).assertOk().as(ImplementationResponseBody.class);
        assertAll(() -> assertEquals(2, detail.getVersion()),
            () -> assertEquals(8000, detail.getFlowDefinition().path("timeoutMs").asInt()));
    }

    /** 校验接口：新建时已实现报错，修改时版本落后只给警告，标准 API 不存在单独报告。 */
    @Test
    void test_validateVersionHints() {
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());
        assertMutation(
            put(PATH + "/PAY", admin, ExternalApiRequests.updateImplementation(ExternalApiSamples.payFlow(), 1)),
            "INTEGRATION_IMPLEMENTATION", "DEMO/PAY", 2);

        var asCreate = post(PATH + "/validate", admin,
            ExternalApiRequests.validateImplementation("PAY", ExternalApiSamples.payFlow(), null)).assertOk()
            .as(ValidationResponseBody.class);
        var stale = post(PATH + "/validate", admin,
            ExternalApiRequests.validateImplementation("PAY", ExternalApiSamples.payFlow(), 1)).assertOk()
            .as(ValidationResponseBody.class);
        var missing = issues(post(PATH + "/validate", admin,
            ExternalApiRequests.validateImplementation("REFUND", ExternalApiSamples.payFlow(), null)));

        assertAll(() -> assertFalse(asCreate.isValid()),
            () -> assertEquals(List.of("IMPLEMENTATION_EXISTS@standardApiCode"),
                asCreate.getIssues().stream().map(ExternalApiTestBase::issue).toList()),
            () -> assertTrue(stale.isValid()),
            () -> assertEquals(ValidationIssue.WARNING, stale.getIssues().getFirst().getSeverity()),
            () -> assertEquals("VERSION_STALE", stale.getIssues().getFirst().getCode()),
            () -> assertEquals(List.of("STANDARD_API_NOT_FOUND@standardApiCode"), missing));
    }

    /** 按版本删除实现，之后不可读取；不存在的实现返回 404。 */
    @Test
    void test_delete() {
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());

        assertMutation(delete(PATH + "/PAY", admin, ExternalApiRequests.delete(1)), "INTEGRATION_IMPLEMENTATION",
            "DEMO/PAY", 1);

        get(PATH + "/PAY", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        delete(PATH + "/PAY", admin, ExternalApiRequests.delete(1)).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    private static ArrayNode edges(ObjectNode flow) {
        return (ArrayNode)flow.get("edges");
    }

    private static ObjectNode edge(ObjectNode flow, int index) {
        return (ObjectNode)edges(flow).get(index);
    }

    private static ArrayNode returns(ObjectNode flow) {
        return (ArrayNode)flow.get("returns");
    }

    private static ObjectNode outputs(ObjectNode flow, int index) {
        return (ObjectNode)returns(flow).get(index).get("outputs");
    }

    private static ObjectNode node(ObjectNode flow, int index) {
        return (ObjectNode)flow.get("nodes").get(index);
    }

    private static ObjectNode request(ObjectNode flow) {
        return (ObjectNode)node(flow, 0).get("request");
    }

    private static ObjectNode params(ObjectNode flow) {
        return (ObjectNode)node(flow, 0).get("params");
    }

    private static ObjectNode constant(String expression) {
        return ExternalApiSamples.object("{\"kind\":\"EXPRESSION\",\"expression\":\"" + expression + "\"}");
    }

    private static ObjectNode path(String path) {
        return ExternalApiSamples.object("{\"kind\":\"PATH\",\"path\":\"" + path + "\"}");
    }

    /** 需要第二个节点的流程变形。 */
    static final class FlowVariants {

        private FlowVariants() {}

        /** 下单 ERROR 时补查，成功出口却读取补查节点：直接成功的路径上补查未执行。 */
        static void readQueryOnSuccess(ObjectNode flow) {
            addQueryNode(flow);
            edges(flow).add(ExternalApiSamples.object(
                """
                    {"from":"createOrder","to":"queryOrder","condition":"steps.createOrder.executeStatus == 'ERROR'","priority":3}
                    """));
            edges(flow).add(ExternalApiSamples.object("{\"from\":\"queryOrder\",\"to\":\"paid\",\"default\":true}"));
            outputs(flow, 0).set("transactionRef", path("steps.queryOrder.outputs.transactionRef"));
        }

        /** 下单 ERROR 时补查，补查兜底又回到下单节点。 */
        static void loopBack(ObjectNode flow) {
            addQueryNode(flow);
            edges(flow).add(ExternalApiSamples.object(
                """
                    {"from":"createOrder","to":"queryOrder","condition":"steps.createOrder.executeStatus == 'ERROR'","priority":3}
                    """));
            edges(flow)
                .add(ExternalApiSamples.object("{\"from\":\"queryOrder\",\"to\":\"createOrder\",\"default\":true}"));
        }

        /** 第二个节点复用下单原子 API 的完整请求与参数绑定。 */
        static void addQueryNode(ObjectNode flow) {
            ObjectNode query = node(flow, 0).deepCopy();
            query.put("id", "queryOrder");
            ((ArrayNode)flow.get("nodes")).add(query);
        }
    }
}
