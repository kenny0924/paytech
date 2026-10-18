package com.paytech.gateway.auto.idempotency;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.gateway.idempotency.IdempotencyKeyPolicy;
import com.paytech.gateway.test.RedisUnavailableGatewayAutoTestBase;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;

/** Redis 整体不可用时 REQUIRED 路由失败关闭，不绕过幂等直接下发。 */
class ClientApiIdempotencyRedisUnavailableAutoTest extends RedisUnavailableGatewayAutoTestBase {
    @Test
    void test_requiredRouteReturns503WithoutAuthenticationOrOwnerCall() {
        publishSnapshot(GatewaySnapshotFixture.clientApiIdempotencySnapshot());

        assertProblem(clientApiGatewayClient().post().uri(GatewaySnapshotFixture.IDEMPOTENT_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL)
            .header(IdempotencyKeyPolicy.HEADER_NAME, "order-redis-down").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}").exchange(), 503, "DEPENDENCY_UNAVAILABLE");

        Assertions.assertEquals(0, credentialSimulator().count());
    }
}
