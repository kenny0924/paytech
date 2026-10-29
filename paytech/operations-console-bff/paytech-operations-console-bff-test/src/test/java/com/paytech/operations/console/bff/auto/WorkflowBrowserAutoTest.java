package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.ext.rest.WorkflowActionTemplateController;
import com.paytech.operations.console.bff.ext.rest.WorkflowAttachmentController;
import com.paytech.operations.console.bff.ext.rest.WorkflowDataQueryTemplateController;
import com.paytech.operations.console.bff.ext.rest.WorkflowDefinitionController;
import com.paytech.operations.console.bff.ext.rest.WorkflowInstanceController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发 Platform 工作流的端点（WF01—WF28：技术方案第 7 节接口 1—25，另含回调重试、
 * 我已处理与模板引用；WF29—WF32：重新验证与附件开始上传、完成上传、下载授权，即接口 20、26—28；WF33—WF36：REQ-05 立即检查、
 * data-query 模板选择、按编码发起与动作模板配置校验）：同路径、同方法、同 Query 与 Body，查询与命令都携带会话操作人；开始上传返回 201。
 * <li>Platform 由 Loopback Simulator 承担，PLT-I02 授权与 Owner 调用记录在同一 Simulator。</li>
 *
 * @see WorkflowActionTemplateController
 * @see WorkflowDefinitionController
 * @see WorkflowInstanceController
 * @see WorkflowAttachmentController
 * @see WorkflowDataQueryTemplateController
 */
class WorkflowBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/workflow-boundary.json";
    private static final String OWNER_REQUEST_ID = "platform-workflow-request";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、路径、Query、Body 与操作人 Header 准确转发一次，响应按合同原样返回；浏览器凭据与幂等键不转发。 */
    @ParameterizedTest(name = "{index} 工作流转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(row.path("response")));

        var response = send(row);

        assertEquals(row.path("status").asInt(), response.statusCode(), response.body());
        assertEquals(withoutNulls(row.path("response")), withoutNulls(JSON.readTree(response.body())));
        var call = ownerCall();
        String query = row.path("query").asString();
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(query.isEmpty() ? null : query.substring(1), call.query()),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (row.path("request").isObject()) {
            assertEquals(withoutNulls(row.path("request")), withoutNulls(JSON.readTree(call.body())));
        } else {
            assertTrue(call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        }
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问工作流接口。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertEquals(403, response.statusCode(), response.body());
        assertOnlyAuthorized();
    }

    /** 未知或重复 Query、查询带 Body、未知或强转的 Body 字段、非法路径 ID 与非法枚举均在访问 Platform 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "WF16");
        assertRejectedBeforePlatform(400, send(list, "?unknown=1", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(list, "?page=1&page=2", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(list, "", JSON.createObjectNode(), true));

        var create = row(BOUNDARY, "WF03");
        assertRejectedBeforePlatform(400, send(create, "", with(create, "unknown", "x"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "templateCode", "lower"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "actionType", "SCRIPT"), true));

        var status = row(BOUNDARY, "WF05");
        assertRejectedBeforePlatform(400, send(status, "", with(status, "expectedVersion", "2"), true));
        assertRejectedBeforePlatform(422, send(status, "", with(status, "status", "DRAFT"), true));

        var decide = row(BOUNDARY, "WF22");
        assertRejectedBeforePlatform(422, send(decide, "", with(decide, "decision", "MAYBE"), true));
        var badId = decide.deepCopy();
        ((ObjectNode) badId).put("path", "/workflow/v1/approvals/abc/decision");
        assertRejectedBeforePlatform(400, send(badId, "", decide.path("request"), true));

        var upload = row(BOUNDARY, "WF30");
        assertRejectedBeforePlatform(422, send(upload, "", with(upload, "mimeType", "PDF"), true));
        assertRejectedBeforePlatform(422, send(upload, "", with(upload, "sizeBytes", "0"), true));
        var badFile = row(BOUNDARY, "WF32").deepCopy();
        ((ObjectNode) badFile).put("path", "/workflow/v1/instances/7300000000000000401/files/abc/download");
        assertRejectedBeforePlatform(400, send(badFile, "", JSON.nullNode(), true));
        var download = row(BOUNDARY, "WF32");
        assertRejectedBeforePlatform(400, send(download, "?disposition=PREVIEW", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(download, "?disposition=inline", JSON.nullNode(), true));

        var start = row(BOUNDARY, "WF15");
        var badApprover = (ObjectNode) start.path("request").deepCopy();
        ((ObjectNode) badApprover.path("approvers").get(0)).putArray("approverIds").add("not-an-id");
        assertRejectedBeforePlatform(422, send(start, "", badApprover, true));

        assertEquals(403, send(create, "", create.path("request"), false).statusCode());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals));
    }

    /** 附件下载的 disposition Query 原样转发给 Platform，响应中实际生效的下载方式原样返回（含 INLINE 降为 ATTACHMENT）。 */
    @ParameterizedTest
    @CsvSource({"INLINE,INLINE", "INLINE,ATTACHMENT", "ATTACHMENT,ATTACHMENT"})
    void test_downloadDispositionForwarded(String requested, String effective) throws Exception {
        var row = row(BOUNDARY, "WF32");
        var upstream = withResponse(row, "disposition", effective);
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(upstream));

        var response = send(row, "?disposition=" + requested, JSON.nullNode(), true);

        assertEquals(200, response.statusCode(), response.body());
        var call = ownerCall();
        assertAll(() -> assertEquals("disposition=" + requested, call.query()),
            () -> assertEquals(effective, JSON.readTree(response.body()).path("disposition").asString()),
            () -> assertEquals(withoutNulls(upstream), withoutNulls(JSON.readTree(response.body()))));
    }

    /** 业务拒绝保留 Platform 状态、公共 Code 与原因（发布校验未通过、被他人处理、版本冲突），使用浏览器 Request ID。 */
    @ParameterizedTest
    @CsvSource({"WF10,400,BUSINESS_CHECK_FAILED,发布校验未通过，共 2 个问题，首个：任务节点没有动作或审批",
        "WF22,400,BUSINESS_CHECK_FAILED,审批已处理或节点不在待审批状态",
        "WF04,409,CONCURRENT_REQUEST,动作模板已被其他操作修改，请刷新后重试，当前版本 3",
        "WF17,404,NOT_FOUND,资源不存在"})
    void test_ownerRejectionShowsPlatformReason(String id, int status, String code, String detail) throws Exception {
        var row = row(BOUNDARY, id);
        platform.rawResponse(ownerPath(row), status, "application/problem+json", problem(status, code, detail),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code, body.path("code").asString()),
            () -> assertEquals(detail, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()));
        ownerCall();
    }

    /**
     * 附件接口的 Platform 文件错误码（类型或大小不符、文件不存在、状态冲突）按 4xx 外显中文原因，不当作上游无效响应 502；
     * 例如声明 image/png 而实际上传 PDF 时完成上传被拒。
     */
    @ParameterizedTest
    @CsvSource({"WF31,422,FILE_POLICY_REJECTED,文件类型或大小不符合要求,422",
        "WF30,422,FILE_POLICY_REJECTED,文件类型或大小不符合要求,422",
        "WF31,404,FILE_NOT_FOUND,文件目标不可访问,404",
        "WF32,409,FILE_STATE_CONFLICT,当前文件状态不允许此操作，请刷新申请,409"})
    void test_attachmentFileRejection(String id, int ownerStatus, String code, String detail, int status)
        throws Exception {
        var row = row(BOUNDARY, id);
        platform.rawResponse(ownerPath(row), ownerStatus, "application/problem+json", problem(ownerStatus, code, detail),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code, body.path("code").asString()),
            () -> assertEquals(detail, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()));
        ownerCall();
    }

    /** Platform 503 映射为依赖不可用，不外露 Platform 原文。 */
    @ParameterizedTest
    @CsvSource({"WF17", "WF22"})
    void test_ownerUnavailable(String id) throws Exception {
        var row = row(BOUNDARY, id);
        platform.problem(ownerPath(row), 503, "SERVICE_UNAVAILABLE");

        var response = send(row);

        assertEquals(503, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals("DEPENDENCY_UNAVAILABLE", body.path("code").asString()),
            () -> assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY)));
        ownerCall();
    }

    /** Platform 422 只保留本次请求体内存在的字段路径。 */
    @Test
    void test_ownerValidationPathsProjected() throws Exception {
        var row = row(BOUNDARY, "WF15");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"remote-private-detail","code":"VALIDATION_ERROR",
             "requestId":"platform-fields-request","timestamp":"2026-10-22T00:00:00.123456Z","errors":[
              {"field":"title","message":"remote-private-message"},{"field":"privateField","message":"unknown"}]}
            """;
        platform.rawResponse(ownerPath(row), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", "platform-fields-request"));

        var response = send(row);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals("title", body.path("errors").get(0).path("field").asString()),
            () -> assertFalse(response.body().contains("remote-private")));
    }

    /**
     * 完成动作（WF20）与发起实例（WF15）：提交值根下的路径即使请求体里没有该键也保留，登记的 Platform 固定文案透传；
     * 含数据的远端文案换为通用提示，根外不存在的路径与畸形路径仍丢弃。
     */
    @ParameterizedTest(name = "{index} 运行时字段错误 {0}")
    @CsvSource({"WF20,output", "WF15,startValues"})
    void test_runtimeValidationKeepsMissingKeysAndFixedMessages(String id, String root) throws Exception {
        var row = row(BOUNDARY, id);
        var errors = JSON.createArrayNode();
        errors.addObject().put("field", root + ".monthlyVolume").put("message", "必填");
        errors.addObject().put("field", root + ".files[0]").put("message", "文件未通过安全检查，请删除后重新上传");
        errors.addObject().put("field", root + ".remark").put("message", "超过 4000 个字符");
        errors.addObject().put("field", root + ".contract[1]").put("message", "文件不存在：/files/v1/987654321");
        errors.addObject().put("field", root + ".bad key").put("message", "必填");
        errors.addObject().put("field", "privateField").put("message", "必填");
        var upstream = ((ObjectNode) JSON.readTree(problem(422, CommonErrorCode.VALIDATION_ERROR.getCode(),
            "remote-private-detail"))).put("requestId", "platform-fields-request").set("errors", errors);
        platform.rawResponse(ownerPath(row), 422, "application/problem+json", JSON.writeValueAsString(upstream),
            Map.of("X-Request-Id", "platform-fields-request"));

        var response = send(row);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        Map<String, String> fields = new java.util.TreeMap<>();
        body.path("errors").forEach(error -> fields.put(error.path("field").asString(),
            error.path("message").asString()));
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(Map.of(root + ".monthlyVolume", "必填", root + ".files[0]",
                "文件未通过安全检查，请删除后重新上传", root + ".remark", "超过 4000 个字符", root + ".contract[1]", "请检查此处填写"),
                fields),
            () -> assertFalse(response.body().contains("987654321")),
            () -> assertFalse(response.body().contains("remote-private")));
    }

    /** 写回执的对象类型或路径对象 ID 与本次操作不符时按上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var update = row(BOUNDARY, "WF04");
        assertUpstreamInvalid(update, withResponse(update, "objectId", "7300000000000000999"));
        var publish = row(BOUNDARY, "WF10");
        assertUpstreamInvalid(publish, withResponse(publish, "objectType", "WORKFLOW_INSTANCE"));
        var draft = row(BOUNDARY, "WF09");
        assertUpstreamInvalid(draft, withResponse(draft, "definitionId", "7300000000000000999"));
        var submit = row(BOUNDARY, "WF20");
        assertUpstreamInvalid(submit, withResponse(submit, "actionRunId", "7300000000000000999"));
        var retry = row(BOUNDARY, "WF25");
        assertUpstreamInvalid(retry, withResponse(retry, "objectType", "WORKFLOW_INSTANCE"));
        var complete = row(BOUNDARY, "WF31");
        assertUpstreamInvalid(complete, withResponse(complete, "uploadId", "7300000000000000999"));
        var validation = row(BOUNDARY, "WF36");
        assertUpstreamInvalid(validation, withResponse(validation, "problems", null));
        var check = row(BOUNDARY, "WF33");
        assertUpstreamInvalid(check, withResponse(check, "actionRunId", "7300000000000000999"));
    }

    /** Platform 严格 Problem 响应体。 */
    private static String problem(int status, String code, String detail) {
        return JSON.writeValueAsString(JSON.createObjectNode().put("type", "about:blank")
            .put("title", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase()).put("detail", detail)
            .put("code", code).put("requestId", OWNER_REQUEST_ID).put("timestamp", "2026-10-22T00:00:00.123456Z"));
    }

    /** Platform 响应 502 且本次只调用一次 Owner。 */
    private void assertUpstreamInvalid(JsonNode row, JsonNode upstream) throws Exception {
        resetBetweenCalls();
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(upstream));
        var response = send(row);
        assertEquals(502, response.statusCode(), response.body());
        ownerCall();
    }

    /** 本地拒绝：指定状态且 Platform 至多收到授权请求；之后为下一次调用重置记录。 */
    private void assertRejectedBeforePlatform(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals),
            platform.requests().toString());
        resetBetweenCalls();
    }

    /** Platform 只收到一次 PLT-I02 授权。 */
    private void assertOnlyAuthorized() {
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 授权之后恰好一次 Owner 调用，返回该调用。 */
    private RecordedRequest ownerCall() {
        var requests = platform.requests();
        assertEquals(2, requests.size(), requests.toString());
        assertEquals(AUTHORIZE_PATH, requests.getFirst().path());
        return requests.getLast();
    }

    /** 复制样例请求并覆盖一个字段；字符串值写为文本，整数写为数字。 */
    private static ObjectNode with(JsonNode row, String field, Object value) {
        var node = (ObjectNode) row.path("request").deepCopy();
        if (value instanceof Integer number) {
            node.put(field, number);
        } else {
            node.put(field, value.toString());
        }
        return node;
    }

    /** 复制样例响应并覆盖一个文本字段。 */
    private static ObjectNode withResponse(JsonNode row, String field, String value) {
        return ((ObjectNode) row.path("response").deepCopy()).put(field, value);
    }

    /** 去掉值为 null 的属性（缺省与 null 等价）。 */
    private static JsonNode withoutNulls(JsonNode node) {
        if (node.isObject()) {
            var copy = JSON.createObjectNode();
            node.properties().forEach(entry -> {
                if (!entry.getValue().isNull()) {
                    copy.set(entry.getKey(), withoutNulls(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            var copy = JSON.createArrayNode();
            node.forEach(item -> copy.add(withoutNulls(item)));
            return copy;
        }
        return node;
    }
}
