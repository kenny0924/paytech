package com.paytech.gateway.auto.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayGuardRuleSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayTargetSnapshot;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** 通过真实 Gateway、Redis 和两个独立 HTTP 下游验证数据库快照动态转发。 */
@Import(GatewayDatabaseRoutingAutoTest.PinningConfiguration.class)
class GatewayDatabaseRoutingAutoTest extends GatewayAutoTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private RequestPauseFilter requestPause;

    /** 逻辑目标与运行时生成的发现名称分离，两条路由确实访问不同的监听端口。 */
    @Test
    void test_twoDatabaseTargetsReachIndependentDownstreams() {
        var first = snapshot("first-logical", GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE, "/dictionary/{id}");
        var secondRoute = route("second-route", "/api/operations/other/{id}", "/new-capability/{id}",
            "second-logical", List.of());
        publish(new GatewayPublicSnapshotResponseBody(1,
            List.of(first.getTargets().getFirst(), new GatewayTargetSnapshot("second-logical", "OPERATIONS",
                GatewayAutoTestRuntime.SECOND_DYNAMIC_SERVICE)), List.of(first.getRoutes().getFirst(), secondRoute)));
        expectFirst("/dictionary/123");
        expectSecond("/new-capability/456");

        get("/api/operations/dynamic/123").expectStatus().isOk().expectBody().json("{\"owner\":\"first\"}");
        get("/api/operations/other/456").expectStatus().isOk().expectBody().json("{\"owner\":\"second\"}");
        get("/api/operations/other-evil/456").expectStatus().isNotFound();
        assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        assertEquals(1, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    /** 同一逻辑目标切换发现服务后无需重启；随后修改路由绑定也按新快照转发。 */
    @Test
    void test_refreshChangesDiscoveryAndRouteBinding() {
        publish(snapshot("logical-owner", GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE, "/before/{id}"));
        expectFirst("/before/123");
        get("/api/operations/dynamic/123").expectStatus().isOk().expectBody().json("{\"owner\":\"first\"}");
        publish(snapshot("logical-owner", GatewayAutoTestRuntime.SECOND_DYNAMIC_SERVICE, "/after/{id}"));
        expectSecond("/after/123");
        get("/api/operations/dynamic/123").expectStatus().isOk().expectBody().json("{\"owner\":\"second\"}");
        publish(snapshot("replacement-owner", GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE, "/rebound/{id}"));
        expectFirst("/rebound/123");
        get("/api/operations/dynamic/123").expectStatus().isOk().expectBody().json("{\"owner\":\"first\"}");
    }

    /** 精确暂停已匹配请求，刷新后目标、路径及查询策略仍属于原快照。 */
    @Test
    void test_inFlightRequestKeepsTargetPathAndPolicyAcrossRefresh() throws Exception {
        var old = snapshot("logical-owner", GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE, "/before/{id}");
        publish(new GatewayPublicSnapshotResponseBody(1, old.getTargets(), List.of(route("dynamic-route",
            "/api/operations/dynamic/{id}", "/before/{id}", "logical-owner", List.of("detail")))));
        Pause pause = requestPause.arm();
        CompletableFuture<Void> request = CompletableFuture.runAsync(() ->
            get("/api/operations/dynamic/123?detail=full").expectStatus().isOk().expectBody()
                .json("{\"owner\":\"first\"}"));
        try {
            var captured = pause.matched.asMono().block(Duration.ofSeconds(5));
            assertNotNull(captured);
            publish(snapshot("logical-owner", GatewayAutoTestRuntime.SECOND_DYNAMIC_SERVICE, "/after/{id}"));
            assertEquals(GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE, captured.target().discoveryServiceName());
            expectSecond("/after/123");
            get("/api/operations/dynamic/123").expectStatus().isOk().expectBody().json("{\"owner\":\"second\"}");
            get("/api/operations/dynamic/123?detail=full").expectStatus().isBadRequest();
            expectFirst("/before/123");
        } finally {
            pause.release.tryEmitEmpty();
        }
        request.get(8, TimeUnit.SECONDS);
    }

    /** 无效新目标使整个快照拒绝，已加载目标仍提供原有转发。 */
    @Test
    void test_invalidRefreshRetainsLastValidTarget() {
        publish(snapshot("logical-owner", GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE, "/retained/{id}"));
        GatewayAutoTestRuntime.platformSnapshotSimulator()
            .publish(snapshot("logical-owner", "http://caller-selected", "/invalid/{id}"));
        assertFalse(refreshSnapshot());
        expectFirst("/retained/123");
        get("/api/operations/dynamic/123").expectStatus().isOk().expectBody().json("{\"owner\":\"first\"}");
    }

    /** 控制面停用后返回空有效路由，旧转发缓存不能绕过关闭操作。 */
    @Test
    void test_disabledRoutesAndTargetsStopNewRequests() {
        var enabled = snapshot("logical-owner", GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE, "/active/{id}");
        publish(enabled);
        publish(new GatewayPublicSnapshotResponseBody(1, enabled.getTargets(), List.of()));
        get("/api/operations/dynamic/123").expectStatus().isNotFound();
        publish(new GatewayPublicSnapshotResponseBody(1, List.of(), List.of()));
        get("/api/operations/dynamic/123").expectStatus().isNotFound();
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /** 已登记但无实例的目标按上游不可用失败，不回退到 Operations BFF。 */
    @Test
    void test_targetWithoutInstancesDoesNotFallBack() {
        publish(snapshot("unavailable-owner", "unregistered-discovery-service", "/unavailable/{id}"));
        get("/api/operations/dynamic/123").expectStatus().isEqualTo(503);
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    /** 从真实控制面 HTTP 响应刷新，避免直接替换生产存储。 */
    private void publish(GatewayPublicSnapshotResponseBody snapshot) {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(snapshot);
        assertTrue(refreshSnapshot(), "合法数据库快照应可整体加载");
    }

    /** 构造具有最小共享 Guard 的合法配置数据。 */
    private GatewayPublicSnapshotResponseBody snapshot(String code, String discovery, String downstream) {
        return new GatewayPublicSnapshotResponseBody(1, List.of(new GatewayTargetSnapshot(code, "OPERATIONS", discovery)),
            List.of(route("dynamic-route", "/api/operations/dynamic/{id}", downstream, code, List.of())));
    }

    /** 保持公开 GET 的完整策略输入，只按场景更换目标、路径与查询名单。 */
    private GatewayRouteSnapshot route(String code, String external, String downstream, String target,
        List<String> queries) {
        return new GatewayRouteSnapshot(code, "OPERATIONS", "GET", external, downstream, target, "PUBLIC", "PUBLIC",
            null, null, "DROP", "NONE", List.of(), List.of("origin"), queries, 0, 5_000, 1_000,
            List.of(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 300L, 300L, 60_000L, null),
                new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, null, 20L)));
    }

    /** 为第一个实际下游准备精确请求场景。 */
    private void expectFirst(String path) {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.GET, path,
            BffScenarioStore.Response.json(200, "{\"owner\":\"first\"}"));
    }

    /** 为另一个独立实际下游准备精确请求场景。 */
    private void expectSecond(String path) {
        GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.GET, path,
            ClientApiOwnerScenarioStore.Response.json(200, "{\"owner\":\"second\"}"));
    }

    /** 请求经过公开 HTTP 链及可信 Nginx 入口头，客户端不携带目标选择信息。 */
    private WebTestClient.ResponseSpec get(String path) {
        return GatewayPublicTestClient.operations(port).defaultHeader("X-Real-IP", "203.0.113.17")
            .defaultHeader("Origin", "https://operations-console.test").responseTimeout(Duration.ofSeconds(10))
            .build().get().uri(path).exchange();
    }

    /** 仅观察并暂停一次已匹配请求，不替换任何生产 Bean 或结果。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class PinningConfiguration {
        /** 注册确定时序的测试屏障。 */
        @Bean
        RequestPauseFilter requestPauseFilter() {
            return new RequestPauseFilter();
        }
    }

    static final class Pause {
        final Sinks.One<GatewayRuntimeRoute> matched = Sinks.one();
        final Sinks.Empty<Void> release = Sinks.empty();
    }

    static final class RequestPauseFilter implements WebFilter, Ordered {
        private final AtomicReference<Pause> next = new AtomicReference<>();

        /** 仅暂停下一条请求，之后的新请求可正常执行。 */
        Pause arm() {
            Pause pause = new Pause();
            assertTrue(next.compareAndSet(null, pause));
            return pause;
        }

        /** 在真实路由匹配后观察目标，等待测试显式放行。 */
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            Pause pause = next.getAndSet(null);
            if (pause == null) {
                return chain.filter(exchange);
            }
            pause.matched.tryEmitValue(exchange.getRequiredAttribute(GatewayExchangeAttributes.RUNTIME_ROUTE));
            return pause.release.asMono().timeout(Duration.ofSeconds(10)).then(Mono.defer(() -> chain.filter(exchange)));
        }

        /** 屏障必须位于固定 Route 之后、其余请求策略之前。 */
        @Override
        public int getOrder() {
            return GatewayGlobalFilterOrder.DYNAMIC_ROUTE + 1;
        }
    }
}
