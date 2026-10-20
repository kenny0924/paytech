package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_CONTRACT_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_PRICING_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_PRICING_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_PRICING_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.CommercialAgreementController;
import com.paytech.operations.console.bff.ext.rest.CommercialPricingController;
import com.paytech.operations.console.bff.test.base.CommercialBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.CommercialOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发 commercial 的合同、画像与条款端点；commercial 由 Loopback Simulator 承担
 * <li>覆盖转发契约、操作人请求头、PLT-I02 拒绝、Maker / Checker 权限分离、Owner 错误透传与本地边界拒绝。</li>
 *
 * @see CommercialAgreementController
 * @see CommercialPricingController
 * @author Claude
 */
class CommercialBrowserAutoTest extends CommercialBrowserAutoTestBase {

    static Stream<JsonNode> endpoints() {
        return rows();
    }

    /** 方法、路径、Query、命令正文与操作人 Header 准确转发，状态码与响应原样返回。 */
    @ParameterizedTest(name = "{index} 商务端点转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        replyAsOwner(row);

        assertForwarded(row, send(row));
    }

    /** 当前会话授权失败时不访问 commercial。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        assertDenied(send(row));
    }

    /** 定价 MANAGE 可以提交与排期但不能审批；APPROVE 可以审批但不能提交（Maker 与 Checker 分属不同权限）。 */
    @Test
    void test_makerCheckerPermissionsAreSeparated() throws Exception {
        platform.grantPermissions(COMMERCIAL_PRICING_MANAGE_PERMISSION_ID);
        assertDenied(send(row("T05")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_MANAGE_PERMISSION_ID);
        assertDenied(send(row("T06")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_MANAGE_PERMISSION_ID);
        var submit = row("T04");
        replyAsOwner(submit);
        assertForwarded(submit, send(submit));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_APPROVE_PERMISSION_ID);
        assertDenied(send(row("T04")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_APPROVE_PERMISSION_ID);
        assertDenied(send(row("T07")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_APPROVE_PERMISSION_ID);
        var approve = row("T05");
        replyAsOwner(approve);
        assertForwarded(approve, send(approve));
    }

    /** 只读权限只能查询：合同只读不能创建合同，定价只读不能维护画像，两类读权限互不替代。 */
    @Test
    void test_readOnlyOperatorCannotManage() throws Exception {
        platform.grantPermissions(COMMERCIAL_CONTRACT_READ_PERMISSION_ID);
        assertDenied(send(row("C02")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_CONTRACT_READ_PERMISSION_ID);
        assertDenied(send(row("P01")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_READ_PERMISSION_ID);
        assertDenied(send(row("P02")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_READ_PERMISSION_ID);
        var list = row("P01");
        replyAsOwner(list);
        assertForwarded(list, send(list));
    }

    /** commercial 的公共业务拒绝保留公共 Code，Owner 的拒绝原因经 Foundation Owner detail 开关到达浏览器（F9）；Owner Request ID 不外露。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        var schedule = row("T07");
        commercial.problem("POST", schedule.path("ownerPath").asString(), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        var response = send(schedule);

        assertEquals(400, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), body.path("code").asString()),
            () -> assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(CommercialOwnerSimulator.OWNER_REQUEST_ID)));
    }

    /** 查询类拒绝同样透传：Owner 404 的 detail 与公共 NOT_FOUND 一起到达浏览器。 */
    @Test
    void test_noDataFound() throws Exception {
        var detail = row("T10");
        commercial.problem("GET", detail.path("ownerPath").asString(), 404, CommonErrorCode.NOT_FOUND.getCode());

        var response = send(detail);

        assertEquals(404, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.NOT_FOUND.getCode(), body.path("code").asString()),
            () -> assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(CommercialOwnerSimulator.OWNER_REQUEST_ID)));
    }

    /** BCR 版本冲突以 409 到达浏览器并带 Owner 原因，页面据此提示重新加载。 */
    @Test
    void test_versionConflict() throws Exception {
        var activate = row("C05");
        commercial.problem("POST", activate.path("ownerPath").asString(), 409,
            CommonErrorCode.CONCURRENT_REQUEST.getCode());

        var response = send(activate);

        assertEquals(409, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.CONCURRENT_REQUEST.getCode(), body.path("code").asString()),
            () -> assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(CommercialOwnerSimulator.OWNER_REQUEST_ID)));
    }

    /** 超过 512 字符或含控制字符的 Owner detail 不透传，保留公共说明；512 字符以内原样到达。 */
    @Test
    void test_edgeCase() throws Exception {
        var schedule = row("T07");
        String boundary = "审".repeat(512);
        assertEquals(boundary, rejectDetail(schedule, boundary).path("detail").asString());
        for (String unsafe : List.of("审".repeat(513), "拒绝\\n原因")) {
            resetBetweenCalls();
            var body = rejectDetail(schedule, unsafe);
            assertAll(() -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getMessage(), body.path("detail").asString()),
                () -> assertEquals(REQUEST_ID, body.path("requestId").asString()));
        }
    }

    /** Owner 以 400 BUSINESS_CHECK_FAILED 拒绝且 detail 为给定 JSON 文本，返回浏览器 Problem。 */
    private JsonNode rejectDetail(JsonNode route, String jsonDetail)
        throws Exception {
        String upstream = """
            {"type":"about:blank","title":"Bad Request","detail":"%s","code":"%s","requestId":"%s",
             "timestamp":"2026-10-19T06:00:00.000000Z"}
            """.formatted(jsonDetail, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            CommercialOwnerSimulator.OWNER_REQUEST_ID);
        commercial.reply("POST", route.path("ownerPath").asString(), 400, "application/problem+json", upstream,
            Map.of("X-Request-Id", CommercialOwnerSimulator.OWNER_REQUEST_ID));
        var response = send(route);
        assertEquals(400, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    /** commercial 422 只保留本次请求体内存在的字段路径。 */
    @Test
    void test_validationPathsProjected() throws Exception {
        var prices = row("T03");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"%s","code":"VALIDATION_ERROR",
             "requestId":"%s","timestamp":"2026-10-19T06:00:00.000000Z","errors":[
              {"field":"prices[0].components[1].bands[0].rate","message":"x"},{"field":"operatorAccountId","message":"y"}]}
            """.formatted(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, CommercialOwnerSimulator.OWNER_REQUEST_ID);
        commercial.reply("PUT", prices.path("ownerPath").asString(), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", CommercialOwnerSimulator.OWNER_REQUEST_ID));

        var response = send(prices);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals("prices[0].components[1].bands[0].rate", body.path("errors").get(0).path("field").asString()),
            () -> assertFalse(response.body().contains(CommercialOwnerSimulator.OWNER_DETAIL_CANARY)));
    }

    /** commercial 不可用映射 503，响应回执与请求身份不一致映射 502。 */
    @Test
    void test_dependencyFailures() throws Exception {
        var submit = row("C03");
        commercial.problem("POST", submit.path("ownerPath").asString(), 503, "OWNER_UNAVAILABLE");
        var unavailable = send(submit);
        assertEquals(503, unavailable.statusCode(), unavailable.body());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode(),
            JSON.readTree(unavailable.body()).path("code").asString());

        resetBetweenCalls();
        var other = (ObjectNode) submit.path("response").deepCopy();
        other.put("agreementId", "7319000000000000999");
        commercial.reply("POST", submit.path("ownerPath").asString(), 200, JSON.writeValueAsString(other));
        var mismatched = send(submit);
        assertEquals(502, mismatched.statusCode(), mismatched.body());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(),
            JSON.readTree(mismatched.body()).path("code").asString());
    }

    /** 未知 Query、未知 Body 字段、非法路径 ID、查询带 Body 与缺少必填字段均在访问 commercial 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row("P01");
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?kind=PAYMENT_METHOD&unknown=1",
            list.path("routeCode").asString(), null));

        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?kind=CARD",
            list.path("routeCode").asString(), null));

        resetBetweenCalls();
        var submit = row("T04");
        var withOperator = (ObjectNode) submit.path("request").deepCopy();
        withOperator.put("operatorAccountId", "10002");
        assertRejectedLocally(400, send("POST", submit.path("path").asString(), submit.path("routeCode").asString(),
            withOperator));

        resetBetweenCalls();
        assertRejectedLocally(400, send("POST", "/commercial-admin/v1/commercial-terms/versions/0abc/submit",
            submit.path("routeCode").asString(), submit.path("request")));

        resetBetweenCalls();
        var create = row("C02");
        var missingParties = (ObjectNode) create.path("request").deepCopy();
        missingParties.remove("parties");
        var response = send("POST", create.path("path").asString(), create.path("routeCode").asString(), missingParties);
        assertRejectedLocally(422, response);
        assertEquals("parties", JSON.readTree(response.body()).path("errors").get(0).path("field").asString());

        resetBetweenCalls();
        var detail = "/commercial-admin/v1/agreements/7319000000000000101";
        assertRejectedLocally(400, send("GET", detail, "operations-commercial-agreement-detail",
            JSON.createObjectNode().put("agreementId", "1")));
    }
}
