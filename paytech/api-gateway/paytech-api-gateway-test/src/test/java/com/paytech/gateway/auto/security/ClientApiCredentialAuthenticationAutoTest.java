package com.paytech.gateway.auto.security;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.gateway.test.ClientApiGatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Outcome;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;
import com.paytech.gateway.web.GatewayProtocol;

/** 从真实 Gateway 端口经过 PLT-I03 HTTP、Redis、Scope 到 Owner。 */
class ClientApiCredentialAuthenticationAutoTest extends ClientApiGatewayAutoTestBase {

    @Test
    void test_missingCredentialStopsBeforePlatformAndOwner() {
        assertProblem(client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH).exchange().expectBody().returnResult(), 401, "UNAUTHENTICATED");
        assertEquals(0, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @Test
    void test_validCredentialAndExactScopeReachOwnerOnce() {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.POST,
            GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, Outcome.ACCEPTED);
        var result = client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL)
            .header(GatewayProtocol.REQUEST_ID, "client-auth-success").bodyValue("{}").exchange()
            .expectBody().returnResult();
        assertEquals(200, result.getStatus().value());
        assertEquals("client-auth-success", result.getResponseHeaders().getFirst(GatewayProtocol.REQUEST_ID));
        assertFalse(result.getResponseHeaders().containsHeader(HttpHeaders.VARY));
        assertEquals(1, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(1, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @Test
    void test_validCredentialWithoutExactScopeNeverCallsOwner() {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write.extra", "orders.read");
        assertProblem(client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL)
            .exchange().expectBody().returnResult(), 403, "INSUFFICIENT_SCOPE");
        assertEquals(1, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @Test
    void test_platformInvalidCredentialIsOneGeneric401() {
        GatewayAutoTestRuntime.credentialSimulator().expectInvalid();
        assertProblem(client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL)
            .exchange().expectBody().returnResult(), 401, "UNAUTHENTICATED");
        assertEquals(1, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @Test
    void test_eachAttemptRevalidatesTheSameCredentialWithoutCachingOrRetry() {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        GatewayAutoTestRuntime.credentialSimulator().expectInvalid();
        GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.POST,
            GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, Outcome.ACCEPTED);
        var first = authenticatedRequest().exchange().expectBody().returnResult();
        assertEquals(200, first.getStatus().value());
        assertProblem(authenticatedRequest().exchange().expectBody().returnResult(), 401, "UNAUTHENTICATED");
        assertEquals(2, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(1, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

}
