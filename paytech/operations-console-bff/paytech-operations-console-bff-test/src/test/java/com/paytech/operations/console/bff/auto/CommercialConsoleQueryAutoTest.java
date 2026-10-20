package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_CONTRACT_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_PRICING_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.CommercialAgreementStatus;
import com.paytech.operations.console.bff.domain.enums.CommercialDashboardMetric;
import com.paytech.operations.console.bff.domain.enums.CommercialTermVersionStatus;
import com.paytech.operations.console.bff.ext.rest.CommercialAgreementController;
import com.paytech.operations.console.bff.ext.rest.CommercialCatalogController;
import com.paytech.operations.console.bff.ext.rest.CommercialPricingController;
import com.paytech.operations.console.bff.service.processor.CommercialDashboardProcessor;
import com.paytech.operations.console.bff.test.base.CommercialBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.CommercialOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 第二切片 I2 的只读列表、产品目录与工作台：经真实 BFF 入口与真实 commercial 客户端执行，commercial 由 Loopback Simulator 返回配置的 DTO
 * <li>原样转发（C10 / T11 / T12 / K01–K03）与授权拒绝由 CommercialBrowserAutoTest 的边界清单覆盖；这里覆盖空数据、参数错误、
 * 多值状态、Owner 4xx 透传、分页回执漂移与工作台聚合。</li>
 *
 * @see CommercialAgreementController
 * @see CommercialPricingController
 * @see CommercialCatalogController
 * @see CommercialDashboardProcessor
 * @author Claude
 */
class CommercialConsoleQueryAutoTest extends CommercialBrowserAutoTestBase {
    private static final String AGREEMENTS = "/commercial-admin/v1/agreements";
    private static final String PLANS = "/commercial-admin/v1/commercial-terms/plans";
    private static final String VERSIONS = "/commercial-admin/v1/commercial-terms/versions";
    private static final String PRODUCTS = "/commercial-admin/v1/products";
    private static final String CAPABILITIES = "/commercial-admin/v1/products/capabilities";
    private static final String FEE_EVENTS = "/commercial-admin/v1/products/fee-events";
    private static final String DASHBOARD = "/commercial-admin/v1/dashboard";
    private static final String AGREEMENT_ROUTE = "operations-commercial-agreement-list";
    private static final String PLAN_ROUTE = "operations-commercial-term-plan-list";
    private static final String VERSION_ROUTE = "operations-commercial-term-version-list";
    private static final String PRODUCT_ROUTE = "operations-commercial-product-list";
    private static final String CAPABILITY_ROUTE = "operations-commercial-capability-catalog";
    private static final String FEE_EVENT_ROUTE = "operations-commercial-fee-event-list";
    private static final String DASHBOARD_ROUTE = "operations-commercial-dashboard";
    private static final String OWNER_AGREEMENTS = "/agreements/v1";
    private static final String OWNER_PLANS = "/commercial-terms/v1/plans";
    private static final String OWNER_VERSIONS = "/commercial-terms/v1/versions";

    /** 工作台：五个状态计数并发读取，卡片按声明顺序取 total；待审批第一页即审批队列。 */
    @Test
    void test_normalFlow_dashboard() throws Exception {
        var queue = row("T12").path("response").path("items");
        commercial.reply("GET", OWNER_VERSIONS + "?" + firstPage(CommercialTermVersionStatus.PENDING_APPROVAL.getCode()), 200,
            JSON.writeValueAsString(page(queue, 1, 10, 3)));
        commercial.reply("GET", OWNER_VERSIONS + "?" + firstPage(CommercialTermVersionStatus.SCHEDULED.getCode()), 200,
            JSON.writeValueAsString(emptyPage(1, 10, 2)));
        replyAgreementCount(CommercialAgreementStatus.DRAFT, 4);
        replyAgreementCount(CommercialAgreementStatus.PENDING_SIGNATURE, 1);
        replyAgreementCount(CommercialAgreementStatus.ACTIVE, 12);

        var response = send("GET", DASHBOARD, DASHBOARD_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        var metrics = body.path("metrics");
        assertAll(() -> assertEquals(Arrays.stream(CommercialDashboardMetric.values()).map(CommercialDashboardMetric::getCode).toList(),
                metrics.valueStream().map(metric -> metric.path("metricCode").asString()).toList()),
            () -> assertEquals(List.of(3L, 2L, 4L, 1L, 12L), metrics.valueStream().map(metric -> metric.path("count").asLong()).toList()),
            () -> assertEquals(queue, body.path("approvalQueue")),
            () -> assertTrue(body.path("asOf").asString().endsWith("Z")),
            () -> assertEquals(5, commercial.requests().size(), commercial.requests().toString()));
    }

    /** 空数据：列表空页原样返回；工作台全部为 0、队列为空；目录空集合原样返回。 */
    @Test
    void test_noDataFound() throws Exception {
        commercial.reply("GET", OWNER_AGREEMENTS, 200, JSON.writeValueAsString(emptyPage(1, 10, 0)));
        var agreements = send("GET", AGREEMENTS + "?clientId=7300000000000000099", AGREEMENT_ROUTE, null);
        assertEquals(200, agreements.statusCode(), agreements.body());
        var emptyAgreements = JSON.readTree(agreements.body());
        assertAll(() -> assertEquals(0, emptyAgreements.path("items").size()), () -> assertEquals(0, emptyAgreements.path("total").asLong()),
            () -> assertEquals(1, emptyAgreements.path("page").asInt()), () -> assertEquals(10, emptyAgreements.path("size").asInt()));

        resetBetweenCalls();
        commercial.reply("GET", OWNER_VERSIONS, 200, JSON.writeValueAsString(emptyPage(1, 10, 0)));
        commercial.reply("GET", OWNER_AGREEMENTS, 200, JSON.writeValueAsString(emptyPage(1, 10, 0)));
        var dashboard = JSON.readTree(send("GET", DASHBOARD, DASHBOARD_ROUTE, null).body());
        assertAll(() -> assertTrue(dashboard.path("metrics").valueStream().allMatch(metric -> metric.path("count").asLong() == 0)),
            () -> assertEquals(0, dashboard.path("approvalQueue").size()));

        resetBetweenCalls();
        commercial.reply("GET", "/products/v1/fee-events", 200, "{\"feeEvents\":[]}");
        var feeEvents = send("GET", FEE_EVENTS + "?termType=PLATFORM_FEE", FEE_EVENT_ROUTE, null);
        assertEquals(200, feeEvents.statusCode(), feeEvents.body());
        assertEquals(0, JSON.readTree(feeEvents.body()).path("feeEvents").size());
    }

    /** 合同与版本的 status 可多选：重复参数按原顺序转发给 commercial。 */
    @Test
    void test_edgeCase_multipleStatusesForwarded() throws Exception {
        commercial.reply("GET", OWNER_AGREEMENTS, 200, JSON.writeValueAsString(emptyPage(1, 10, 0)));

        var response = send("GET", AGREEMENTS + "?status=" + CommercialAgreementStatus.DRAFT.getCode() + "&status="
            + CommercialAgreementStatus.PENDING_SIGNATURE.getCode(), AGREEMENT_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("status=DRAFT&status=PENDING_SIGNATURE&page=1&size=10", commercial.requests().getFirst().query());

        resetBetweenCalls();
        commercial.reply("GET", OWNER_VERSIONS, 200, JSON.writeValueAsString(emptyPage(2, 20, 21)));
        var versions = send("GET", VERSIONS + "?status=" + CommercialTermVersionStatus.SCHEDULED.getCode() + "&status="
            + CommercialTermVersionStatus.ACTIVE.getCode() + "&page=2&size=20", VERSION_ROUTE, null);
        assertEquals(200, versions.statusCode(), versions.body());
        assertEquals("status=SCHEDULED&status=ACTIVE&page=2&size=20", commercial.requests().getFirst().query());
    }

    /** 未知或重复键、非法分页、未知代码与非法时间在访问 commercial 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        List<String[]> rejected = List.of(
            new String[] {AGREEMENTS + "?size=15", AGREEMENT_ROUTE},
            new String[] {AGREEMENTS + "?page=0", AGREEMENT_ROUTE},
            new String[] {AGREEMENTS + "?status=UNKNOWN", AGREEMENT_ROUTE},
            new String[] {AGREEMENTS + "?clientId=1&clientId=2", AGREEMENT_ROUTE},
            new String[] {AGREEMENTS + "?clientId=0012", AGREEMENT_ROUTE},
            new String[] {AGREEMENTS + "?debug=1", AGREEMENT_ROUTE},
            new String[] {AGREEMENTS + "?agreementNo=", AGREEMENT_ROUTE},
            new String[] {AGREEMENTS + "?" + "status=DRAFT&".repeat(8), AGREEMENT_ROUTE},
            new String[] {PLANS + "?status=PENDING_APPROVAL", PLAN_ROUTE},
            new String[] {PLANS + "?status=ACTIVE&status=DRAFT", PLAN_ROUTE},
            new String[] {VERSIONS + "?effectiveFrom=2026-10-01", VERSION_ROUTE},
            new String[] {VERSIONS + "?planId=abc", VERSION_ROUTE},
            new String[] {PRODUCTS + "?status=ENABLED", PRODUCT_ROUTE},
            new String[] {CAPABILITIES + "?productCode=CARD", CAPABILITY_ROUTE},
            new String[] {FEE_EVENTS + "?termType=client_pricing", FEE_EVENT_ROUTE},
            new String[] {DASHBOARD + "?page=1", DASHBOARD_ROUTE});
        for (String[] request : rejected) {
            var response = send("GET", request[0], request[1], null);
            assertRejectedLocally(400, response);
            resetBetweenCalls();
        }
    }

    /** commercial 的 400 业务拒绝 / 404 保留公共 Code，并经 Foundation Owner detail 开关把 Owner 原因透传到每个新列表与目录端点。 */
    @Test
    void test_businessRuleViolation_ownerRejectionPassedThrough() throws Exception {
        List<String[]> endpoints = List.of(
            new String[] {AGREEMENTS + "?entityNo=AE009", AGREEMENT_ROUTE, OWNER_AGREEMENTS},
            new String[] {PLANS + "?termType=UNKNOWN_TERM", PLAN_ROUTE, OWNER_PLANS},
            new String[] {VERSIONS + "?effectiveFrom=2026-11-01T00:00:00Z&effectiveTo=2026-10-01T00:00:00Z", VERSION_ROUTE,
                OWNER_VERSIONS},
            new String[] {PRODUCTS, PRODUCT_ROUTE, "/products/v1"},
            new String[] {CAPABILITIES, CAPABILITY_ROUTE, "/products/v1/capabilities"},
            new String[] {FEE_EVENTS, FEE_EVENT_ROUTE, "/products/v1/fee-events"},
            new String[] {DASHBOARD, DASHBOARD_ROUTE, OWNER_VERSIONS});
        for (String[] endpoint : endpoints) {
            for (var error : List.of(CommonErrorCode.BUSINESS_CHECK_FAILED, CommonErrorCode.NOT_FOUND)) {
                int status = error == CommonErrorCode.NOT_FOUND ? 404 : 400;
                commercial.problem("GET", endpoint[2], status, error.getCode());
                if (DASHBOARD.equals(endpoint[0])) {
                    commercial.reply("GET", OWNER_AGREEMENTS, 200, JSON.writeValueAsString(emptyPage(1, 10, 0)));
                }

                var response = send("GET", endpoint[0], endpoint[1], null);

                assertEquals(status, response.statusCode(), endpoint[0] + " " + response.body());
                var body = JSON.readTree(response.body());
                assertAll(() -> assertEquals(error.getCode(), body.path("code").asString()),
                    () -> assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
                    () -> assertFalse(response.body().contains(CommercialOwnerSimulator.OWNER_REQUEST_ID)));
                resetBetweenCalls();
            }
        }
    }

    /** 分页回执的页码与请求不一致、条目多于每页条数、或目录缺少集合，按上游无效响应 502 处理。 */
    @Test
    void test_upstreamContractDrift() throws Exception {
        commercial.reply("GET", OWNER_AGREEMENTS, 200, JSON.writeValueAsString(emptyPage(2, 10, 30)));
        assertUpstreamInvalid(send("GET", AGREEMENTS, AGREEMENT_ROUTE, null));

        resetBetweenCalls();
        var plan = row("T11").path("response").path("items").get(0);
        var eleven = JSON.createArrayNode();
        for (int index = 0; index < 11; index++) {
            eleven.add(plan);
        }
        commercial.reply("GET", OWNER_PLANS, 200, JSON.writeValueAsString(page(eleven, 1, 10, 11)));
        assertUpstreamInvalid(send("GET", PLANS, PLAN_ROUTE, null));

        resetBetweenCalls();
        commercial.reply("GET", "/products/v1", 200, "{}");
        assertUpstreamInvalid(send("GET", PRODUCTS, PRODUCT_ROUTE, null));
    }

    /** 合同列表与目录需要合同只读；方案与版本列表需要定价只读。 */
    @Test
    void test_authorizationDenied() throws Exception {
        platform.grantPermissions(COMMERCIAL_PRICING_READ_PERMISSION_ID);
        assertDenied(send("GET", AGREEMENTS, AGREEMENT_ROUTE, null));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_READ_PERMISSION_ID);
        assertDenied(send("GET", CAPABILITIES, CAPABILITY_ROUTE, null));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_CONTRACT_READ_PERMISSION_ID);
        assertDenied(send("GET", VERSIONS, VERSION_ROUTE, null));
    }

    private void replyAgreementCount(CommercialAgreementStatus status, long total) {
        commercial.reply("GET", OWNER_AGREEMENTS + "?" + firstPage(status.getCode()), 200,
            JSON.writeValueAsString(emptyPage(1, 10, total)));
    }

    private static void assertUpstreamInvalid(java.net.http.HttpResponse<String> response) {
        assertEquals(502, response.statusCode(), response.body());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(), JSON.readTree(response.body()).path("code").asString());
    }

    /** 工作台计数调用的 Query：单一状态、第 1 页、10 条。 */
    private static String firstPage(String status) {
        return "status=" + status + "&page=1&size=10";
    }

    private static ObjectNode emptyPage(int page, int size, long total) {
        return page(JSON.createArrayNode(), page, size, total);
    }

    private static ObjectNode page(JsonNode items, int page, int size, long total) {
        var body = JSON.createObjectNode();
        body.set("items", items);
        return body.put("page", page).put("size", size).put("total", total);
    }
}
