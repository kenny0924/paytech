package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_ACCOUNT_CREDENTIAL_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_CALL_GRANT_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_CHANGE_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_CONFIG_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_CONFIG_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_COST_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_COST_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_COST_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.ChannelConfigurationController;
import com.paytech.operations.console.bff.ext.rest.ChannelCostController;
import com.paytech.operations.console.bff.test.base.ChannelBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.ChannelConnectorOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发 channel-connector 的渠道（含出站地址）、账号、MID、能力、变更记录、调用授权与成本端点
 * （块 92 的 30 条与块 129 的 4 条转发路由）
 * <li>channel-connector 由 Loopback Simulator 承担；覆盖转发契约、渠道类型与出站地址字段透传、操作人请求头与成本 Body 操作人、PLT-I02 拒绝、
 * 凭据、调用授权与 Maker / Checker 权限分离、Owner 业务拒绝 detail 透传、409、422 字段投影、依赖失败与本地边界拒绝。</li>
 *
 * @see ChannelConfigurationController
 * @see ChannelCostController
 * @author Claude
 */
@ExtendWith(OutputCaptureExtension.class)
class ChannelBrowserAutoTest extends ChannelBrowserAutoTestBase {

    static Stream<JsonNode> endpoints() {
        return rows();
    }

    /** 方法、路径、Query、Body 与操作人准确转发，状态码与响应原样返回。 */
    @ParameterizedTest(name = "{index} 渠道端点转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        replyAsOwner(row);

        assertForwarded(row, send(row));
    }

    /** 当前会话授权失败时不访问 channel-connector。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        assertDenied(send(row));
    }

    /** 成本 MANAGE 可以编制、提交、作废但不能审批；APPROVE 可以批准与驳回但不能提交（Maker 与 Checker 分属不同权限）。 */
    @Test
    void test_makerCheckerPermissionsAreSeparated() throws Exception {
        for (String id : new String[] {"P07", "P08"}) {
            resetBetweenCalls();
            platform.grantPermissions(CHANNEL_COST_MANAGE_PERMISSION_ID);
            assertDenied(send(row(id)));
        }
        for (String id : new String[] {"P05", "P06", "P09"}) {
            resetBetweenCalls();
            platform.grantPermissions(CHANNEL_COST_MANAGE_PERMISSION_ID);
            assertAllowed(row(id));
        }
        for (String id : new String[] {"P05", "P06", "P09"}) {
            resetBetweenCalls();
            platform.grantPermissions(CHANNEL_COST_APPROVE_PERMISSION_ID);
            assertDenied(send(row(id)));
        }
        for (String id : new String[] {"P07", "P08"}) {
            resetBetweenCalls();
            platform.grantPermissions(CHANNEL_COST_APPROVE_PERMISSION_ID);
            assertAllowed(row(id));
        }
    }

    /** 账号参数与凭据只认 CHANNEL_ACCOUNT_CREDENTIAL：维护权限不能写凭据，凭据权限不能做其他维护。 */
    @Test
    void test_credentialPermissionIsSeparated() throws Exception {
        platform.grantPermissions(CHANNEL_CONFIG_MANAGE_PERMISSION_ID);
        assertDenied(send(row("A05")));

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_ACCOUNT_CREDENTIAL_PERMISSION_ID);
        assertDenied(send(row("A03")));

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_ACCOUNT_CREDENTIAL_PERMISSION_ID);
        assertAllowed(row("A05"));
    }

    /** 只读权限只能查询；渠道配置读、成本读与变更记录读互不替代。 */
    @Test
    void test_readOnlyOperatorCannotManage() throws Exception {
        platform.grantPermissions(CHANNEL_CONFIG_READ_PERMISSION_ID);
        assertDenied(send(row("H03")));

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_CONFIG_READ_PERMISSION_ID);
        assertDenied(send(row("G01")));

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_CONFIG_READ_PERMISSION_ID);
        assertDenied(send(row("P01")));

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_COST_READ_PERMISSION_ID);
        assertDenied(send(row("P04")));

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_CHANGE_READ_PERMISSION_ID);
        assertAllowed(row("G01"));

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_CONFIG_READ_PERMISSION_ID);
        assertAllowed(row("M01"));
    }

    /** 渠道激活被拒（渠道未配置出站地址）：公共 Code 与 Owner 原因到达浏览器，Owner Request ID 不外露。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        var activate = row("H05");
        channelConnector.problem("POST", activate.path("ownerPath").asString(), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        assertOwnerDetail(400, CommonErrorCode.BUSINESS_CHECK_FAILED, send(activate));
    }

    /** 成本 Checker 与 Maker 相同被 Owner 拒绝时，原因同样到达浏览器。 */
    @Test
    void test_sameMakerCheckerRejectedByOwner() throws Exception {
        var approve = row("P07");
        channelConnector.problem("POST", approve.path("ownerPath").asString(), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        assertOwnerDetail(400, CommonErrorCode.BUSINESS_CHECK_FAILED, send(approve));
    }

    /** 查询对象不存在：Owner 的 400 原因到达浏览器。 */
    @Test
    void test_noDataFound() throws Exception {
        var detail = row("M02");
        channelConnector.problem("GET", detail.path("ownerPath").asString(), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        assertOwnerDetail(400, CommonErrorCode.BUSINESS_CHECK_FAILED, send(detail));
    }

    /** 并发或唯一冲突以 409 到达浏览器并带 Owner 原因，页面据此提示重新加载。 */
    @Test
    void test_versionConflict() throws Exception {
        var create = row("M03");
        channelConnector.problem("POST", create.path("ownerPath").asString(), 409,
            CommonErrorCode.CONCURRENT_REQUEST.getCode());

        assertOwnerDetail(409, CommonErrorCode.CONCURRENT_REQUEST, send(create));
    }

    /** channel-connector 422 只保留本次页面 Body 内存在的字段路径；BFF 补入的 makerId 不外显。 */
    @Test
    void test_validationPathsProjected() throws Exception {
        var create = row("P05");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"%s","code":"VALIDATION_ERROR",
             "requestId":"%s","timestamp":"2026-10-20T06:00:00.000000Z","errors":[
              {"field":"scopes[0].components[0].rateValue","message":"x"},{"field":"makerId","message":"y"}]}
            """.formatted(ChannelConnectorOwnerSimulator.OWNER_DETAIL_CANARY, ChannelConnectorOwnerSimulator.OWNER_REQUEST_ID);
        channelConnector.reply("POST", create.path("ownerPath").asString(), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", ChannelConnectorOwnerSimulator.OWNER_REQUEST_ID));

        var response = send(create);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals("scopes[0].components[0].rateValue", body.path("errors").get(0).path("field").asString()),
            () -> assertFalse(response.body().contains(ChannelConnectorOwnerSimulator.OWNER_DETAIL_CANARY)));
    }

    /** channel-connector 不可用映射 503；状态变更回执状态不等于目标状态、详情回执身份不一致映射 502。 */
    @Test
    void test_dependencyFailures() throws Exception {
        var status = row("A06");
        channelConnector.problem("POST", status.path("ownerPath").asString(), 503, "OWNER_UNAVAILABLE");
        var unavailable = send(status);
        assertEquals(503, unavailable.statusCode(), unavailable.body());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode(),
            JSON.readTree(unavailable.body()).path("code").asString());

        resetBetweenCalls();
        var other = (ObjectNode) status.path("response").deepCopy();
        other.put("status", "ACTIVE");
        channelConnector.reply("POST", status.path("ownerPath").asString(), 200, JSON.writeValueAsString(other));
        assertUpstreamInvalid(send(status));

        resetBetweenCalls();
        var detail = row("H02");
        var otherChannel = (ObjectNode) detail.path("response").deepCopy();
        otherChannel.put("channelId", "7400000000000000999");
        channelConnector.reply("GET", detail.path("ownerPath").asString(), 200, JSON.writeValueAsString(otherChannel));
        assertUpstreamInvalid(send(detail));
    }

    /** 未知 Query、非法筛选值、页面自报操作人、无 Body 命令带 Body、非法路径、缺少必填字段与查询带 Body 均在访问 channel-connector 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row("H01");
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?status=ACTIVE&unknown=1",
            list.path("routeCode").asString(), null));

        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?status=ENABLED",
            list.path("routeCode").asString(), null));

        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?size=15",
            list.path("routeCode").asString(), null));

        resetBetweenCalls();
        var mids = row("M01");
        assertRejectedLocally(400, send("GET", mids.path("path").asString() + "?externalMidCode=" + "M".repeat(129),
            mids.path("routeCode").asString(), null));

        resetBetweenCalls();
        var approve = row("P07");
        var withChecker = (ObjectNode) approve.path("request").deepCopy();
        withChecker.put("checkerId", "10002");
        assertRejectedLocally(400, send("POST", approve.path("path").asString(), approve.path("routeCode").asString(),
            withChecker));

        resetBetweenCalls();
        var submit = row("P06");
        assertRejectedLocally(400, send("POST", submit.path("path").asString(), submit.path("routeCode").asString(),
            JSON.createObjectNode().put("makerId", "10002")));

        resetBetweenCalls();
        assertRejectedLocally(400, send("POST", "/channel-admin/v1/channels/0abc/status",
            row("H05").path("routeCode").asString(), row("H05").path("request")));

        resetBetweenCalls();
        var create = row("A03");
        var missingName = (ObjectNode) create.path("request").deepCopy();
        missingName.remove("accountName");
        var response = send("POST", create.path("path").asString(), create.path("routeCode").asString(), missingName);
        assertRejectedLocally(422, response);
        assertEquals("accountName", JSON.readTree(response.body()).path("errors").get(0).path("field").asString());

        resetBetweenCalls();
        var changes = row("G01");
        assertRejectedLocally(400, send("GET", changes.path("path").asString() + "?targetType=CHANNEL",
            changes.path("routeCode").asString(), null));

        resetBetweenCalls();
        var detail = row("H02");
        assertRejectedLocally(400, send("GET", detail.path("path").asString(), detail.path("routeCode").asString(),
            JSON.createObjectNode().put("channelId", "1")));
    }

    /** 渠道类型、出站地址与调用授权的本地边界：非法类型、缺少类型、空地址集合、非法调用方与未知 Query 均不访问 channel-connector。 */
    @Test
    void test_channelTypeEndpointAndGrantBoundaries() throws Exception {
        var list = row("H01");
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?channelType=OTHER",
            list.path("routeCode").asString(), null));

        resetBetweenCalls();
        var create = row("H03");
        var missingType = (ObjectNode) create.path("request").deepCopy();
        missingType.remove("channelType");
        var typeResponse = send("POST", create.path("path").asString(), create.path("routeCode").asString(), missingType);
        assertRejectedLocally(422, typeResponse);
        assertEquals("channelType", JSON.readTree(typeResponse.body()).path("errors").get(0).path("field").asString());

        resetBetweenCalls();
        var endpoints = row("H06");
        var empty = (ObjectNode) endpoints.path("request").deepCopy();
        empty.putArray("endpoints");
        assertRejectedLocally(422, send("PUT", endpoints.path("path").asString(), endpoints.path("routeCode").asString(),
            empty));

        resetBetweenCalls();
        var noVersion = (ObjectNode) endpoints.path("request").deepCopy();
        noVersion.remove("expectedVersion");
        assertRejectedLocally(422, send("PUT", endpoints.path("path").asString(), endpoints.path("routeCode").asString(),
            noVersion));

        resetBetweenCalls();
        var grant = row("R02");
        var badCaller = (ObjectNode) grant.path("request").deepCopy();
        badCaller.put("callerService", "Commerce_Service");
        var callerResponse = send("POST", grant.path("path").asString(), grant.path("routeCode").asString(), badCaller);
        assertRejectedLocally(422, callerResponse);
        assertEquals("callerService", JSON.readTree(callerResponse.body()).path("errors").get(0).path("field").asString());

        resetBetweenCalls();
        var grants = row("R01");
        assertRejectedLocally(400, send("GET", grants.path("path").asString() + "?grantStatus=ENABLED",
            grants.path("routeCode").asString(), null));

        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", grants.path("path").asString() + "?channelType=SERVICE",
            grants.path("routeCode").asString(), null));

        resetBetweenCalls();
        var status = row("R03");
        var withActor = (ObjectNode) status.path("request").deepCopy();
        withActor.put("updatedBy", "10002");
        assertRejectedLocally(400, send("POST", status.path("path").asString(), status.path("routeCode").asString(),
            withActor));
    }

    /** 服务渠道列表项：类型筛选透传，ACTIVE 授权数与无 MID 原样返回。 */
    @Test
    void test_serviceChannelSummaryForwarded() throws Exception {
        var service = (ObjectNode) row("H01").deepCopy();
        service.put("query", "?channelType=SERVICE&page=1&size=10");
        var item = (ObjectNode) service.path("response").path("items").get(0);
        item.put("channelId", "7400000000000000102").put("channelCode", "GEO_MAP").put("channelName", "地图服务")
            .put("channelType", "SERVICE").put("accountCount", 1).put("midCount", 0).put("endpointCount", 1)
            .put("activeGrantCount", 2);
        replyAsOwner(service);

        assertForwarded(service, send(service));
    }

    /** 调用授权的新建与启停只认 CHANNEL_CALL_GRANT_MANAGE；授权列表沿用渠道读取，出站地址沿用渠道维护。 */
    @Test
    void test_callGrantPermissionIsSeparated() throws Exception {
        for (String id : new String[] {"R02", "R03"}) {
            resetBetweenCalls();
            platform.grantPermissions(CHANNEL_CONFIG_MANAGE_PERMISSION_ID);
            assertDenied(send(row(id)));
        }
        for (String id : new String[] {"R02", "R03"}) {
            resetBetweenCalls();
            platform.grantPermissions(CHANNEL_CALL_GRANT_MANAGE_PERMISSION_ID);
            assertAllowed(row(id));
        }
        for (String id : new String[] {"R01", "H06"}) {
            resetBetweenCalls();
            platform.grantPermissions(CHANNEL_CALL_GRANT_MANAGE_PERMISSION_ID);
            assertDenied(send(row(id)));
        }

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_CONFIG_READ_PERMISSION_ID);
        assertAllowed(row("R01"));

        resetBetweenCalls();
        platform.grantPermissions(CHANNEL_CONFIG_MANAGE_PERMISSION_ID);
        assertAllowed(row("H06"));
    }

    /** 出站地址版本冲突或仍被原子 API 引用（409）、私网地址（400），以及授权组合重复（409）：Owner 原因到达浏览器。 */
    @Test
    void test_endpointAndGrantRejectionsVisible() throws Exception {
        var endpoints = row("H06");
        channelConnector.problem("PUT", endpoints.path("ownerPath").asString(), 409,
            CommonErrorCode.CONCURRENT_REQUEST.getCode());
        assertOwnerDetail(409, CommonErrorCode.CONCURRENT_REQUEST, send(endpoints));

        resetBetweenCalls();
        channelConnector.problem("PUT", endpoints.path("ownerPath").asString(), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        assertOwnerDetail(400, CommonErrorCode.BUSINESS_CHECK_FAILED, send(endpoints));

        resetBetweenCalls();
        var grant = row("R02");
        channelConnector.problem("POST", grant.path("ownerPath").asString(), 409,
            CommonErrorCode.CONCURRENT_REQUEST.getCode());
        assertOwnerDetail(409, CommonErrorCode.CONCURRENT_REQUEST, send(grant));

        resetBetweenCalls();
        channelConnector.problem("POST", grant.path("ownerPath").asString(), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        assertOwnerDetail(400, CommonErrorCode.BUSINESS_CHECK_FAILED, send(grant));
    }

    /** 回执不符合合同（502）：授权状态不等于目标状态、授权列表越出筛选、渠道列表类型越出筛选、渠道详情缺出站地址集合、地址数量不一致。 */
    @Test
    void test_grantAndEndpointReceiptMismatch() throws Exception {
        var status = row("R03");
        var notChanged = (ObjectNode) status.path("response").deepCopy();
        notChanged.put("grantStatus", "ACTIVE");
        channelConnector.reply("POST", status.path("ownerPath").asString(), 200, JSON.writeValueAsString(notChanged));
        assertUpstreamInvalid(send(status));

        resetBetweenCalls();
        var grants = row("R01");
        var otherChannel = (ObjectNode) grants.path("response").deepCopy();
        ((ObjectNode) otherChannel.path("items").get(0)).put("channelId", "7400000000000000999");
        channelConnector.reply("GET", grants.path("ownerPath").asString(), 200, JSON.writeValueAsString(otherChannel));
        assertUpstreamInvalid(send(grants));

        resetBetweenCalls();
        var channels = row("H01");
        var otherType = (ObjectNode) channels.path("response").deepCopy();
        ((ObjectNode) otherType.path("items").get(0)).put("channelType", "SERVICE");
        channelConnector.reply("GET", channels.path("ownerPath").asString(), 200, JSON.writeValueAsString(otherType));
        assertUpstreamInvalid(send(channels));

        resetBetweenCalls();
        var detail = row("H02");
        var noEndpoints = (ObjectNode) detail.path("response").deepCopy();
        noEndpoints.putNull("endpoints");
        channelConnector.reply("GET", detail.path("ownerPath").asString(), 200, JSON.writeValueAsString(noEndpoints));
        assertUpstreamInvalid(send(detail));

        resetBetweenCalls();
        var endpoints = row("H06");
        var fewer = (ObjectNode) endpoints.path("response").deepCopy();
        ((ArrayNode) fewer.path("endpoints")).remove(1);
        channelConnector.reply("PUT", endpoints.path("ownerPath").asString(), 200, JSON.writeValueAsString(fewer));
        assertUpstreamInvalid(send(endpoints));
    }

    /** 账号配置的凭据明文只经 BFF 转发：请求日志只记元数据，凭据值与参数值不出现在任何日志。 */
    @Test
    void test_credentialsAreNeverLogged(CapturedOutput output) throws Exception {
        var configure = row("A05");
        replyAsOwner(configure);

        assertForwarded(configure, send(configure));

        assertAll(() -> assertFalse(output.getAll().contains("canary-credential-value")),
            () -> assertFalse(output.getAll().contains("NI-MERCHANT-01")),
            () -> assertTrue(output.getAll().contains("uri=\"/channel-admin/v1/channel-accounts/**\"")));
    }

    /** 当前授权下成功转发。 */
    private void assertAllowed(JsonNode row) throws Exception {
        replyAsOwner(row);
        assertForwarded(row, send(row));
    }

    /** Owner 拒绝：指定状态与公共 Code，detail 为 Owner 原因，Request ID 为 BFF 自己的。 */
    private void assertOwnerDetail(int status, CommonErrorCode code, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code.getCode(), body.path("code").asString()),
            () -> assertEquals(ChannelConnectorOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(ChannelConnectorOwnerSimulator.OWNER_REQUEST_ID)));
    }

    /** 上游回执不符合合同：502 UPSTREAM_INVALID_RESPONSE。 */
    private static void assertUpstreamInvalid(HttpResponse<String> response) {
        assertEquals(502, response.statusCode(), response.body());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(),
            JSON.readTree(response.body()).path("code").asString());
    }
}
