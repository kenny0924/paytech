package com.paytech.gateway.auto.security;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.paytech.gateway.guard.GatewayAuthenticatedGuardGlobalFilter;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;
import com.paytech.gateway.security.clientapi.ClientApiScopeGlobalFilter;
import com.paytech.gateway.security.identity.OperationsIdentity;
import com.paytech.gateway.test.ClientApiGatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.web.ClientApiDownstreamIdentityGlobalFilter;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

/** 聚焦内部不变量：每个身份消费者独立 Fail-Closed，不能借用 Operations identity。 */
class ClientApiIdentityInvariantAutoTest extends ClientApiGatewayAutoTestBase {
    @Autowired private GatewaySnapshotStore snapshots;
    @Autowired private GatewayAuthenticatedGuardGlobalFilter guard;
    @Autowired private ClientApiScopeGlobalFilter scope;
    @Autowired private ClientApiDownstreamIdentityGlobalFilter downstream;

    @Test
    void test_missingOrWrongTypedIdentityFailsClosedAtEveryConsumer() throws Exception {
        var route = snapshots.current().orElseThrow().catalog().all().stream()
            .filter(value -> value.channel() == GatewayRouteChannel.CLIENT_API).findFirst().orElseThrow();
        for (boolean wrongType : List.of(false, true)) {
            for (GlobalFilter filter : List.of(guard, scope, downstream)) {
                var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(GatewaySnapshotFixture.CLIENT_API_PATH));
                exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
                exchange.getAttributes().put(GatewayExchangeAttributes.ENTRY_CHANNEL, GatewayRouteChannel.CLIENT_API);
                exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "identity-invariant");
                exchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_ADDRESS, InetAddress.getLoopbackAddress());
                if (wrongType) {
                    exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY,
                        new OperationsIdentity("operator", "session", "TENANT", "tenant", "member"));
                }
                filter.filter(exchange, ignored -> { fail("身份合同损坏不得继续过滤链"); return reactor.core.publisher.Mono.empty(); })
                    .block();
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
                assertTrue(exchange.getResponse().getBodyAsString().block().contains("DEPENDENCY_UNAVAILABLE"));
            }
        }
        assertEquals(0, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }
}
