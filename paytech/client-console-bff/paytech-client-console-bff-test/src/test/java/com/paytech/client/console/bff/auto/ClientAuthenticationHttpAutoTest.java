package com.paytech.client.console.bff.auto;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import com.paytech.client.console.bff.ClientConsoleBffApplication;
import static org.junit.jupiter.api.Assertions.*;

/** BFF 真实 HTTP 调用 Platform 协议替身，验证 Cookie 与身份边界。 */
@ActiveProfiles("auto")
@SpringBootTest(classes=ClientConsoleBffApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientAuthenticationHttpAutoTest {
    private static final String ORIGIN="https://client-console.local.test";
    private static final String RAW="abcdefghijklmnopqrstuv.abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final HttpServer OWNER=startOwner();
    private static final AtomicInteger CALLS=new AtomicInteger();
    private static final AtomicInteger OWNER_FAILURE=new AtomicInteger();
    @LocalServerPort int port;
    @LocalManagementPort int managementPort;
    RestTestClient http;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("client.platform-test-base-url",()->"http://127.0.0.1:"+OWNER.getAddress().getPort()+"/platform-support");
    }
    @BeforeEach void prepare() { OWNER_FAILURE.set(0); http=RestTestClient.bindToServer().baseUrl("http://127.0.0.1:"+port).build(); }
    @AfterAll static void stopOwner() { OWNER.stop(0); }

    /** 未启用入驻开关时不能暴露新增业务入口。 */
    @Test void onboardingRemainsDisabledByDefault() {
        int before=CALLS.get();
        http.get().uri("/client-onboarding/v1/start").exchange().expectStatus().isNotFound();
        http.get().uri("/onboarding-files/v1/files/1").exchange().expectStatus().isNotFound();
        assertEquals(before,CALLS.get());
    }
    @Test void loginStoresRefreshOnlyInHostCookie() {
        var result=http.post().uri("/client-auth/v1/login").header("Origin",ORIGIN)
            .body(java.util.Map.of("email","test@example.test","password","a sufficiently long test password"))
            .exchange().expectStatus().isOk().expectHeader().valueEquals("Cache-Control","no-store")
            .expectBody(String.class).returnResult();
        String cookie=result.getResponseHeaders().getFirst("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("__Secure-client-refresh="+RAW));
        for(String property:java.util.List.of("Path=/api/client/auth/v1","Secure","HttpOnly","SameSite=Lax")) {
            assertTrue(cookie.contains(property),property);
        }
        assertFalse(cookie.contains("Domain="));
        assertFalse(cookie.contains("Expires="), "Gateway 的固定 Cookie 合同只接受 Max-Age");
        assertEquals(6, cookie.split(";").length);
        assertFalse(result.getResponseBody().contains(RAW));
        assertFalse(result.getResponseBody().contains("refreshToken"));
        assertTrue(result.getResponseBody().contains("accessToken"));
    }
    @Test void writesRejectWrongOrMissingOriginBeforeOwner() {
        int before=CALLS.get();
        for(String origin:java.util.List.of("https://evil.example","null")) {
            http.post().uri("/client-auth/v1/login").header("Origin",origin).body(java.util.Map.of("email","test@example.test","password","long password"))
                .exchange().expectStatus().isForbidden();
        }
        assertEquals(before,CALLS.get());
    }
    @Test void refreshTakesCookieAndRejectsBodyTokens() {
        http.post().uri("/client-auth/v1/refresh").header("Origin",ORIGIN)
            .header("Cookie","__Secure-client-refresh="+RAW).body(java.util.Map.of())
            .exchange().expectStatus().isOk();
        int before=CALLS.get();
        http.post().uri("/client-auth/v1/refresh").header("Origin",ORIGIN)
            .header("Cookie","__Secure-client-refresh="+RAW).body(java.util.Map.of("refreshToken",RAW))
            .exchange().expectStatus().isEqualTo(422);
        http.post().uri("/client-auth/v1/refresh").header("Origin",ORIGIN).body(java.util.Map.of())
            .exchange().expectStatus().isUnauthorized();
        assertEquals(before,CALLS.get());
    }
    @Test void protectedContextRejectsForeignChannel() {
        http.get().uri("/client-auth/v1/context").header("X-Gateway-Realm","OPERATIONS")
            .header("X-Gateway-Channel","OPERATIONS").header("X-Gateway-Actor-Subject","1").header("X-Gateway-Session-Id","2")
            .exchange().expectStatus().isUnauthorized();
    }

    @Test void listenersKeepBusinessAndManagementRoutesSeparate() {
        http.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
        var management=RestTestClient.bindToServer().baseUrl("http://127.0.0.1:"+managementPort).build();
        management.get().uri("/actuator/health").exchange().expectStatus().isOk();
        int before=CALLS.get();
        management.post().uri("/client-auth/v1/login").header("Origin",ORIGIN)
            .body(java.util.Map.of("email","test@example.test","password","a long test password"))
            .exchange().expectStatus().isNotFound();
        assertEquals(before,CALLS.get());
    }

    @Test void duplicateRefreshCookiesAreRejectedAndCleared() {
        int before=CALLS.get();
        var result=http.post().uri("/client-auth/v1/refresh").header("Origin",ORIGIN)
            .header("Cookie","__Secure-client-refresh="+RAW+"; __Secure-client-refresh="+RAW)
            .body(java.util.Map.of()).exchange().expectStatus().isUnauthorized().expectBody().returnResult();
        assertEquals("__Secure-client-refresh=; Path=/api/client/auth/v1; Max-Age=0; Secure; HttpOnly; SameSite=Lax",
            result.getResponseHeaders().getFirst("Set-Cookie"));
        assertEquals(before,CALLS.get());
    }

    @Test void registrationRejectsUnknownFieldsAndMissingOriginBeforeOwner() {
        int before=CALLS.get();
        http.post().uri("/client-auth/v1/register").header("Origin",ORIGIN)
            .body(java.util.Map.of("email","test@example.test","password","a long test password","realm","OPERATIONS"))
            .exchange().expectStatus().isBadRequest();
        http.post().uri("/client-auth/v1/register")
            .body(java.util.Map.of("email","test@example.test","password","a long test password"))
            .exchange().expectStatus().isForbidden();
        assertEquals(before,CALLS.get());
    }

    @Test void ownerSessionFailureClearsCookieButOutageDoesNot() {
        for (int status : java.util.List.of(401, 503)) {
            OWNER_FAILURE.set(status);
            var result=http.post().uri("/client-auth/v1/refresh").header("Origin",ORIGIN)
                .header("Cookie","__Secure-client-refresh="+RAW).body(java.util.Map.of())
                .exchange().expectStatus().isEqualTo(status)
                .expectHeader().valueEquals("Cache-Control","no-store")
                .expectHeader().valueEquals("Referrer-Policy","no-referrer")
                .expectBody(String.class).returnResult();
            String cookie=result.getResponseHeaders().getFirst("Set-Cookie");
            if (status==401) { assertNotNull(cookie); assertTrue(cookie.contains("Max-Age=0")); }
            else { assertNull(cookie); }
            assertFalse(result.getResponseBody().contains(RAW));
        }
    }

    @Test void publicEmailRequestsPreserveAcceptedAndNeverIssueSessionCookies() {
        for (String path : java.util.List.of("register","email-verification-requests","password-reset-requests")) {
            Object input=path.equals("register") ? java.util.Map.of("email","test@example.test","password","a long test password")
                : java.util.Map.of("email","test@example.test");
            http.post().uri("/client-auth/v1/"+path).header("Origin",ORIGIN).body(input)
                .exchange().expectStatus().isEqualTo(202).expectHeader().doesNotExist("Set-Cookie")
                .expectBody().json("{\"accepted\":true,\"resendAfterSeconds\":60}");
        }
    }

    @Test void logoutUsesTrustedAccountAndClearsCookieOnlyAfterOwner() {
        http.post().uri("/client-auth/v1/logout").header("Origin",ORIGIN)
            .header("Cookie","__Secure-client-refresh="+RAW)
            .header("X-Gateway-Realm","CUSTOMER").header("X-Gateway-Channel","CLIENT")
            .header("X-Gateway-Actor-Type","PLATFORM_USER").header("X-Gateway-Context-Type","ACCOUNT")
            .header("X-Gateway-Actor-Subject","1").header("X-Gateway-Session-Id","2")
            .body(java.util.Map.of()).exchange().expectStatus().isNoContent()
            .expectHeader().value("Set-Cookie",cookie -> assertTrue(cookie.contains("Max-Age=0")));
    }

    /** 最小 Owner 仅提供 HTTP 合同；本测试不替代 Platform 持久化用例。 */
    private static HttpServer startOwner() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/platform-support/authentication/v1/client",exchange->{
                CALLS.incrementAndGet();
                String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                String path=exchange.getRequestURI().getPath();
                int status=200;
                String body;
                if(path.endsWith("/login") || path.endsWith("/refresh")) {
                    if(path.endsWith("/refresh") && !input.contains(RAW)) { status=400; }
                    body="{\"context\":{\"accountId\":\"1\",\"sessionId\":\"2\",\"emailMasked\":\"t***@example.test\",\"emailVerified\":true,\"contextType\":\"ACCOUNT\",\"tenantId\":null,\"memberId\":null,\"allowedActions\":[\"ONBOARDING\",\"LOGOUT\"]},\"accessToken\":\"fixture-access\",\"expiresAt\":\"2099-01-01T00:00:00.000000Z\",\"refreshToken\":\""+RAW+"\",\"refreshExpiresAt\":\"2099-01-08T00:00:00.000000Z\"}";
                } else if(path.endsWith("/logout")) {
                    assertEquals("1",exchange.getRequestHeaders().getFirst("X-Gateway-Actor-Subject"));
                    assertEquals("2",exchange.getRequestHeaders().getFirst("X-Gateway-Session-Id"));
                    exchange.sendResponseHeaders(204,-1); exchange.close(); return;
                } else { status=202; body="{\"accepted\":true,\"resendAfterSeconds\":60}"; }
                if (OWNER_FAILURE.get()!=0) {
                    status=OWNER_FAILURE.get();
                    String code=status==401?"CLIENT_SESSION_INVALID":"SERVICE_UNAVAILABLE";
                    body="{\"type\":\"about:blank\",\"title\":\""+(status==401?"Unauthorized":"Service Unavailable")
                        +"\",\"detail\":\"fixture failure\",\"code\":\""+code
                        +"\",\"requestId\":\"client-owner-fixture\",\"timestamp\":\"2026-09-15T00:00:00.000000Z\"}";
                }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",status>=400?"application/problem+json":"application/json");
                if(status>=400) exchange.getResponseHeaders().set("X-Request-Id","client-owner-fixture");
                exchange.sendResponseHeaders(status,bytes.length);
                try(var output=exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start(); return server;
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
}
