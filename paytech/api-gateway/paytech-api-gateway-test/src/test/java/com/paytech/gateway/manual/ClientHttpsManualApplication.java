package com.paytech.gateway.manual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.support.TestPropertySourceUtils;
import com.paytech.gateway.ApiGatewayApplication;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefresher;
import com.paytech.gateway.test.simulator.platform.PlatformSnapshotSimulator;
import com.paytech.platform.support.api.gatewayconfiguration.response.*;

/** 显式本机联调使用真实 Gateway 与 CUSTOMER JWKS，路由目录采用隔离的协议夹具。 */
public final class ClientHttpsManualApplication {
    /** 启动本轮 Gateway，不注册或修改共享控制面。 */
    public static void main(String[] args) throws Exception {
        var env = new StandardEnvironment();
        String run = env.getRequiredProperty("CLIENT_HTTPS_RUN_ID");
        if (!run.matches("[a-f0-9-]{36}")) { throw new IllegalArgumentException("缺少本机联调身份"); }
        var snapshot = new PlatformSnapshotSimulator(Duration.ofSeconds(10));
        snapshot.publish(snapshot());
        snapshot.start();
        var app = new SpringApplication(ApiGatewayApplication.class);
        app.setAdditionalProfiles("auto");
        app.addInitializers(context -> TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
            "gateway.client.enabled=true", "gateway.client.console-origin=" + env.getRequiredProperty("CLIENT_HTTPS_ORIGIN"),
            "gateway.client.security.issuer=https://customer.client-https.test",
            "gateway.operations.guard.redis.namespace=gateway-auto:" + run,
            "gateway.platform-jwks.base-uri=" + env.getRequiredProperty("CLIENT_HTTPS_PLATFORM_URL"),
            "gateway.platform-snapshot.base-uri=" + snapshot.uri(),
            "gateway.platform-credential.base-uri=" + snapshot.uri(),
            "spring.cloud.discovery.client.simple.instances.client-https-bff[0].uri=" + env.getRequiredProperty("CLIENT_HTTPS_BFF_URL")));
        var context = app.run(args);
        if (!Boolean.TRUE.equals(context.getBean(GatewaySnapshotRefresher.class).refresh().block(Duration.ofSeconds(5)))) {
            context.close();
            snapshot.stop();
            throw new IllegalStateException("本轮 Client 路由目录未加载");
        }
        context.addApplicationListener(event -> {
            if (event instanceof org.springframework.context.event.ContextClosedEvent) {
                snapshot.stop();
            }
        });
        Files.writeString(Path.of(env.getRequiredProperty("CLIENT_HTTPS_STATE_DIR"), "gateway.json"),
            "{\"port\":" + ((WebServerApplicationContext)context).getWebServer().getPort() + "}");
    }

    /** 九个认证合同都经过生产 Snapshot 编译和 Guard，不伪造认证结果。 */
    private static GatewayPublicSnapshotResponseBody snapshot() {
        return new GatewayPublicSnapshotResponseBody(1, List.of(new GatewayTargetSnapshot("client-console-bff", "CLIENT", "client-https-bff")),
            List.of(route("register", "PUBLIC", "DROP", "NONE"), route("email-verification-requests", "PUBLIC", "DROP", "NONE"),
                route("email-verifications", "PUBLIC", "DROP", "NONE"), route("password-reset-requests", "PUBLIC", "DROP", "NONE"),
                route("password-resets", "PUBLIC", "DROP", "NONE"), route("login", "PUBLIC", "DROP", "ISSUE_ONLY"),
                route("refresh", "PUBLIC", "REQUIRE_PROFILE_COOKIE", "ISSUE_OR_CLEAR"),
                route("logout", "CLIENT_JWT", "REQUIRE_PROFILE_COOKIE", "CLEAR_ONLY"),
                route("context", "CLIENT_JWT", "DROP", "NONE")));
    }

    /** 固定本轮目录的精确路径、Cookie 和限流边界。 */
    private static GatewayRouteSnapshot route(String action, String profile, String requestCookie, String responseCookie) {
        var guards = new ArrayList<GatewayGuardRuleSnapshot>();
        for (String dimension : "PUBLIC".equals(profile) ? List.of("SHARED_ROUTE", "CLIENT_IP")
            : List.of("SHARED_ROUTE", "CLIENT_IP", "AUTHENTICATED_PRINCIPAL")) {
            guards.add(new GatewayGuardRuleSnapshot("RATE_LIMIT", dimension, 300L, 300L, 60_000L, null));
            guards.add(new GatewayGuardRuleSnapshot("CONCURRENCY", dimension, null, null, null, 20L));
        }
        boolean query = action.equals("context");
        return new GatewayRouteSnapshot("client-https-" + action, "CLIENT", query ? "GET" : "POST",
            "/api/client/auth/v1/" + action, "/client-auth/v1/" + action, "client-console-bff", profile,
            "PUBLIC".equals(profile) ? "PUBLIC" : "HUMAN_AUTHENTICATED", null, null, requestCookie, responseCookie,
            List.of("content-type", "origin", "authorization"), List.of("content-type", "origin"), List.of(),
            query ? 0 : 4096, 5000, 10000, guards);
    }
}
