package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.CHANNEL_CONFIG_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.service.processor.ChannelCostVersionComparisonProcessor;
import com.paytech.operations.console.bff.test.base.ChannelBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.ChannelConnectorOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 渠道成本版本对比：经真实 BFF 入口与真实 channel-connector 客户端读取两个版本，在 BFF 内按六维范围与组成代码对齐
 * <li>channel-connector 由 Loopback Simulator 返回配置的版本详情 DTO。</li>
 *
 * @see ChannelCostVersionComparisonProcessor
 * @author Claude
 */
class ChannelAggregationAutoTest extends ChannelBrowserAutoTestBase {
    private static final String PLAN = "NI_COST";
    private static final String ROUTE = "operations-channel-cost-version-diff";
    private static final String DIFF = "/channel-admin/v1/channel-cost-plans/" + PLAN + "/versions/1/diff/2";
    private static final String OWNER_VERSION = "/channel-connector/v1/channel-cost-plans/" + PLAN + "/versions/";
    private static final String MID_A = "7400000000000000301";
    private static final String MID_B = "7400000000000000302";

    /** 范围新增 / 删除 / 修改 / 未变；修改内含组成的新增、删除与字段差异；数值相等（0.10 与 0.1）不算修改。 */
    @Test
    void test_normalFlow() throws Exception {
        var base = version("1", "ACTIVE",
            scope(1, MID_A, "CAPTURE", "AGR-1", component("PROCESSING_FEE", "0.025", "0.30"), component("FX_FEE", "0.01", null)),
            scope(2, MID_A, "REFUND", "AGR-1", component("PROCESSING_FEE", "0.10", null)),
            scope(3, MID_B, "CAPTURE", "AGR-1", component("PROCESSING_FEE", "0.02", null)));
        var target = version("2", "PENDING_APPROVAL",
            scope(1, MID_A, "CAPTURE", "AGR-2", component("PROCESSING_FEE", "0.0250", "0.35"),
                component("SCHEME_FEE", "0.005", null)),
            scope(2, MID_A, "REFUND", "AGR-1", component("PROCESSING_FEE", "0.1", null)),
            scope(3, MID_A, "SALE", "AGR-1", component("PROCESSING_FEE", "0.03", null)));
        replyVersions(base, target);

        var response = send("GET", DIFF, ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        var changed = body.path("changed").get(0);
        assertAll(() -> assertEquals("1", body.path("base").path("costVer").asString()),
            () -> assertEquals("PENDING_APPROVAL", body.path("target").path("status").asString()),
            () -> assertTrue(body.path("base").path("scopes").isMissingNode(), "摘要不含范围"),
            () -> assertEquals("7400000000000000702", body.path("target").path("costVerId").asString()),
            () -> assertEquals(3, body.path("base").path("scopeCount").asInt()),
            () -> assertEquals(4, body.path("base").path("componentCount").asInt()),
            () -> assertEquals(List.of("SALE"), actions(body.path("added"))),
            () -> assertEquals(List.of(MID_B), mids(body.path("removed"))),
            () -> assertEquals(List.of("REFUND"), actions(body.path("unchanged"))),
            () -> assertEquals(1, body.path("changed").size()),
            () -> assertEquals("channelAgreementRef", changed.path("differences").get(0).path("field").asString()),
            () -> assertEquals("AGR-2", changed.path("differences").get(0).path("targetValue").asString()),
            () -> assertEquals("SCHEME_FEE", changed.path("addedComponents").get(0).path("componentCode").asString()),
            () -> assertEquals("FX_FEE", changed.path("removedComponents").get(0).path("componentCode").asString()),
            () -> assertEquals(1, changed.path("changedComponents").size()),
            () -> assertEquals(1, changed.path("changedComponents").get(0).path("differences").size(), "费率按数值相等"),
            () -> assertEquals("fixedAmount",
                changed.path("changedComponents").get(0).path("differences").get(0).path("field").asString()),
            () -> assertEquals(List.of(OWNER_VERSION + "1", OWNER_VERSION + "2"),
                channelConnector.requests().stream().map(request -> request.path()).sorted().toList()));
    }

    /** 同一版本内六维相同的范围按 scopeNo 顺序逐个配对，多出的一个记为新增。 */
    @Test
    void test_edgeCase() throws Exception {
        var base = version("1", "ACTIVE", scope(1, MID_A, "CAPTURE", "AGR-1", component("PROCESSING_FEE", "0.02", null)));
        var target = version("2", "DRAFT", scope(2, MID_A, "CAPTURE", "AGR-9", component("PROCESSING_FEE", "0.02", null)),
            scope(1, MID_A, "CAPTURE", "AGR-1", component("PROCESSING_FEE", "0.02", null)));
        replyVersions(base, target);

        var body = JSON.readTree(send("GET", DIFF, ROUTE, null).body());

        assertAll(() -> assertEquals(1, body.path("unchanged").size()),
            () -> assertEquals(1, body.path("unchanged").get(0).path("scopeNo").asInt()),
            () -> assertEquals(1, body.path("added").size()),
            () -> assertEquals("AGR-9", body.path("added").get(0).path("channelAgreementRef").asString()),
            () -> assertEquals(0, body.path("removed").size()), () -> assertEquals(0, body.path("changed").size()));
    }

    /** 任一版本读取失败整页失败并带 Owner 原因，不返回把缺失误报为删除的对比。 */
    @Test
    void test_noDataFound() throws Exception {
        channelConnector.reply("GET", OWNER_VERSION + "1", 200, JSON.writeValueAsString(version("1", "ACTIVE")));
        channelConnector.problem("GET", OWNER_VERSION + "2", 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        var response = send("GET", DIFF, ROUTE, null);

        assertEquals(400, response.statusCode(), response.body());
        assertEquals(ChannelConnectorOwnerSimulator.OWNER_DETAIL_CANARY, JSON.readTree(response.body()).path("detail").asString());
    }

    /** 两个版本相同或版本号非法时在访问 channel-connector 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        assertRejectedLocally(400, send("GET", "/channel-admin/v1/channel-cost-plans/" + PLAN + "/versions/2/diff/2", ROUTE, null));

        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", "/channel-admin/v1/channel-cost-plans/" + PLAN + "/versions/0/diff/2", ROUTE, null));
    }

    /** 对比只认 CHANNEL_COST_READ：只有渠道配置读权限时拒绝。 */
    @Test
    void test_authorizationDenied() throws Exception {
        platform.grantPermissions(CHANNEL_CONFIG_READ_PERMISSION_ID);

        assertDenied(send("GET", DIFF, ROUTE, null));
    }

    /** 两个版本的 Owner 响应。 */
    private void replyVersions(ObjectNode base, ObjectNode target) {
        channelConnector.reply("GET", OWNER_VERSION + "1", 200, JSON.writeValueAsString(base));
        channelConnector.reply("GET", OWNER_VERSION + "2", 200, JSON.writeValueAsString(target));
    }

    /** 范围的动作类型列表。 */
    private static List<String> actions(JsonNode scopes) {
        var result = new ArrayList<String>();
        scopes.forEach(scope -> result.add(scope.path("actionType").asString()));
        return result;
    }

    /** 范围的 MID 列表。 */
    private static List<String> mids(JsonNode scopes) {
        var result = new ArrayList<String>();
        scopes.forEach(scope -> result.add(scope.path("midId").asString()));
        return result;
    }

    /** 一个版本详情。 */
    private static ObjectNode version(String costVer, String status, ObjectNode... scopes) {
        var version = JSON.createObjectNode().put("costVerId", "740000000000000070" + costVer).put("planCode", PLAN)
            .put("costVer", costVer).put("status", status)
            .put("effectiveFrom", "2026-11-0" + costVer + "T00:00:00Z").putNull("effectiveTo").put("contentHash", "c".repeat(64))
            .put("makerId", "10002").putNull("checkerId");
        ArrayNode array = version.putArray("scopes");
        for (ObjectNode scope : scopes) {
            array.add(scope);
        }
        return version;
    }

    /** 一个范围（国家 AE、币种 USD、支付方式 CARD、OE AE001）。 */
    private static ObjectNode scope(int scopeNo, String midId, String actionType, String agreementRef,
        ObjectNode... components) {
        var scope = JSON.createObjectNode().put("scopeNo", scopeNo).put("midId", midId).put("entityNo", "AE001")
            .put("channelAgreementRef", agreementRef).put("countryCode", "AE").put("currency", "USD")
            .put("paymentMethodCode", "CARD").put("actionType", actionType);
        ArrayNode array = scope.putArray("components");
        for (ObjectNode component : components) {
            array.add(component);
        }
        return scope;
    }

    /** 一个百分比组成。 */
    private static ObjectNode component(String code, String rate, String fixed) {
        return JSON.createObjectNode().put("componentId", "74000000000000009" + code.length()).put("componentCode", code)
            .put("componentType", "PERCENTAGE").put("calculationBase", "TRANSACTION_AMOUNT").put("rateValue", rate)
            .put("fixedAmount", fixed).put("currency", fixed == null ? null : "USD").putNull("minAmount")
            .putNull("maxAmount").put("roundingMode", "HALF_UP");
    }
}
