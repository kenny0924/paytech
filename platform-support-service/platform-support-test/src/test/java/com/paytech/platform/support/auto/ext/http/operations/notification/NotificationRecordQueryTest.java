package com.paytech.platform.support.auto.ext.http.operations.notification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi;
import com.paytech.platform.support.api.notificationmanagement.response.AttemptItem;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationRequestDetailResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationRequestSummaryResponseBody;
import com.paytech.platform.support.test.base.NotificationManagementTestBase;

import tools.jackson.core.type.TypeReference;

/**
 * 发送记录：按时间范围与条件分页、查看投递与尝试；不返回收件邮箱、Vault Token、参数值和供应商流水号。
 *
 * @see NotificationManagementApi#requests(String, String, Integer, Integer, String, String, String, String, String, String)
 * @see NotificationManagementApi#request(String, String, String)
 */
class NotificationRecordQueryTest extends NotificationManagementTestBase {

    private Instant created;

    /** 一条一小时前受理的记录：两个收件人、三次尝试。 */
    @BeforeEach
    void setUpRecordMocks() {
        created = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        fixture.prepareRecord(created);
    }

    /** 时间范围内按条件筛选；状态计数按投递汇总；响应不含敏感值。 */
    @Test
    void test_listRequests() {
        var response = get(requests(""), reader).assertOk();
        var page = response.as(new TypeReference<PagingResponseBody<NotificationRequestSummaryResponseBody>>() {});

        assertEquals(1, page.total());
        var item = page.items().getFirst();
        assertAll(() -> assertEquals("890021", item.getRequestId()),
            () -> assertEquals("NOTIFICATION_TEST", item.getTemplateCode()), () -> assertEquals(2, item.getDeliveryCount()),
            () -> assertEquals(Map.of("SENT", 1, "RETRY_WAIT", 1), item.getStatusCounts()),
            () -> assertNull(item.getTenantId()));
        assertNoSensitive(response.text());
        assertEquals(1, total(requests("&deliveryStatus=RETRY_WAIT&sourceEventKey=refund:R-0001")));
        assertEquals(1, total(requests("&sourceSystem=notification-test-admin&templateCode=NOTIFICATION_TEST")));
        assertEquals(0, total(requests("&deliveryStatus=FAILED")));
        assertEquals(0, total(requests("&templateCode=NOTIFICATION_ADMIN_NONE")));
        assertEquals(0, total(BASE + "/requests?createdFrom=" + created.plusSeconds(1) + "&createdTo="
            + created.plusSeconds(60)));
    }

    /** 详情含两个投递与三次尝试，账号以编码表示，参数只给名称。 */
    @Test
    void test_requestDetail() {
        var response = get(BASE + "/requests/890021", reader).assertOk();
        var detail = response.as(NotificationRequestDetailResponseBody.class);

        assertAll(() -> assertEquals(List.of("amount", "name"), detail.getParameterKeys()),
            () -> assertEquals("890005", detail.getRouteId()), () -> assertEquals(2, detail.getDeliveries().size()));
        var first = detail.getDeliveries().get(0);
        var second = detail.getDeliveries().get(1);
        assertAll(() -> assertNull(first.getLocale()), () -> assertEquals("SENT", first.getStatus()),
            () -> assertEquals(List.of("NOTIFICATION_TEST_EMAIL"), providers(first.getAttempts())),
            () -> assertEquals("en-US", second.getLocale()), () -> assertEquals(List.of("name"), second.getParameterKeys()),
            () -> assertEquals(List.of("NOTIFICATION_TEST_EMAIL", "NOTIFICATION_TEST_FALLBACK"),
                providers(second.getAttempts())),
            () -> assertEquals("THROTTLED", second.getAttempts().get(1).getResponseCode()));
        assertNoSensitive(response.text());
        get(BASE + "/requests/123456", reader).assertProblem(404, "NOT_FOUND");
        get(BASE + "/requests/abc", reader).assertProblem(400, "INVALID_REQUEST");
    }

    /** 时间范围必填、不超过 31 天，状态必须合法；运营只读人员无权查看。 */
    @Test
    void test_invalidQuery() {
        get(BASE + "/requests", reader).assertProblem(400, "INVALID_REQUEST");
        get(BASE + "/requests?createdFrom=" + created.minus(Duration.ofDays(40)) + "&createdTo=" + created, reader)
            .assertProblem(400, "INVALID_REQUEST");
        get(BASE + "/requests?createdFrom=yesterday&createdTo=" + created, reader).assertProblem(400, "INVALID_REQUEST");
        get(requests("&deliveryStatus=LOST"), reader).assertProblem(400, "INVALID_REQUEST");
        get(requests(""), viewer).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
    }

    /** 覆盖本例记录的时间范围加附加条件。 */
    String requests(String extra) {
        return BASE + "/requests?page=1&size=20&createdFrom=" + created.minus(Duration.ofDays(1)) + "&createdTo="
            + Instant.now().plusSeconds(60) + extra;
    }

    /** 查询结果总数。 */
    long total(String path) {
        return get(path, reader).assertOk()
            .as(new TypeReference<PagingResponseBody<NotificationRequestSummaryResponseBody>>() {}).total();
    }

    /** 尝试使用的账号编码。 */
    static List<String> providers(List<AttemptItem> attempts) {
        return attempts.stream().map(AttemptItem::getProviderCode).toList();
    }

    /** 不含 Vault Token、参数值与供应商请求号、消息标识。 */
    static void assertNoSensitive(String text) {
        for (String secret : List.of("vlt_", "Alice", "Bob", "10.00", "admin-test-req", "admin-test-msg")) {
            assertFalse(text.contains(secret), "响应包含 " + secret);
        }
    }
}
