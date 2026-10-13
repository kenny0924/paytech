package com.paytech.platform.support.auto.ext.http.operations.management;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.operations.OperationsManagementApi;
import com.paytech.platform.support.api.operations.response.AuditEvent;
import com.paytech.platform.support.api.operations.response.AuditEventResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.mocker.local.AuditMocker;

import tools.jackson.core.type.TypeReference;

/**
 * Operations 审计查询：范围限定、组合过滤、时间区间、分页排序、理由与属性分级投影。
 *
 * @see OperationsManagementApi#auditEvents(String, String, Integer, Integer, Instant, Instant, String, String,
 *      String, String, String, String)
 * @see OperationsManagementApi#auditEvent(String, String, String)
 */
class OperationsAuditQueryTest extends OperationsApiTestBase {

    private static final String EVENTS = "/operations/v1/audit-events";
    private static final Instant BASE = Instant.parse("2026-09-01T00:00:00.000001Z");

    @Autowired
    private AuditMocker audit;

    private Operator viewer;
    private long login;
    private long denied;
    private long tenantSecurity;

    /** 本类共用：三条 Operations 范围事件与两条范围外事件。 */
    @BeforeEach
    void setUpClassMocks() {
        viewer = operationsMocker.operator(OperatorBuilder.viewer());
        login = audit.event("1001", "OPERATIONS_LOGIN", "AUTH_SESSION", "2001", "SUCCEEDED", "req-1", BASE);
        audit.attribute(login, 1, "reason", "PASSWORD_ACCEPTED", null, null, "INTERNAL", BASE);
        denied = audit.event("1002", "OPERATIONS_ROLE_CREATE", "ROLE", "3001", "DENIED", "req-2", BASE.plusSeconds(60));
        tenantSecurity = audit.event("1001", "CHANGE_TENANT_SECURITY_STATUS", "ACCESS_TENANT", "4001", "SUCCEEDED",
            "req-3", BASE.plusSeconds(120));
        audit.event("1001", "CHANGE_TENANT_SECURITY_STATUS", "TENANT_MEMBER", "4002", "SUCCEEDED", "req-4",
            BASE.plusSeconds(180));
        audit.event("CLIENT", "1003", "OPERATIONS_LOGIN", "AUTH_SESSION", "2003", "SUCCEEDED", "req-5",
            BASE.plusSeconds(240));
    }

    /** 默认列表只包含 Operations 范围，按发生时间倒序，理由来自 reason 属性。 */
    @Test
    void test_listScopeAndOrder() {
        var page = list("?page=1&size=10");

        assertEquals(3, page.total());
        assertEquals(List.of(id(tenantSecurity), id(denied), id(login)),
            page.items().stream().map(AuditEvent::getAuditEventId).toList());
        var first = page.items().get(2);
        assertAll(() -> assertEquals("PASSWORD_ACCEPTED", first.getReason()),
            () -> assertEquals(BASE, first.getOccurredTime()), () -> assertEquals("req-1", first.getRequestId()),
            () -> assertNull(page.items().get(1).getReason()));
    }

    /** 每个过滤条件单独生效，时间区间为左闭右开。 */
    @Test
    void test_filters() {
        assertEquals(List.of(id(tenantSecurity), id(login)), ids(list("?actorId=1001")));
        assertEquals(List.of(id(denied)), ids(list("?actionCode=OPERATIONS_ROLE_CREATE")));
        assertEquals(List.of(id(denied)), ids(list("?result=DENIED")));
        assertEquals(List.of(id(login)), ids(list("?requestId=req-1")));
        assertEquals(List.of(id(denied)), ids(list("?resourceType=ROLE&resourceId=3001")));
        assertEquals(List.of(id(denied), id(login)),
            ids(list("?occurredFrom=" + BASE + "&occurredTo=" + BASE.plusSeconds(120))));
        assertEquals(List.of(), ids(list("?actorId=%201001%20&resultX=1&resourceType=ROLE&resourceId=x")));
    }

    /** 分页使用 1 基页码与固定页长，total 为过滤后的总数。 */
    @Test
    void test_paging() {
        for (int index = 0; index < 11; index++) {
            audit.event("1009", "OPERATIONS_REFRESH", "AUTH_SESSION", "s" + index, "SUCCEEDED", "page-" + index,
                BASE.plusSeconds(1000 + index));
        }

        var second = list("?actorId=1009&page=2&size=10");

        assertAll(() -> assertEquals(11, second.total()), () -> assertEquals(2, second.page()),
            () -> assertEquals(List.of("page-0"), second.items().stream().map(AuditEvent::getRequestId).toList()));
    }

    /** 非法查询条件在读库前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"?occurredFrom=2026-09-02T00:00:00Z&occurredTo=2026-09-01T00:00:00Z",
        "?resourceId=3001", "?result=PENDING", "?resourceType=role", "?page=0", "?size=15"})
    void test_invalidQuery(String query) {
        get(EVENTS + query, viewer).assertProblem(400, "INVALID_REQUEST");
    }

    /** 详情按分级投影属性：内部可见原文、受限只给脱敏值、带 Vault Token 的内部值不展示。 */
    @Test
    void test_detailProjection() {
        audit.attribute(denied, 1, "reason", "NO_PERMISSION", null, null, "INTERNAL", BASE);
        audit.attribute(denied, 2, "email", "secret@example.test", null, "s***@example.test", "CONFIDENTIAL", BASE);
        audit.attribute(denied, 3, "token", "raw-token-value", "vlt_token", null, "INTERNAL", BASE);
        audit.attribute(denied, 4, "public", "visible", null, null, "PUBLIC", BASE);

        var checker = get(EVENTS + "/" + denied, viewer).assertOk();
        var details = checker.as(AuditEventResponseBody.class);

        assertEquals("NO_PERMISSION", details.getEvent().getReason());
        assertEquals(Arrays.asList("NO_PERMISSION", "s***@example.test", null, "visible"),
            details.getAttributes().stream().map(attribute -> attribute.getValue()).toList());
        assertFalse(checker.text().contains("secret@example.test") || checker.text().contains("raw-token-value"));
    }

    /** 不存在或范围外的事件不可读取。 */
    @Test
    void test_detailNotFound() {
        get(EVENTS + "/123", viewer).assertProblem(400, "BUSINESS_CHECK_FAILED");
        long outside = audit.event("CLIENT", "1003", "OPERATIONS_LOGIN", "AUTH_SESSION", "x", "SUCCEEDED", "req-x",
            BASE);
        get(EVENTS + "/" + outside, viewer).assertProblem(400, "BUSINESS_CHECK_FAILED");
        get(EVENTS + "/abc", viewer).assertProblem(400, "INVALID_REQUEST");
    }

    /** 没有审计读取权限时拒绝列表与详情。 */
    @Test
    void test_requiresPermission() {
        long role = operationsMocker.customRole("NO_AUDIT", "OPERATIONS_ROLE_READ");
        Operator other = operationsMocker.operator(OperatorBuilder.administrator().roles(role));

        get(EVENTS, other).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        get(EVENTS + "/" + login, other).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
    }

    private PagingResponseBody<AuditEvent> list(String query) {
        return get(EVENTS + query, viewer).assertOk().as(new TypeReference<PagingResponseBody<AuditEvent>>() {});
    }

    private static List<String> ids(PagingResponseBody<AuditEvent> page) {
        return page.items().stream().map(AuditEvent::getAuditEventId).toList();
    }

    private static String id(long value) {
        return Long.toString(value);
    }
}
