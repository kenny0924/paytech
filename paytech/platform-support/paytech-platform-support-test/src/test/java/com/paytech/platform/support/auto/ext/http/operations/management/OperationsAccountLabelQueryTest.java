package com.paytech.platform.support.auto.ext.http.operations.management;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.operations.OperationsAccountLabelQueryApi;
import com.paytech.platform.support.api.operations.response.OperationsAccountLabelListResponseBody;
import com.paytech.platform.support.api.operations.response.OperationsAccountLabelResponseBody;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

/**
 * Operations 账号显示名批量查询：经真实 Controller、Processor、Service、Repository、Mapper 读取本地插入的账号行。
 *
 * @see OperationsAccountLabelQueryApi#listLabels(String, String)
 */
class OperationsAccountLabelQueryTest extends MockTestBase {

    private static final String PATH = "/operations-accounts/v1/labels";

    private static final String BFF = "operations-console-bff";

    /** 登录名优先；没有登录名时回退脱敏邮箱；Bootstrap 账号同样返回；按账号 ID 升序、ID 以字符串输出。 */
    @Test
    void test_normalFlow() {
        long admin = operationsMocker.account(AuthenticationRealm.OPERATIONS, OperationsAccountType.BOOTSTRAP_ADMIN,
            "admin", null);
        long named = operationsMocker.account(AuthenticationRealm.OPERATIONS, OperationsAccountType.HUMAN, "kenny",
            "ke***@example.test");
        long masked = operationsMocker.account(AuthenticationRealm.OPERATIONS, OperationsAccountType.HUMAN, null,
            "op***@example.test");
        long blankLogin = operationsMocker.account(AuthenticationRealm.OPERATIONS, OperationsAccountType.HUMAN, " ",
            "bl***@example.test");

        Map<String, String> labels = labels(list(BFF, ids(masked, named, admin, blankLogin)));

        Map<Long, String> expected = new TreeMap<>(Map.of(admin, "admin", named, "kenny", masked, "op***@example.test",
            blankLogin, "bl***@example.test"));
        assertEquals(expected.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList(),
            labels.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList());
    }

    /** CUSTOMER Realm 账号、不存在的账号以及登录名与脱敏邮箱都为空的账号不返回。 */
    @Test
    void test_noDataFound() {
        long customer = operationsMocker.account(AuthenticationRealm.CUSTOMER, OperationsAccountType.HUMAN, null,
            "cu***@example.test");
        long empty = operationsMocker.account(AuthenticationRealm.OPERATIONS, OperationsAccountType.HUMAN, null, null);
        long named = operationsMocker.account(AuthenticationRealm.OPERATIONS, OperationsAccountType.HUMAN, "ops-reader",
            null);
        long unknown = named + 1;

        Map<String, String> labels = labels(list(BFF, ids(customer, empty, unknown, named, named)));

        assertEquals(Map.of(Long.toString(named), "ops-reader"), labels);
        assertTrue(labels(list(BFF, ids(customer, unknown))).isEmpty());
    }

    /** 账号 ID 缺失、为空、含空段或非数字、非正数、超过 50 个：400 INVALID_REQUEST；正好 50 个可以查询。 */
    @Test
    void test_invalidParameter() {
        String fifty = LongStream.rangeClosed(1, 50).mapToObj(Long::toString).collect(Collectors.joining(","));

        assertAll(() -> get(BFF, PATH).assertProblem(400, CommonErrorCode.INVALID_REQUEST),
            () -> get(BFF, PATH + "?accountIds=").assertProblem(400, CommonErrorCode.INVALID_REQUEST),
            () -> get(BFF, PATH + "?accountIds=1,,2").assertProblem(400, CommonErrorCode.INVALID_REQUEST),
            () -> get(BFF, PATH + "?accountIds=1,abc").assertProblem(400, CommonErrorCode.INVALID_REQUEST),
            () -> get(BFF, PATH + "?accountIds=0").assertProblem(400, CommonErrorCode.INVALID_REQUEST),
            () -> get(BFF, PATH + "?accountIds=99999999999999999999").assertProblem(400,
                CommonErrorCode.INVALID_REQUEST),
            () -> get(BFF, PATH + "?accountIds=" + fifty + ",51").assertProblem(400, CommonErrorCode.INVALID_REQUEST),
            () -> assertTrue(list(BFF, fifty).getItems().isEmpty()));
    }

    /** 调用方不是 operations-console-bff：400 BUSINESS_CHECK_FAILED，即使账号存在也不返回显示名。 */
    @Test
    void test_callerRejected() {
        long named = operationsMocker.account(AuthenticationRealm.OPERATIONS, OperationsAccountType.HUMAN, "kenny",
            null);

        assertAll(() -> get("card-service", PATH + "?accountIds=" + named)
                .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED),
            () -> get(null, PATH + "?accountIds=" + named).assertProblem(400, CommonErrorCode.INVALID_REQUEST));
    }

    /** 账号 ID 拼成 Query 值。 */
    private static String ids(long... accountIds) {
        return Arrays.stream(accountIds).mapToObj(Long::toString).collect(Collectors.joining(","));
    }

    /** 响应元素按原顺序转为账号 ID 到显示名。 */
    private static Map<String, String> labels(OperationsAccountLabelListResponseBody body) {
        return body.getItems().stream().collect(Collectors.toMap(OperationsAccountLabelResponseBody::getAccountId,
            OperationsAccountLabelResponseBody::getLabel, (left, right) -> left, LinkedHashMap::new));
    }

    /** 以指定调用方查询并断言成功。 */
    private OperationsAccountLabelListResponseBody list(String caller, String accountIds) {
        return get(caller, PATH + "?accountIds=" + accountIds).assertOk()
            .as(OperationsAccountLabelListResponseBody.class);
    }

    /** 以指定调用方发送 GET；调用方为 null 时不带 X-Caller-Service。 */
    private PlatformResponseChecker get(String caller, String path) {
        String requestId = "plt-account-label-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        if (caller != null) {
            headers.set(OperationsAccountLabelQueryApi.CALLER_SERVICE_HEADER, caller);
        }
        return new PlatformResponseChecker(httpTestInvoker.get(path, headers), objectMapper, requestId);
    }
}
