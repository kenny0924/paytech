package com.paytech.platform.support.auto.ext.http.operations.notification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi;
import com.paytech.platform.support.api.notificationmanagement.request.ChangeNotificationStatusRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.SaveNotificationRouteRequestBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationMutationResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationTemplateDetailResponseBody;
import com.paytech.platform.support.test.base.NotificationManagementTestBase;

/**
 * 通知路由：新建与修改主备账号、启停；账号必须是启用的 EMAIL 账号且主备不同，版本不符返回 409。
 *
 * @see NotificationManagementApi#saveRoute(String, String, String, SaveNotificationRouteRequestBody)
 * @see NotificationManagementApi#changeRouteStatus(String, String, String, ChangeNotificationStatusRequestBody)
 */
class NotificationRouteManagementTest extends NotificationManagementTestBase {

    static final String NEW_TEMPLATE = "NOTIFICATION_ADMIN_ROUTE";

    /** 新模板的路由新建为启用，详情按编码展示主备账号；写入记审计。 */
    @Test
    void test_createRoute() {
        post(BASE + "/templates", admin, Map.of("templateCode", NEW_TEMPLATE, "reason", "新增")).assertOk();

        var saved = put(BASE + "/templates/" + NEW_TEMPLATE + "/route", admin,
            route("NOTIFICATION_TEST_EMAIL", "NOTIFICATION_TEST_FALLBACK", 0)).assertOk()
            .as(NotificationMutationResponseBody.class);

        assertEquals(1, saved.getVersion());
        auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_ROUTE_SAVE", "SUCCEEDED", admin.actor(),
            "NOTIFICATION_ROUTE", NEW_TEMPLATE);
        var detail = get(BASE + "/templates/" + NEW_TEMPLATE, reader).assertOk()
            .as(NotificationTemplateDetailResponseBody.class);
        assertAll(() -> assertEquals("NOTIFICATION_TEST_EMAIL", detail.getRoute().getPrimaryProviderCode()),
            () -> assertEquals("NOTIFICATION_TEST_FALLBACK", detail.getRoute().getFallbackProviderCode()),
            () -> assertEquals("ACTIVE", detail.getRoute().getStatus()));
    }

    /** 按版本替换主账号并清空备用账号。 */
    @Test
    void test_changeRoute() {
        put(BASE + "/templates/NOTIFICATION_TEST/route", admin,
            route("NOTIFICATION_TEST_EMAIL", "NOTIFICATION_TEST_FALLBACK", 1)).assertOk();
        assertEquals("NOTIFICATION_TEST_FALLBACK", mapper.routeState("NOTIFICATION_TEST").get("fallback_code"));
        var saved = put(BASE + "/templates/NOTIFICATION_TEST/route", admin, route("NOTIFICATION_TEST_SES", null, 2))
            .assertOk().as(NotificationMutationResponseBody.class);

        assertEquals(3, saved.getVersion());
        var row = mapper.routeState("NOTIFICATION_TEST");
        assertAll(() -> assertEquals("NOTIFICATION_TEST_SES", row.get("primary_code")),
            () -> assertNull(row.get("fallback_code")));
    }

    /** 主备相同、账号停用或不存在拒绝；版本过期、重复新建冲突；路由不存在 404；均不改数据。 */
    @Test
    void test_routeRejected() {
        String path = BASE + "/templates/NOTIFICATION_TEST/route";
        put(path, admin, route("NOTIFICATION_TEST_EMAIL", "NOTIFICATION_TEST_EMAIL", 1))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        put(path, admin, route("NOTIFICATION_ADMIN_UNBOUND", null, 1)).assertProblem(400, "BUSINESS_CHECK_FAILED");
        put(path, admin, route("NOTIFICATION_ADMIN_NONE", null, 1)).assertProblem(400, "BUSINESS_CHECK_FAILED");
        put(path, admin, route("NOTIFICATION_TEST_SES", null, 3)).assertProblem(409, "CONCURRENT_REQUEST");
        put(path, admin, route("NOTIFICATION_TEST_SES", null, 0)).assertProblem(409, "CONCURRENT_REQUEST");
        post(BASE + "/templates", admin, Map.of("templateCode", NEW_TEMPLATE, "reason", "新增")).assertOk();
        put(BASE + "/templates/" + NEW_TEMPLATE + "/route", admin, route("NOTIFICATION_TEST_SES", null, 1))
            .assertProblem(404, "NOT_FOUND");
        patch(BASE + "/templates/" + NEW_TEMPLATE + "/route/status", admin, status("ACTIVE", 1))
            .assertProblem(404, "NOT_FOUND");
        put(BASE + "/templates/" + NEW_TEMPLATE + "/route", admin, route("NOTIFICATION_TEST_SES", null, 0)).assertOk();
        mapper.changeProvider("ACTIVE", "SMS");
        put(path, admin, route("NOTIFICATION_TEST_EMAIL", null, 1)).assertProblem(400, "BUSINESS_CHECK_FAILED");

        var row = mapper.routeState("NOTIFICATION_TEST");
        assertAll(() -> assertEquals("NOTIFICATION_TEST_EMAIL", row.get("primary_code")),
            () -> assertEquals(1, ((Number) row.get("version")).intValue()));
    }

    /** 停用路由；主账号停用后不能重新启用路由，账号恢复后可以。 */
    @Test
    void test_routeStatus() {
        String path = BASE + "/templates/NOTIFICATION_TEST/route/status";
        patch(path, admin, status("DISABLED", 1)).assertOk();
        assertEquals("DISABLED", mapper.routeState("NOTIFICATION_TEST").get("status"));
        auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_ROUTE_STATUS_CHANGE", "SUCCEEDED",
            admin.actor(), "NOTIFICATION_ROUTE", "NOTIFICATION_TEST");

        patch(BASE + "/providers/NOTIFICATION_TEST_EMAIL/status", admin, status("DISABLED", 1)).assertOk();
        patch(path, admin, status("ACTIVE", 2)).assertProblem(400, "BUSINESS_CHECK_FAILED");
        patch(BASE + "/providers/NOTIFICATION_TEST_EMAIL/status", admin, status("ACTIVE", 2)).assertOk();
        patch(path, admin, status("ACTIVE", 2)).assertOk();

        assertEquals("ACTIVE", mapper.routeState("NOTIFICATION_TEST").get("status"));
    }

    /** 保存路由请求体；备用账号可空。 */
    static Map<String, Object> route(String primary, String fallback, int expectedVersion) {
        var body = new HashMap<String, Object>();
        body.put("primaryProviderCode", primary);
        body.put("fallbackProviderCode", fallback);
        body.put("expectedVersion", expectedVersion);
        body.put("reason", "测试路由");
        return body;
    }
}
