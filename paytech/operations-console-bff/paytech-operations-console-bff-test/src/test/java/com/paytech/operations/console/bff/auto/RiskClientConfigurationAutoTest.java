package com.paytech.operations.console.bff.auto;

import com.paytech.risk.decision.api.request.*;
import com.paytech.risk.decision.api.response.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.configuration.RiskClientConfiguration;
import com.paytech.operations.console.bff.configuration.RiskOwnerResponseHttpMessageConverter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.RiskDiscoveryInterceptor;
import com.paytech.risk.decision.api.DecisionReleaseManagementApi;
import com.paytech.risk.decision.api.DecisionRuleSetManagementApi;
import com.paytech.risk.decision.api.DecisionReleaseManagementModels;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels;
import com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode;

/** 通过真实代理及 Loopback HTTP 覆盖成功解码和独立发现，不启动业务服务。 */
class RiskClientConfigurationAutoTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String HASH = "a".repeat(64);
    private static final String TIME = "2026-09-07T01:02:03.000000Z";
    private static final String REQUEST_ID = "risk-client-request-1";
    private static final String ACTOR = "10001";
    private static final String SESSION = "session-10001";
    private static final String OWNER_PACKAGE = "com/paytech/risk/decision/api/";
    private static final String COMPLIANCE_OWNER_PACKAGE = "com/paytech/compliance/api/";
    private static final String BFF_PACKAGE = "com/paytech/operations/console/bff/";

    @Test
    void test_missingDiscoveryIsSafeUnavailableWithoutRetry() {
        var discovery = new EmptyDiscovery();
        context(discovery).run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(DecisionReleaseManagementApi.class));
            var failure = assertThrows(ServiceUnavailableException.class, () -> context
                .getBean(DecisionRuleSetManagementApi.class).getAuthoringContext(ACTOR, SESSION, "CHECKOUT_PREPARE"));
            assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, failure.getErrorCode());
            assertInstanceOf(ResourceAccessException.class, failure.getCause());
            assertEquals(List.of("risk-decision-service"), discovery.choices);
            assertFalse(failure.getMessage().contains("risk-decision-service"));
            assertFalse(failure.getMessage().contains(SESSION));
        });
    }

    @ParameterizedTest
    @MethodSource("successCases")
    void test_exactSuccessTypesRetainWireDataAndRequestHeaders(SuccessCase sample) throws Exception {
        try (var server = new ProtocolServer(sample)) {
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    try (var ignored = MDC.putCloseable(RequestIdFilter.MDC_KEY, REQUEST_ID)) {
                        Object response = sample.invoke(context.getBean(DecisionRuleSetManagementApi.class),
                            context.getBean(DecisionReleaseManagementApi.class));
                        assertEquals(JSON.readTree(sample.json()), JSON.readTree(JSON.writeValueAsString(response)));
                    }
                    assertEquals(1, server.calls.get());
                    assertEquals(REQUEST_ID, server.requestId);
                    assertEquals(ACTOR, server.actor);
                    assertEquals(SESSION, server.session);
                    if (!sample.method().equals("GET")) {
                        assertNull(server.idempotencyKey);
                        assertTrue(JSON.readTree(server.requestBody).isObject());
                    }
                    assertEquals(List.of(), server.unexpected);
                });
        }
    }

    @ParameterizedTest
    @MethodSource("successCases")
    void test_allPropertiesRequireExactShapeBeforeBinding(SuccessCase sample) throws Exception {
        List<String> bodies = new ArrayList<>();
        bodies.addAll(List.of("[]", "null", "true", "1", "\"payload-canary\""));
        bodies.add(sample.json() + " {}");
        bodies.add("{\"privateUnknown\":\"payload-canary\"," + sample.json().substring(1));
        String first = JSON.readTree(sample.json()).propertyNames().iterator().next();
        bodies.add("{\"" + first + "\":null," + sample.json().substring(1));
        Set<String> nullablePaths = switch (sample.name()) {
            case "ruleList" -> Set.of("/items/0/pointVersionId");
            case "ruleDetail", "createRule", "freezeRule" -> Set.of("/pointVersionId");
            default -> Set.of();
        };
        collectInvalidProperties(JSON.readTree(sample.json()), JSON.readTree(sample.json()), "", bodies, nullablePaths);
        try (var server = new ProtocolServer(sample)) {
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url())
                .run(context -> {
                    for (String body : bodies) {
                        server.response = body;
                        ErrorException failure = assertThrows(ErrorException.class, () -> sample.invoke(
                            context.getBean(DecisionRuleSetManagementApi.class),
                            context.getBean(DecisionReleaseManagementApi.class)), sample.name() + " invalid fixture: " + body);
                        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, failure.getErrorCode());
                        assertNotNull(failure.getCause());
                        assertSafeFailure(failure);
                        assertFalse(failure.getMessage().contains("payload-canary"));
                        assertFalse(failure.getMessage().contains(server.url()));
                    }
                    assertEquals(bodies.size(), server.calls.get(), "每个失败只能发出一次请求");
                    assertEquals(List.of(), server.unexpected);
                    System.out.println("Risk strict success " + sample.name() + ": " + bodies.size() + " invalid bodies rejected");
                });
        }
    }

    @Test
    void test_converterReadsOnlyExactSuccessTypesAndNeverWrites() {
        var converter = new RiskOwnerResponseHttpMessageConverter();
        List<Type> allowed = successCases().map(SuccessCase::type).toList();
        assertEquals(10, Set.copyOf(allowed).size());
        for (Type type : allowed) {
            var resolved = ResolvableType.forType(type);
            assertTrue(converter.canRead(resolved, MediaType.APPLICATION_JSON));
            assertTrue(converter.canRead(resolved, null));
            assertFalse(converter.canRead(resolved, MediaType.APPLICATION_PROBLEM_JSON));
            assertFalse(converter.canRead(resolved, MediaType.TEXT_PLAIN));
            assertFalse(converter.canWrite(resolved, resolved.resolve(), MediaType.APPLICATION_JSON));
            assertFalse(converter.canWrite(resolved.resolve(), MediaType.APPLICATION_JSON));
        }
        List<Type> rejected = new ArrayList<>(List.of(PagingResponseBody.class, Object.class, String.class,
            DecisionRuleSetManagementModels.RuleRequestBody.class, RuleSetListItemResponseBody.class, TestReleaseListItemResponseBody.class,
            new ParameterizedTypeReference<PagingResponseBody<String>>() {}.getType(),
            new ParameterizedTypeReference<PagingResponseBody<? extends RuleSetListItemResponseBody>>() {}.getType()));
        for (Class<?> container : List.of(DecisionRuleSetManagementModels.class, DecisionReleaseManagementModels.class)) {
            Arrays.stream(container.getDeclaredClasses()).filter(type -> !allowed.contains(type)).forEach(rejected::add);
        }
        rejected.forEach(type -> assertFalse(converter.canRead(ResolvableType.forType(type), MediaType.APPLICATION_JSON)));
        assertFalse(converter.canRead(PagingResponseBody.class, MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:1234", "https://127.0.0.1:1234", "http://127.0.0.1:0",
        "http://127.0.0.1:65536", "http://127.0.0.1", "http://127.0.0.1:1234/path",
        "http://127.0.0.1:1234?debug=true", "http://127.0.0.1:1234#fragment", "http://user@127.0.0.1:1234",
        "http://example.invalid:1234", "not a URI"})
    void test_invalidTestAddressFailsStartupWithoutContactingAnything(String address) {
        var discovery = new EmptyDiscovery();
        context(discovery).withPropertyValues("operations.risk-test-base-url=" + address).run(context -> {
            assertNotNull(context.getStartupFailure());
            assertEquals(List.of(), discovery.choices);
        });
    }

    @Test
    void test_testAddressRequiresAutoProfile() {
        context(new EmptyDiscovery()).withPropertyValues("spring.profiles.active=prod",
            "operations.risk-test-base-url=http://127.0.0.1:1234").run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void test_defaultJacksonCannotBypassStrictSuccessReaderForProblemMediaType() throws Exception {
        var sample = successCases().filter(value -> value.name().equals("authoring")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            server.contentType = "application/problem+json";
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                assertInvalid(context.getBean(DecisionRuleSetManagementApi.class));
                assertEquals(1, server.calls.get());
            });
        }
    }

    @Test
    void test_strictProblemContractRegistersOnlyRiskErrorsAndRejectsLegacyAndRedirects() throws Exception {
        SuccessCase sample = successCases().filter(value -> value.name().equals("authoring")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                var api = context.getBean(DecisionRuleSetManagementApi.class);
                server.status = 404;
                server.contentType = "application/problem+json";
                server.response = problem("Not Found", "RISK_DECISION_CONFIGURATION_NOT_FOUND");
                var missing = assertThrows(FailException.class, () -> api.getAuthoringContext(ACTOR, SESSION, "CHECKOUT_PREPARE"));
                assertEquals(RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND, missing.getErrorCode());
                assertFalse(missing.getMessage().contains("payload-canary"));
                server.response = problem("Not Found", "GATEWAY_CONFIGURATION_NOT_FOUND");
                assertInvalid(api);
                server.contentType = "application/json";
                server.response = "{\"code\":\"INVALID_REQUEST\",\"message\":\"payload-canary\","
                    + "\"requestId\":\"" + REQUEST_ID + "\",\"timestamp\":\"" + TIME + "\"}";
                assertInvalid(api);
                server.status = 302;
                server.response = "{}";
                assertInvalid(api);
                assertEquals(4, server.calls.get());
                assertEquals(List.of(), server.unexpected);
            });
        }
    }

    @ParameterizedTest
    @CsvSource({"500,Internal Server Error,DEPENDENCY_UNAVAILABLE", "503,Service Unavailable,DEPENDENCY_UNAVAILABLE",
        "504,Gateway Timeout,DEPENDENCY_TIMEOUT"})
    void test_strictUpstreamServerErrorsKeepSafeDependencyClassification(int status, String title, String code) throws Exception {
        var sample = successCases().filter(value -> value.name().equals("authoring")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            server.status = status;
            server.contentType = "application/problem+json";
            server.response = problem(title, "INTERNAL_ERROR");
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                var error = assertThrows(ErrorException.class, () -> sample.invoke(context.getBean(DecisionRuleSetManagementApi.class),
                    context.getBean(DecisionReleaseManagementApi.class)));
                assertEquals(code, error.getCode());
                assertNotNull(error.getCause());
                assertFalse(error.getMessage().contains("payload-canary"));
                assertEquals(1, server.calls.get());
            });
        }
    }

    @Test
    void test_actualReadTimeoutMapsToDependencyTimeoutWithoutRetry() throws Exception {
        var sample = successCases().filter(value -> value.name().equals("createCandidate")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            server.responseGate = new CountDownLatch(1);
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                long started = System.nanoTime();
                var error = assertThrows(ErrorException.class, () -> sample.invoke(context.getBean(DecisionRuleSetManagementApi.class),
                    context.getBean(DecisionReleaseManagementApi.class)));
                Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
                assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, error.getErrorCode());
                assertInstanceOf(ResourceAccessException.class, error.getCause());
                assertTrue(hasReadTimeoutCause(error));
                assertTrue(elapsed.compareTo(Duration.ofSeconds(3)) >= 0);
                assertTrue(elapsed.compareTo(Duration.ofSeconds(7)) < 0);
                assertEquals(1, server.calls.get());
            });
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void test_partialBodyReadTimeoutKeepsTransportCauseWithoutRetry(int prefixLength) throws Exception {
        var sample = successCases().filter(value -> value.name().equals("createCandidate")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            server.partialBodyGate = new CountDownLatch(1);
            server.prefixLength = prefixLength;
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                long started = System.nanoTime();
                var error = assertThrows(ErrorException.class, () -> sample.invoke(context.getBean(DecisionRuleSetManagementApi.class),
                    context.getBean(DecisionReleaseManagementApi.class)));
                Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
                assertAll(
                    () -> assertEquals(0, server.prefixSent.getCount(), "200 Headers 和指定长度的 JSON 前缀已经发出"),
                    () -> assertEquals(1, server.partialBodyGate.getCount(), "剩余响应体仍等待测试释放"),
                    () -> assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, error.getErrorCode()),
                    () -> assertInstanceOf(ResourceAccessException.class, error.getCause()),
                    () -> assertTrue(hasReadTimeoutCause(error), "底层读取超时类型必须保留"),
                    () -> assertTrue(elapsed.compareTo(Duration.ofSeconds(3)) >= 0, elapsed.toString()),
                    () -> assertTrue(elapsed.compareTo(Duration.ofSeconds(7)) < 0, elapsed.toString()),
                    () -> assertEquals(1, server.calls.get()),
                    () -> assertNull(server.idempotencyKey),
                    () -> assertEquals(List.of(), server.unexpected));
                assertSafeFailure(error);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void test_partialBodyDisconnectIsUnavailableWithoutRetry(int prefixLength) throws Exception {
        var sample = successCases().filter(value -> value.name().equals("createCandidate")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            server.closeAfterPrefix = true;
            server.prefixLength = prefixLength;
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                var error = assertThrows(ErrorException.class, () -> sample.invoke(context.getBean(DecisionRuleSetManagementApi.class),
                    context.getBean(DecisionReleaseManagementApi.class)));
                assertAll(
                    () -> assertEquals(0, server.prefixSent.getCount()),
                    () -> assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, error.getErrorCode()),
                    () -> assertInstanceOf(ServiceUnavailableException.class, error),
                    () -> assertInstanceOf(ResourceAccessException.class, error.getCause()),
                    () -> assertTrue(hasCause(error, IOException.class)),
                    () -> assertFalse(hasReadTimeoutCause(error)),
                    () -> assertEquals(1, server.calls.get()),
                    () -> assertNull(server.idempotencyKey),
                    () -> assertEquals(List.of(), server.unexpected));
                assertSafeFailure(error);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204})
    void test_emptySuccessStillFailsRequiredTypedResponse(int status) throws Exception {
        var sample = successCases().filter(value -> value.name().equals("authoring")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            server.status = status;
            server.response = "";
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                assertInvalid(context.getBean(DecisionRuleSetManagementApi.class));
                assertEquals(1, server.calls.get());
                assertEquals(List.of(), server.unexpected);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void test_commandRedirectsAreNeverFollowed(int status) throws Exception {
        var sample = successCases().filter(value -> value.name().equals("createCandidate")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            server.status = status;
            server.response = "{}";
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                var error = assertThrows(ErrorException.class, () -> sample.invoke(context.getBean(DecisionRuleSetManagementApi.class),
                    context.getBean(DecisionReleaseManagementApi.class)));
                assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, error.getErrorCode());
                assertEquals(1, server.calls.get());
                assertNull(server.idempotencyKey);
                assertEquals(List.of(), server.unexpected);
            });
        }
    }

    @Test
    void test_commandDisconnectBeforeHeadersDoesNotRetry() throws Exception {
        var sample = successCases().filter(value -> value.name().equals("createCandidate")).findFirst().orElseThrow();
        try (var server = new ProtocolServer(sample)) {
            server.closeBeforeHeaders = true;
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                var error = assertThrows(ServiceUnavailableException.class, () -> sample.invoke(context.getBean(DecisionRuleSetManagementApi.class),
                    context.getBean(DecisionReleaseManagementApi.class)));
                assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, error.getErrorCode());
                assertInstanceOf(ResourceAccessException.class, error.getCause());
                assertFalse(hasReadTimeoutCause(error));
                assertEquals(1, server.calls.get());
                assertNull(server.idempotencyKey);
                assertEquals(List.of(), server.unexpected);
            });
        }
    }

    @Test
    void test_jsonSyntaxFailuresStayInvalidAndNeverExposeDiagnostics() throws Exception {
        var sample = successCases().filter(value -> value.name().equals("authoring")).findFirst().orElseThrow();
        List<String> bodies = List.of(
            "{\"private-key-canary\":payload-canary}",
            "{\"private-key-canary\":\"payload-canary\",\"private-key-canary\":null}",
            "{\"private-key-canary\":\"payload-canary\"");
        try (var server = new ProtocolServer(sample)) {
            context(new EmptyDiscovery()).withPropertyValues("operations.risk-test-base-url=" + server.url()).run(context -> {
                for (String body : bodies) {
                    server.response = body;
                    var error = assertThrows(ErrorException.class, () -> sample.invoke(context.getBean(DecisionRuleSetManagementApi.class),
                        context.getBean(DecisionReleaseManagementApi.class)));
                    assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, error.getErrorCode());
                    assertFalse(hasCause(error, ResourceAccessException.class));
                    assertSafeFailure(error);
                }
                assertEquals(bodies.size(), server.calls.get());
                assertEquals(List.of(), server.unexpected);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_converterClosesPartialBodyAndPreservesOnlyOriginalIoCause(boolean timeout) {
        IOException transport = timeout ? new HttpTimeoutException("response body read timed out")
            : new IOException("connection closed before response body completed");
        var body = new PartialResponseStream(transport);
        var converter = new RiskOwnerResponseHttpMessageConverter();
        var error = assertThrows(ResourceAccessException.class, () -> converter.read(
            ResolvableType.forClass(RuleAuthoringContextResponseBody.class), new MockHttpInputMessage(body), Map.of()));
        assertSame(transport, error.getCause(), "只保留原始 I/O；不携带 Jackson 的原始 JSON 诊断");
        assertTrue(body.closed, "读取异常后仍必须关闭已创建 Parser 的响应流");
        assertSafeFailure(error);
    }

    @Test
    void test_productionOwnerImportsAndFieldAccessRemainInsideApprovedBoundary() throws Exception {
        Path web = appModule();
        String adapter = BFF_PACKAGE + "integration/impl/RiskDecisionConfigurationClientImpl";
        Set<String> adapters = Set.of(adapter, BFF_PACKAGE + "integration/impl/RiskFlowClientImpl", BFF_PACKAGE + "integration/impl/RiskFlowConverter", BFF_PACKAGE + "integration/impl/RiskDictionaryClientImpl",
            BFF_PACKAGE + "integration/impl/RiskConfigurationQueryClientImpl", BFF_PACKAGE + "integration/impl/RiskConfigurationConverter", BFF_PACKAGE + "integration/impl/RiskVariableClientImpl", BFF_PACKAGE + "integration/impl/RiskVariableConverter", BFF_PACKAGE + "integration/impl/RiskOperandConverter", BFF_PACKAGE + "integration/impl/RiskVariableResponsePolicy", BFF_PACKAGE + "integration/impl/ClientReviewRiskClientImpl",
            BFF_PACKAGE + "integration/impl/RiskListClientImpl", BFF_PACKAGE + "integration/impl/RiskExecutionReleaseClientImpl", BFF_PACKAGE + "integration/impl/RiskControlConverter",
            BFF_PACKAGE + "integration/impl/RiskEvaluationClientImpl", BFF_PACKAGE + "integration/impl/RiskEvaluationConverter");
        // Client 入驻审核为简单转发：端口、页面模型与 Controller 只引用 Risk 审核响应 DTO，不调用 Owner 方法。
        Set<String> reviewPassThrough = Set.of(BFF_PACKAGE + "integration/ClientReviewRiskClient",
            BFF_PACKAGE + "service/model/ClientReviewModels", BFF_PACKAGE + "ext/rest/ClientOnboardingReviewController");
        String reviewConfiguration = BFF_PACKAGE + "configuration/ClientReviewClientConfiguration";
        // compliance 管理合同的直接消费者：块 137 主档 / 资格与 2026-10-27 案件工作台按 Owner 请求体转发、按 Owner 响应读取（Risk 决策合同的限制不变）。
        Set<String> complianceConsumers = Set.of("configuration/EntityQualificationClientConfiguration", "domain/enums/QualificationSubjectTerminalStatus",
            "ext/rest/ClientOnboardingReviewController", "ext/rest/QualificationAdminController", "integration/ClientReviewRiskClient",
            "integration/EntityAdminClient", "integration/QualificationAdminClient", "integration/impl/EntityAdminClientImpl",
            "integration/impl/QualificationAdminClientImpl", "service/model/entityadmin/response/LocalPartnerPageResponseBody",
            "service/model/entityadmin/response/OperatingEntityPageResponseBody", "service/model/qualificationadmin/response/QualificationListItemResponseBody",
            "service/processor/ClientReviewCommandProcessor", "service/processor/LocalPartnerDetailsProcessor",
            "service/processor/OperatingEntityDetailsProcessor", "service/processor/QualificationListProcessor",
            "service/processor/RecordManualQualificationProcessor", "service/processor/request/ClientReviewCommandRequest",
            "service/processor/request/RecordManualQualificationRequest", "service/processor/ClientReviewDetailsProcessor",
            "service/processor/ClientReviewFileDownloadProcessor", "configuration/OperationsErrorStatusResolver",
            "service/processor/ClientDetailsProcessor", "service/model/clientadmin/response/ClientPageResponseBody",
            "ext/rest/OnboardingConfigAdminController", "service/processor/UnderwritingInputAssembler",
            "service/processor/LimitTierChecklistAssembler", "service/processor/LimitTierChecklistReading").stream()
            .map(name -> BFF_PACKAGE + name).collect(java.util.stream.Collectors.toSet());
        String configuration = BFF_PACKAGE + "configuration/RiskClientConfiguration";
        String resolver = BFF_PACKAGE + "configuration/OperationsErrorStatusResolver";
        // REQ-03 承保（2026-10-29）：工作台聚合与命令转发按 Owner 承保合同组装请求体、读取响应（同 compliance 审核消费者），
        // 只允许 control 包内的承保 DTO / API 与 RiskUnderwritingErrorCode；名单等其他 Risk 合同的限制不变。
        java.util.function.Predicate<String> underwritingConsumer = name -> name.startsWith(BFF_PACKAGE + "service/model/riskunderwriting/")
            || Set.of("configuration/RiskUnderwritingRequestConfiguration", "configuration/OperationsErrorStatusResolver",
                "configuration/RiskClientConfiguration", "integration/RiskUnderwritingClient", "integration/impl/RiskUnderwritingClientImpl",
                "ext/rest/RiskUnderwritingController", "service/processor/UnderwritingInputAssembler",
                "service/processor/UnderwritingLabeler", "service/processor/UnderwritingWorkspaceProcessor",
                "service/processor/UnderwritingCommandProcessor", "service/processor/UnderwritingPreviewProcessor",
                "service/processor/UnderwritingListProcessor", "service/processor/UnderwritingDetailProcessor",
                "service/processor/UnderwritingHistoryProcessor", "service/processor/request/UnderwritingCommandRequest")
                .stream().map(file -> BFF_PACKAGE + file).anyMatch(name::equals);
        java.util.function.Predicate<String> underwritingOwner = owner -> owner.equals(OWNER_PACKAGE + "enums/RiskUnderwritingErrorCode")
            || owner.startsWith(OWNER_PACKAGE + "control/") && owner.contains("Underwriting");
        // REQ-04 限额档位（2026-10-30）：工作台聚合、申请转发与命令回执按 Owner 档位合同组装请求体、读取响应，
        // 复核清单另读取 REQ-03 当前承保；只允许 control 包内的档位 / 承保 DTO 与 API 及 RiskLimitTierErrorCode。
        java.util.function.Predicate<String> limitTierConsumer = name -> name.startsWith(BFF_PACKAGE + "service/model/risklimittier/")
            || Set.of("configuration/RiskUnderwritingRequestConfiguration", "configuration/OperationsErrorStatusResolver",
                "configuration/RiskClientConfiguration", "integration/RiskLimitTierClient", "integration/impl/RiskLimitTierClientImpl",
                "ext/rest/RiskLimitTierController", "service/processor/LimitTierLabeler", "service/processor/LimitTierListProcessor",
                "service/processor/LimitTierHistoryProcessor", "service/processor/LimitTierWorkspaceProcessor",
                "service/processor/LimitTierRequestProcessor", "service/processor/LimitTierChangeCommandProcessor",
                "service/processor/LimitTierChecklistAssembler", "service/processor/LimitTierChecklistReading",
                "service/processor/request/LimitTierChangeCommandRequest")
                .stream().map(file -> BFF_PACKAGE + file).anyMatch(name::equals);
        java.util.function.Predicate<String> limitTierOwner = owner -> owner.equals(OWNER_PACKAGE + "enums/RiskLimitTierErrorCode")
            || owner.startsWith(OWNER_PACKAGE + "control/") && (owner.contains("Tier") || owner.contains("Underwriting"));
        Map<String, Set<String>> restricted = Map.of(
            resolver, Set.of("com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode", "com.paytech.risk.decision.api.enums.RiskVariableErrorCode"));
        Set<String> transport = Set.of(configuration, BFF_PACKAGE + "configuration/RiskOwnerResponseHttpMessageConverter",
            BFF_PACKAGE + "configuration/RiskOwnerResponseShapeValidator", BFF_PACKAGE + "configuration/RiskVariableResponseShape");
        var imports = Pattern.compile("(?m)^import\\s+(?:static\\s+)?(com\\.paytech\\.(?:risk\\.decision|compliance)\\.api\\.[^;]+);");
        try (var sources = Files.walk(web.resolve("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = web.resolve("src/main/java").relativize(source).toString().replace(".java", "");
                var matcher = imports.matcher(Files.readString(source));
                while (matcher.find()) {
                    assertTrue(transport.contains(name) || adapters.contains(name)
                        || underwritingConsumer.test(name) && underwritingOwner.test(matcher.group(1).replace('.', '/'))
                        || limitTierConsumer.test(name) && limitTierOwner.test(matcher.group(1).replace('.', '/'))
                        || restricted.getOrDefault(name, Set.of()).contains(matcher.group(1))
                        || reviewPassThrough.contains(name) && matcher.group(1).startsWith("com.paytech.compliance.api.response.")
                        || complianceConsumers.contains(name) && matcher.group(1).startsWith("com.paytech.compliance.api.")
                        || name.equals(reviewConfiguration) && Set.of("com.paytech.compliance.api.ClientReviewAdminApi",
                            "com.paytech.compliance.api.enums.ClientReviewErrorCode").contains(matcher.group(1)), source.toString());
                }
            }
        }
        try (var classes = Files.walk(web.resolve("target/classes"))) {
            for (Path file : classes.filter(path -> path.toString().endsWith(".class")).toList()) {
                new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                    private String caller;
                    @Override
                    public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                        caller = name.split("\\$")[0];
                    }
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                                if (!owner.startsWith(OWNER_PACKAGE) && !owner.startsWith(COMPLIANCE_OWNER_PACKAGE)) return;
                                boolean errorEnum = owner.equals(OWNER_PACKAGE + "enums/RiskDecisionConfigurationErrorCode") || owner.equals(OWNER_PACKAGE + "enums/RiskVariableErrorCode");
                                boolean reviewEnum = owner.equals(COMPLIANCE_OWNER_PACKAGE + "enums/ClientReviewErrorCode");
                                assertTrue(adapters.contains(caller)
                                    || (underwritingConsumer.test(caller) && underwritingOwner.test(owner))
                                    || (limitTierConsumer.test(caller) && limitTierOwner.test(owner))
                                    || (complianceConsumers.contains(caller) && owner.startsWith(COMPLIANCE_OWNER_PACKAGE))
                                    || (caller.equals(configuration) && errorEnum && method.equals("values"))
                                    || (caller.equals(reviewConfiguration) && reviewEnum && method.equals("values"))
                                    || (caller.equals(resolver) && reviewEnum)
                                    || (caller.equals(resolver) && errorEnum),
                                    caller + " invokes Owner " + owner + "." + method);
                            }
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                                if (!owner.startsWith(OWNER_PACKAGE) && !owner.startsWith(COMPLIANCE_OWNER_PACKAGE)) return;
                                assertTrue(adapters.contains(caller) || (complianceConsumers.contains(caller) && owner.startsWith(COMPLIANCE_OWNER_PACKAGE))
                                    || (underwritingConsumer.test(caller) && underwritingOwner.test(owner))
                                    || (limitTierConsumer.test(caller) && limitTierOwner.test(owner))
                                    || (caller.equals(resolver)
                                    && (owner.equals(OWNER_PACKAGE + "enums/RiskDecisionConfigurationErrorCode") || owner.equals(OWNER_PACKAGE + "enums/RiskVariableErrorCode")
                                    || owner.equals(COMPLIANCE_OWNER_PACKAGE + "enums/ClientReviewErrorCode"))),
                                    caller + " accesses Owner field " + owner + "." + field);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
    }

    private static ApplicationContextRunner context(EmptyDiscovery discovery) {
        return new ApplicationContextRunner().withUserConfiguration(RiskClientConfiguration.class)
            .withBean(RiskDiscoveryInterceptor.class, () -> new RiskDiscoveryInterceptor(discovery))
            .withPropertyValues("spring.profiles.active=auto");
    }

    private static void assertInvalid(DecisionRuleSetManagementApi api) {
        var failure = assertThrows(ErrorException.class, () -> api.getAuthoringContext(ACTOR, SESSION, "CHECKOUT_PREPARE"));
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, failure.getErrorCode());
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private static boolean hasReadTimeoutCause(Throwable failure) {
        return hasCause(failure, HttpTimeoutException.class) || hasCause(failure, SocketTimeoutException.class);
    }

    private static void assertSafeFailure(Throwable failure) {
        var trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        for (String secret : List.of("payload-canary", "privateUnknown", "private-key-canary")) {
            assertFalse(trace.toString().contains(secret), "异常及 Cause/Suppressed 不得包含响应键或值");
        }
    }

    private static String problem(String title, String code) {
        return "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"code\":\"" + code
            + "\",\"detail\":\"payload-canary\",\"requestId\":\"" + REQUEST_ID + "\",\"timestamp\":\"" + TIME + "\"}";
    }

    /** Surefire 在本测试模块目录运行；app 模块是同一服务目录下的相邻模块，不向上遍历查找目录名。 */
    private static Path appModule() {
        Path module = Path.of("").resolve("../paytech-operations-console-bff-app").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(module), "app 模块不存在: " + module);
        return module;
    }

    // 对每个对象的每个属性分别删键、加键和替换类型，覆盖绑定后无法发现的信息丢失。
    private static void collectInvalidProperties(JsonNode root, JsonNode node, String path, List<String> bodies,
                                                 Set<String> nullablePaths) {
        if (node.isObject()) {
            String first = node.propertyNames().iterator().next();
            String duplicate = "{\"" + first + "\":" + node.get(first) + "," + node.toString().substring(1);
            bodies.add(root.toString().replace(node.toString(), duplicate));
            var unknown = root.deepCopy();
            ((ObjectNode) unknown.at(path)).put("privateUnknown", "payload-canary");
            bodies.add(unknown.toString());
            for (String property : node.propertyNames()) {
                var missing = root.deepCopy();
                ((ObjectNode) missing.at(path)).remove(property);
                bodies.add(missing.toString());
                JsonNode value = node.get(property);
                for (String invalid : invalidTokens(value, property)) {
                    if (invalid.equals("null") && nullablePaths.contains(path + "/" + property)) continue;
                    var changed = root.deepCopy();
                    ((ObjectNode) changed.at(path)).set(property, JSON.readTree(invalid));
                    bodies.add(changed.toString());
                }
                collectInvalidProperties(root, value, path + "/" + property, bodies, nullablePaths);
            }
        } else if (node.isArray()) {
            for (String invalidItem : List.of("null", "1")) {
                var changed = root.deepCopy();
                ((ArrayNode) changed.at(path)).add(JSON.readTree(invalidItem));
                bodies.add(changed.toString());
            }
            for (int index = 0; index < node.size(); index++) {
                collectInvalidProperties(root, node.get(index), path + "/" + index, bodies, nullablePaths);
            }
        }
    }

    private static List<String> invalidTokens(JsonNode value, String property) {
        if (property.equals("literalValue")) return List.of("1", "1.5", "[]", "{}");
        if (value.isIntegralNumber()) return property.equals("total")
            ? List.of("\"1\"", "1.5", "-1", "9223372036854775808", "null")
            : List.of("\"1\"", "1.5", "2147483648", "-2147483649", "null");
        if (value.isBoolean()) return List.of("\"true\"", "1", "null");
        if (value.isArray()) return List.of("{}", "null");
        if (value.isObject()) return property.equals("action") ? List.of("[]") : List.of("[]", "null");
        if (value.isNull()) return List.of("1", "[]", "{}");
        if (Set.of("comparisonOperator", "fieldCode", "decisionCode", "handlerCode", "implementationVersion", "implementationHash").contains(property)) return List.of("1", "true");
        return List.of("1", "true", "null");
    }

    static Stream<SuccessCase> successCases() {
        String rule = """
            {"ruleCode":"ALLOW_RULE","ruleName":"允许规则","priorityNo":1,"enabledFlag":true,
             "reasonCode":null,"reasonMessage":null,"additionalActions":[],"conditionNodes":[{"nodeKey":"20001","parentNodeKey":null,
             "sequenceNo":1,"nodeType":"PREDICATE","groupOperator":null,"comparisonOperator":"EQ",
             "operands":[{"operandRole":"LEFT","valueSequence":1,"sourceType":"CONTEXT",
             "fieldCode":"country_code","valueType":"STRING","literalValue":null,"variableCode":null,"numericUnit":null,"currencyCode":null,"currencyVariableCode":null},
             {"operandRole":"RIGHT","valueSequence":1,"sourceType":"LITERAL","fieldCode":null,
             "valueType":"STRING","literalValue":"AE","variableCode":null,"numericUnit":null,"currencyCode":null,"currencyVariableCode":null}]}],"action":{"actionCode":"ALLOW_CHECKOUT","actionVersionNo":1}}
            """;
        String item = "{\"ruleSetVersionId\":\"30001\",\"pointVersionId\":\"20001\",\"pointCode\":\"CHECKOUT_PREPARE\","
            + "\"ruleSetCode\":\"DEFAULT_RULES\",\"ruleSetName\":\"规则集\",\"versionNo\":1,\"status\":\"DRAFT\","
            + "\"contentHash\":\"" + HASH + "\",\"createdBy\":\"10001\",\"changeReason\":\"初始版本\","
            + "\"createTime\":\"" + TIME + "\",\"frozenBy\":null,\"frozenTime\":null}";
        String candidate = "{\"releaseId\":\"40001\",\"releaseNo\":1,\"decisionPointId\":\"10002\","
            + "\"pointCode\":\"CHECKOUT_PREPARE\",\"pointVersionId\":\"20001\",\"ruleSetVersionId\":\"30001\","
            + "\"environmentCode\":\"TEST\",\"status\":\"DRAFT\",\"version\":1,\"contentHash\":\"" + HASH
            + "\",\"createdBy\":\"10001\",\"createTime\":\"" + TIME + "\",\"allStaticChecksPassed\":null}";
        String state = "{\"auditEventId\":\"50001\",\"eventTime\":\"" + TIME + "\",\"fromStatus\":null,"
            + "\"toStatus\":\"DRAFT\",\"reasonCode\":\"CANDIDATE_CREATED\",\"triggerType\":\"OPERATIONS_COMMAND\","
            + "\"actorId\":\"10001\",\"aggregateVersion\":1}";
        String check = "{\"checkCode\":\"POINT_READY\",\"checkStatus\":\"PASSED\",\"severity\":\"INFO\","
            + "\"errorCode\":null,\"errorSummary\":null}";
        String authoring = """
            {"descriptorCode":"RISK_VARIABLE_AUTHORING","operandFormat":"VARIABLE_REFERENCE","variableQueryContext":{"pointCode":"CHECKOUT_PREPARE","inputContractCode":"CHECKOUT_INPUT","inputContractVersion":1,"usage":"RULE_CONDITION"},"point":{"pointCode":"CHECKOUT_PREPARE",
             "pointName":"支付准备","businessDomain":"CHECKOUT","inputContractCode":"CHECKOUT_INPUT",
             "inputContractVersion":1,"outputContractCode":"CHECKOUT_OUTPUT","defaultDecision":"DENY",
             "evaluationStrategy":"FIRST_MATCH","failureStrategy":"DENY","timeoutMillis":1000,"status":"ACTIVE"},
             "fields":[{"fieldCode":"country_code","valueType":"STRING","requiredFlag":true,
             "dataClassification":"INTERNAL","ruleReferenceFlag":true,"actionParameterFlag":false,"variableCode":"REGISTERED_COUNTRY"}],
             "operators":["EQ"],"actions":[{"actionCode":"ALLOW_CHECKOUT","actionName":"允许",
             "actionCategory":"DECISION","versionNo":1,"handlerCode":"CHECKOUT_DECISION",
             "implementationVersion":"1","implementationHash":"%s","terminalFlag":true,
             "decisionCode":"ALLOW","mergePolicy":"MOST_RESTRICTIVE","shadowSupportedFlag":true,"status":"ACTIVE"}],
             "features":[],"actionParameters":[],"obligations":[],"limitBindings":[],"targetBindings":[]}
            """.formatted(HASH);
        return Stream.of(
            new SuccessCase("deleteRule", "DELETE", "/decision-rule-sets/v1/rule-sets/90001",
                "{\"ruleSetId\":\"90001\",\"status\":\"DELETED\",\"version\":\"2\",\"deletedBy\":\"10001\",\"deletedTime\":\"" + TIME + "\"}",
                DeleteRuleSetResponseBody.class),
            new SuccessCase("authoring", "GET", "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE", authoring,
                RuleAuthoringContextResponseBody.class),
            new SuccessCase("ruleList", "GET", "/decision-rule-sets/v1/rule-sets", page(append(item, "\"ruleSetId\":\"90001\",\"managementVersion\":\"1\",\"inUse\":false")),
                new ParameterizedTypeReference<PagingResponseBody<RuleSetListItemResponseBody>>() {}.getType()),
            new SuccessCase("ruleDetail", "GET", "/decision-rule-sets/v1/rule-sets/30001",
                append(item, "\"rules\":[" + rule + "],\"operandFormat\":null,\"currentVariables\":[],\"valuesAsOf\":null,\"variableIssues\":[]"), RuleSetDetailResponseBody.class),
            new SuccessCase("createRule", "POST", "/decision-rule-sets/v1/rule-sets",
                "{\"ruleSetVersionId\":\"30001\",\"pointVersionId\":\"20001\",\"ruleSetCode\":\"DEFAULT_RULES\","
                    + "\"versionNo\":1,\"status\":\"DRAFT\",\"contentHash\":\"" + HASH + "\",\"createdBy\":\"10001\",\"createTime\":\"" + TIME + "\"}",
                CreateRuleSetVersionResponseBody.class),
            new SuccessCase("freezeRule", "POST", "/decision-rule-sets/v1/rule-sets/30001/freeze",
                "{\"ruleSetVersionId\":\"30001\",\"pointVersionId\":\"20001\",\"ruleSetCode\":\"DEFAULT_RULES\","
                    + "\"versionNo\":1,\"status\":\"FROZEN\",\"contentHash\":\"" + HASH + "\",\"frozenBy\":\"10001\",\"frozenTime\":\"" + TIME + "\"}",
                FreezeRuleSetVersionResponseBody.class),
            new SuccessCase("candidateList", "GET", "/decision-releases/v1/candidates", page(candidate),
                new ParameterizedTypeReference<PagingResponseBody<TestReleaseListItemResponseBody>>() {}.getType()),
            new SuccessCase("candidateDetail", "GET", "/decision-releases/v1/candidates/40001", append(candidate,
                "\"waitingForShadow\":false,\"frozenComposition\":{\"decisionPointId\":\"10002\",\"pointCode\":\"CHECKOUT_PREPARE\","
                    + "\"pointVersionId\":\"20001\",\"pointVersionNo\":1,\"pointVersionContentHash\":\"" + HASH
                    + "\",\"ruleSetVersionId\":\"30001\",\"ruleSetCode\":\"DEFAULT_RULES\",\"ruleSetVersionNo\":1,\"ruleSetContentHash\":\"" + HASH
                    + "\"},\"stateChanges\":[" + append(state, "\"triggerId\":\"" + HASH + "\"")
                    + "],\"staticChecks\":[" + append(check, "\"auditEventId\":\"50002\",\"eventTime\":\"" + TIME + "\"") + "]"),
                TestReleaseDetailResponseBody.class),
            new SuccessCase("createCandidate", "POST", "/decision-releases/v1/candidates",
                append(candidate.replace(",\"allStaticChecksPassed\":null", ""), "\"initialStateChange\":" + state),
                CreateTestReleaseResponseBody.class),
            new SuccessCase("validateCandidate", "POST", "/decision-releases/v1/candidates/40001/validate",
                "{\"releaseId\":\"40001\",\"releaseNo\":1,\"environmentCode\":\"TEST\",\"status\":\"VALIDATING\",\"version\":2,"
                    + "\"contentHash\":\"" + HASH + "\",\"allStaticChecksPassed\":true,\"checks\":[" + check + "]}",
                ValidateTestReleaseResponseBody.class));
    }

    private static String page(String item) {
        return "{\"items\":[" + item + "],\"page\":1,\"size\":10,\"total\":1}";
    }

    private static String append(String object, String fields) {
        return object.substring(0, object.lastIndexOf('}')) + "," + fields + "}";
    }

    private record SuccessCase(String name, String method, String path, String json, Type type) {
        Object invoke(DecisionRuleSetManagementApi rules, DecisionReleaseManagementApi releases) {
            return switch (name) {
                case "deleteRule" -> rules.deleteRuleSet(ACTOR, SESSION, "90001", new DeleteRuleSetRequestBody("1"));
                case "authoring" -> rules.getAuthoringContext(ACTOR, SESSION, "CHECKOUT_PREPARE");
                case "ruleList" -> rules.listRuleSets(ACTOR, SESSION, 1, 10, "CHECKOUT_PREPARE", "DEFAULT_RULES", "DRAFT");
                case "ruleDetail" -> rules.getRuleSet(ACTOR, SESSION, "30001");
                case "createRule" -> rules.createRuleSetVersion(ACTOR, SESSION,
                    new CreateRuleSetVersionRequestBody("CHECKOUT_PREPARE", "DEFAULT_RULES", "规则集", null, null, "初始版本", List.of()));
                case "freezeRule" -> rules.freezeRuleSetVersion(ACTOR, SESSION, "30001", new FreezeRuleSetVersionRequestBody(HASH));
                case "candidateList" -> releases.listCandidates(ACTOR, SESSION, 1, 10, "CHECKOUT_PREPARE", "DRAFT");
                case "candidateDetail" -> releases.getCandidate(ACTOR, SESSION, "40001");
                case "createCandidate" -> releases.createCandidate(ACTOR, SESSION, new CreateTestReleaseRequestBody("30001", HASH));
                case "validateCandidate" -> releases.validateCandidate(ACTOR, SESSION, "40001", new ValidateTestReleaseRequestBody(1));
                default -> throw new IllegalArgumentException(name);
            };
        }
    }

    /** 仅负责发现机制；空注册表与业务方法无关。 */
    private static final class EmptyDiscovery implements LoadBalancerClient {
        private final List<String> choices = new ArrayList<>();
        @Override public ServiceInstance choose(String serviceId) { choices.add(serviceId); return null; }
        @Override public <T> ServiceInstance choose(String serviceId, Request<T> request) { return choose(serviceId); }
        @Override public <T> T execute(String serviceId, LoadBalancerRequest<T> request) { throw new AssertionError("无实例不得执行"); }
        @Override public <T> T execute(String serviceId, ServiceInstance instance, LoadBalancerRequest<T> request) { throw new AssertionError("无实例不得执行"); }
        @Override public URI reconstructURI(ServiceInstance instance, URI original) { throw new AssertionError("无实例不得重写地址"); }
    }

    /** 在合法 JSON 前缀后产生真实 InputStream I/O 故障，观察 Parser 的关闭语义。 */
    private static final class PartialResponseStream extends InputStream {
        private final byte[] prefix = "{\"private-key-canary\":\"payload-canary\",".getBytes(StandardCharsets.UTF_8);
        private final IOException failure;
        private int position;
        private boolean closed;

        private PartialResponseStream(IOException failure) { this.failure = failure; }

        @Override public int read() throws IOException {
            if (position < prefix.length) return Byte.toUnsignedInt(prefix[position++]);
            throw failure;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (position == prefix.length) throw failure;
            int count = Math.min(length, prefix.length - position);
            System.arraycopy(prefix, position, bytes, offset, count);
            position += count;
            return count;
        }

        @Override public void close() { closed = true; }
    }

    /** 协议故障场景只改 HTTP 字节；每个实例固定一条 Owner 路由并记录所有请求。 */
    private static final class ProtocolServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> unexpected = new ArrayList<>();
        private volatile String response;
        private volatile int status = 200;
        private volatile String contentType = "application/json";
        private volatile String requestId;
        private volatile String actor;
        private volatile String session;
        private volatile String idempotencyKey;
        private volatile String requestBody;
        private volatile CountDownLatch responseGate;
        private volatile CountDownLatch partialBodyGate;
        private volatile boolean closeAfterPrefix;
        private volatile boolean closeBeforeHeaders;
        private volatile int prefixLength = -1;
        private final CountDownLatch prefixSent = new CountDownLatch(1);

        private ProtocolServer(SuccessCase sample) throws IOException {
            response = sample.json();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                if (!sample.path().equals(exchange.getRequestURI().getPath()) || !sample.method().equals(exchange.getRequestMethod())) {
                    unexpected.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                }
                requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
                actor = exchange.getRequestHeaders().getFirst("X-Gateway-Actor-Subject");
                session = exchange.getRequestHeaders().getFirst("X-Gateway-Session-Id");
                idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (responseGate != null) {
                    try {
                        responseGate.await(8, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("X-Request-Id", REQUEST_ID);
                exchange.getResponseHeaders().set("Location", url() + "/redirect-must-not-be-followed");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                try (exchange) {
                    if (closeBeforeHeaders) return;
                    if (status == 204) {
                        exchange.sendResponseHeaders(status, -1);
                        return;
                    }
                    exchange.sendResponseHeaders(status, bytes.length);
                    if (partialBodyGate != null || closeAfterPrefix) {
                        int sent = prefixLength < 0 ? bytes.length / 2 : prefixLength;
                        exchange.getResponseBody().write(bytes, 0, sent);
                        exchange.getResponseBody().flush();
                        prefixSent.countDown();
                        if (closeAfterPrefix) return;
                        try {
                            partialBodyGate.await(8, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        exchange.getResponseBody().write(bytes, sent, bytes.length - sent);
                    } else {
                        exchange.getResponseBody().write(bytes);
                    }
                }
            });
            server.start();
        }

        private String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        @Override public void close() {
            if (responseGate != null) responseGate.countDown();
            if (partialBodyGate != null) partialBodyGate.countDown();
            server.stop(0);
        }
    }
}
