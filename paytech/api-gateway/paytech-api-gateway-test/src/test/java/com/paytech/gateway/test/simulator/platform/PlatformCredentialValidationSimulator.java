package com.paytech.gateway.test.simulator.platform;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.springframework.http.HttpStatus;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi;
import com.paytech.platform.support.api.apicredential.response.ValidatedCredentialResponseBody;
import com.paytech.platform.support.api.enums.ApiCredentialErrorCode;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import tools.jackson.databind.json.JsonMapper;

/** 使用 Owner 合同的随机端口协议 Simulator；Journal 只保存次数和匹配结果。 */
public final class PlatformCredentialValidationSimulator implements AutoCloseable {
    public static final String CREDENTIAL = "testkey1." + "credential-canary-".repeat(3);
    public static final String TENANT = "810000001";
    public static final String CLIENT = "820000001";
    public static final String CREDENTIAL_ID = "830000001";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Deque<Scenario> scenarios = new ArrayDeque<>();
    private final AtomicInteger calls = new AtomicInteger();
    private int unexpectedCalls;
    private DisposableServer server;

    public synchronized void start() {
        if (server == null) {
            server = HttpServer.create().host("127.0.0.1").port(0).handle(this::handle).bindNow();
        }
    }

    public URI uri() {
        start();
        return URI.create("http://127.0.0.1:" + server.port());
    }

    public void expectValid(String... scopes) {
        expectValidIdentity(CLIENT, CREDENTIAL_ID, scopes);
    }

    /** 同一 Bearer 下由 Owner 返回指定 Client / 凭证，用于凭证轮换与多 Client 隔离场景。 */
    public void expectValidIdentity(String client, String credentialId, String... scopes) {
        expectRaw(200, JSON.writeValueAsString(new ValidatedCredentialResponseBody(TENANT, "CLIENT", client,
            credentialId, List.of(scopes))), Duration.ZERO, false);
    }

    public synchronized void expectRaw(int status, String body, Duration delay, boolean disconnect) {
        scenarios.addLast(new Scenario(status, body, delay, disconnect,
            List.of(status >= 400 ? ApiProblemProfile.CONTENT_TYPE : "application/json")));
    }

    public synchronized void expectMedia(String body, List<String> contentTypes) {
        scenarios.addLast(new Scenario(200, body, Duration.ZERO, false, List.copyOf(contentTypes)));
    }

    public void expectInvalid() {
        expectRaw(401, null, Duration.ZERO, false);
    }

    public int count() {
        return calls.get();
    }

    public synchronized void verifyComplete() {
        Assertions.assertEquals(0, unexpectedCalls, "PLT-I03 收到未声明或不符合合同的请求");
        Assertions.assertEquals(0, scenarios.size(), "PLT-I03 存在未消费场景");
    }

    public synchronized void reset() {
        scenarios.clear();
        calls.set(0);
        unexpectedCalls = 0;
    }

    public static String contractPath() {
        try {
            return "/platform-support" + ApiCredentialValidationApi.class.getAnnotation(HttpExchange.class).value()
                + ApiCredentialValidationApi.class.getMethod("validate", String.class)
                    .getAnnotation(PostExchange.class).value();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("PLT-I03 发布合同缺失", exception);
        }
    }

    private Mono<Void> handle(HttpServerRequest request, HttpServerResponse response) {
        Scenario scenario = consume(request);
        if (scenario == null) {
            return response.status(500).send().then();
        }
        return request.receive().map(buffer -> buffer.readableBytes()).reduce(0, Integer::sum).flatMap(bytes -> {
            if (bytes != 0) {
                synchronized (this) {
                    unexpectedCalls++;
                }
            }
            if (scenario.disconnect()) {
                response.withConnection(connection -> connection.dispose());
                return Mono.empty();
            }
            String requestId = request.requestHeaders().get(ApiProblemProfile.REQUEST_ID_HEADER);
            String body = scenario.body();
            if (body == null && scenario.status() == 401) {
                body = JSON.writeValueAsString(new ApiProblemResponse(ApiProblemProfile.TYPE,
                    HttpStatus.UNAUTHORIZED.getReasonPhrase(), "API Credential 无效",
                    ApiCredentialErrorCode.API_CREDENTIAL_AUTHENTICATION_FAILED.getCode(), requestId,
                    ApiProblemProfile.formatTimestamp(Instant.now()), null));
            }
            if (requestId != null) {
                response.header(ApiProblemProfile.REQUEST_ID_HEADER, requestId);
            }
            scenario.contentTypes().forEach(value -> response.addHeader("Content-Type", value));
            Mono<Void> delay = scenario.delay().isZero() ? Mono.empty() : Mono.delay(scenario.delay()).then();
            return delay.then(response.status(scenario.status()).sendString(Mono.just(body == null ? "" : body)).then());
        });
    }

    private synchronized Scenario consume(HttpServerRequest request) {
        calls.incrementAndGet();
        boolean matches = "POST".equals(request.method().name()) && contractPath().equals(request.uri())
            && request.requestHeaders().getAll(ApiCredentialConstraints.CREDENTIAL_HEADER).equals(List.of(CREDENTIAL))
            && !request.requestHeaders().contains("Authorization")
            && ApiProblemProfile.isValidRequestId(request.requestHeaders().get(ApiProblemProfile.REQUEST_ID_HEADER));
        if (!matches || scenarios.isEmpty()) {
            unexpectedCalls++;
            return null;
        }
        return scenarios.removeFirst();
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.disposeNow();
            server = null;
        }
        reset();
    }

    private record Scenario(int status, String body, Duration delay, boolean disconnect, List<String> contentTypes) {
        @Override
        public String toString() {
            return "CredentialScenario[status=" + status + "]";
        }
    }
}
