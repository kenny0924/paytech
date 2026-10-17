package com.paytech.gateway.auto.guard;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.RedisUnavailableGatewayAutoTestBase;

/** Redis Guard 不可用时的私有全 HTTP Fail-Closed 测试。 */
class RedisGuardUnavailableAutoTest extends RedisUnavailableGatewayAutoTestBase {
    @Test
    void test_loginReturnsProblem503WithoutCallingPrivateBff() {
        assertProblem(gatewayClient().post().uri(OperationsTestPaths.LOGIN)
            .header(HttpHeaders.ORIGIN, "https://operations-console.test").bodyValue("{}").exchange(), 503,
            "GATEWAY_DEPENDENCY_UNAVAILABLE");

        Assertions.assertEquals(0, operationsBffSimulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }
}
