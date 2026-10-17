package com.paytech.gateway.auto.security;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefresher;
import com.paytech.gateway.security.GatewayInternalTokenPolicy;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.simulator.bff.BffRequestJournal;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.web.GatewayProtocol;

/**
 * 默认 {@code internal-auth.mode=OFF}：出站请求不附加内部 Token，与接入前一致。
 *
 * @see GatewaySnapshotRefresher#refresh()
 * @see com.paytech.gateway.web.InternalTokenUpstreamGlobalFilter#filter(org.springframework.web.server.ServerWebExchange,
 *      org.springframework.cloud.gateway.filter.GatewayFilterChain)
 */
class InternalTokenDisabledAutoTest extends GatewayAutoTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private GatewayInternalTokenPolicy internalTokenPolicy;

    @Test
    void test_normalFlow_offAttachesNoToken() {
        Assertions.assertFalse(internalTokenPolicy.enabled());
        Assertions.assertTrue(GatewayAutoTestRuntime.platformSnapshotSimulator().lastAuthorization().isEmpty(),
            "OFF 时 Snapshot 请求不得携带 Authorization");
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(201, "{\"accepted\":true}"));

        GatewayPublicTestClient.operations(port).defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17").build()
            .post().uri(OperationsTestPaths.LOGIN).header(HttpHeaders.ORIGIN, "https://operations-console.test")
            .header(HttpHeaders.AUTHORIZATION, "Bearer public-caller-value").bodyValue("{\"request\":\"value\"}")
            .exchange().expectStatus().isCreated();

        List<BffRequestJournal.Entry> entries = GatewayAutoTestRuntime.operationsBffSimulator().journal().entries();
        Assertions.assertEquals(1, entries.size());
        Assertions.assertTrue(entries.getFirst().headers().keySet().stream()
            .noneMatch(HttpHeaders.AUTHORIZATION::equalsIgnoreCase), "OFF 时 BFF 不得收到 Authorization");
    }
}
