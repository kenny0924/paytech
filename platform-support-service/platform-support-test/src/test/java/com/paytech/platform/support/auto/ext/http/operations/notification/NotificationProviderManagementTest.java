package com.paytech.platform.support.auto.ext.http.operations.notification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi;
import com.paytech.platform.support.api.notificationmanagement.request.ChangeNotificationStatusRequestBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationMutationResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationProviderResponseBody;
import com.paytech.platform.support.test.base.NotificationManagementTestBase;

import tools.jackson.core.type.TypeReference;

/**
 * 发送账号：列表展示本环境是否已配置适配器与路由引用，启停按版本保护；不返回发件地址与凭据。
 *
 * @see NotificationManagementApi#providers(String, String, Integer, Integer)
 * @see NotificationManagementApi#changeProviderStatus(String, String, String, ChangeNotificationStatusRequestBody)
 */
class NotificationProviderManagementTest extends NotificationManagementTestBase {

    /** 按编码排序；无适配器的账号为未配置；引用按主备区分。 */
    @Test
    void test_listProviders() {
        mapper.useFallback();
        var response = get(BASE + "/providers?page=1&size=10", reader).assertOk();
        var page = response.as(new TypeReference<PagingResponseBody<NotificationProviderResponseBody>>() {});

        assertEquals(List.of("NOTIFICATION_ADMIN_UNBOUND", "NOTIFICATION_TEST_EMAIL", "NOTIFICATION_TEST_FALLBACK",
            "NOTIFICATION_TEST_SES"), page.items().stream().map(NotificationProviderResponseBody::getProviderCode)
            .toList());
        var unbound = page.items().get(0);
        var email = page.items().get(1);
        var ses = page.items().get(3);
        assertAll(() -> assertFalse(unbound.isAdapterConfigured()), () -> assertEquals("DISABLED", unbound.getStatus()),
            () -> assertTrue(email.isAdapterConfigured()), () -> assertTrue(ses.isAdapterConfigured()),
            () -> assertEquals(List.of("NOTIFICATION_TEST:PRIMARY"), refs(email)),
            () -> assertEquals(List.of("CLIENT_EMAIL_VERIFICATION:PRIMARY", "CLIENT_PASSWORD_RESET:PRIMARY"),
                refs(ses).stream().sorted().toList()),
            () -> assertEquals(List.of("NOTIFICATION_TEST:FALLBACK"), refs(page.items().get(2))));
        assertFalse(response.text().contains("noreply@example.com"), "不应返回发件地址");
        get(BASE + "/providers", viewer).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
    }

    /** 停用账号并记审计；状态未变化 400，版本过期 409，账号不存在 404，只读人员 403。 */
    @Test
    void test_changeProviderStatus() {
        String path = BASE + "/providers/NOTIFICATION_TEST_EMAIL/status";
        var changed = patch(path, admin, status("DISABLED", 1)).assertOk().as(NotificationMutationResponseBody.class);

        assertEquals(2, changed.getVersion());
        assertEquals("DISABLED", mapper.providerState("NOTIFICATION_TEST_EMAIL").get("status"));
        auditChecker.assertEvent(requestId, "OPERATIONS_NOTIFICATION_PROVIDER_STATUS_CHANGE", "SUCCEEDED",
            admin.actor(), "NOTIFICATION_PROVIDER", "NOTIFICATION_TEST_EMAIL");
        patch(path, admin, status("DISABLED", 2)).assertProblem(400, "BUSINESS_CHECK_FAILED");
        patch(path, admin, status("ACTIVE", 1)).assertProblem(409, "CONCURRENT_REQUEST");
        patch(BASE + "/providers/NOTIFICATION_ADMIN_NONE/status", admin, status("ACTIVE", 1))
            .assertProblem(404, "NOT_FOUND");
        patch(path, reader, status("ACTIVE", 2)).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        patch(path, admin, Map.of("status", "PAUSED", "expectedVersion", 2, "reason", "x"))
            .assertProblem(422, "VALIDATION_ERROR").assertFieldError("status");
    }

    /** 引用的模板与角色。 */
    static List<String> refs(NotificationProviderResponseBody item) {
        return item.getReferences().stream().map(r -> r.getTemplateCode() + ":" + r.getRole()).toList();
    }
}
