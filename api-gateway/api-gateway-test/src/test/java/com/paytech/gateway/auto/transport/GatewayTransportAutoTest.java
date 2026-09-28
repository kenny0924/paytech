package com.paytech.gateway.auto.transport;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import com.paytech.gateway.test.GatewayPublicTestClient;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.ApiGatewayApplication;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotRefresher;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore.RequestBarrier;
import com.paytech.gateway.transport.GatewayHeaderReceiveDeadlineHandler;
import com.paytech.gateway.transport.GatewayNettyServerConfiguration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import reactor.netty.NettyPipeline;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpRequestDecoderSpec;
import reactor.netty.http.server.HttpServer;

/** Gateway application listener、Netty 资源边界与 BFF transport 自动验收。 */
class GatewayTransportAutoTest extends GatewayAutoTestBase {
    private static final String LOGIN_PATH = OperationsTestPaths.LOGIN;
    private static final String ORIGIN = "https://operations-console.test";
    private static final String CLIENT_IP_HEADER = "X-Real-IP";
    private static final String DEFAULT_CLIENT_IP = "203.0.113.17";
    private static final String LISTENER_POLICY_ERROR = "Gateway application listener 配置不符合固定传输策略";

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private HttpClientProperties httpClientProperties;

    @ParameterizedTest
    @CsvSource({"ACCEPTED,200", "TIMEOUT,504", "INVALID_RESPONSE,502", "DISCONNECT,502"})
    void test_secondApprovedTargetUsesOperationsSecurityChainWithoutRetry(String outcome, int expectedStatus) {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(GatewaySnapshotFixture.operationsTransportSnapshot());
        Assertions.assertTrue(refreshSnapshot());
        var owner = GatewayAutoTestRuntime.clientApiOwnerSimulator();
        owner.scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Outcome.valueOf(outcome));

        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
            .exchange().expectStatus().isEqualTo(expectedStatus);

        Assertions.assertEquals(1, owner.journal().count(), "Owner 命令不能自动重试");
        Assertions.assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        Assertions.assertEquals("/auth/v1/login", owner.journal().entries().getFirst().rawPath());
        owner.verifyComplete();
    }

    @Test
    void test_listenerProfilesUseInternalHttpWithoutApplicationTlsMode() throws IOException {
        List<PropertySource<?>> production = yaml("application.yml");
        List<PropertySource<?>> auto = yaml("application-auto.yml");

        Assertions.assertAll(
            () -> Assertions.assertEquals(List.of("${GATEWAY_BIND_ADDRESS}"), values(production, "server.address")),
            () -> Assertions.assertEquals(List.of(), values(production, "gateway.transport.mode")),
            () -> Assertions.assertEquals(List.of(), values(production, "server.ssl.enabled")),
            () -> Assertions.assertEquals(List.of(Boolean.FALSE), values(production, "server.http2.enabled")),
            () -> Assertions.assertEquals(List.of(), values(auto, "gateway.transport.mode")),
            () -> Assertions.assertEquals(List.of("127.0.0.1"), values(auto, "server.address")),
            () -> Assertions.assertEquals(List.of(), values(auto, "server.ssl.enabled")));
    }

    @Test
    void test_listenerStartupGateRetainsHttp11AndRejectsHttp2() {
        ServerProperties internalHttp = new ServerProperties();
        Assertions.assertDoesNotThrow(() -> customize(internalHttp));

        internalHttp.getHttp2().setEnabled(true);
        IllegalStateException failure =
            Assertions.assertThrows(IllegalStateException.class, () -> customize(internalHttp));
        Assertions.assertEquals(LISTENER_POLICY_ERROR, failure.getMessage());
    }

    @Test
    void test_transportAuthoritiesAreRegisteredOnce() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.paytech.gateway.transport.GatewayTransportProperties")),
            () -> Assertions
                .assertNotNull(Class.forName("com.paytech.gateway.transport.GatewayHeaderReceiveDeadlineHandler")),
            () -> Assertions.assertNotNull(Class.forName("com.paytech.gateway.web.GatewayUpstreamFailureClassifier")),
            () -> Assertions.assertEquals(1, applicationContext
                .getBeansOfType(Class.forName("com.paytech.gateway.web.GatewayRequestHeaderLimitsWebFilter")).size()));
    }

    @Test
    void test_headerDeadlinePipelineRequiresOneResolvedHttpCodec() {
        EmbeddedChannel channel = new EmbeddedChannel();
        EmbeddedChannel decoderOnly = new EmbeddedChannel();
        EmbeddedChannel sinkOnly = new EmbeddedChannel();
        HttpRequestDecoderSpec decoder = decoderSpec();
        GatewayHeaderReceiveDeadlineHandler.DecoderSettings settings = decoderSettings(decoder);
        try {
            Assertions.assertThrows(IllegalStateException.class,
                () -> com.paytech.gateway.transport.GatewayHeaderReceiveDeadlineHandler.install(channel.pipeline(),
                    settings));
            channel.pipeline().addLast(NettyPipeline.HttpCodec, new ChannelInboundHandlerAdapter());
            Assertions.assertThrows(IllegalStateException.class,
                () -> com.paytech.gateway.transport.GatewayHeaderReceiveDeadlineHandler.install(channel.pipeline(),
                    settings));
            channel.pipeline().remove(NettyPipeline.HttpCodec);
            channel.pipeline().addLast(NettyPipeline.HttpCodec, new HttpServerCodec(httpDecoderConfig(decoder)));
            com.paytech.gateway.transport.GatewayHeaderReceiveDeadlineHandler.install(channel.pipeline(), settings);
            com.paytech.gateway.transport.GatewayHeaderReceiveDeadlineHandler.install(channel.pipeline(), settings);
            Assertions.assertEquals(2,
                channel.pipeline().names().stream()
                    .filter(
                        name -> name.startsWith("com.paytech.gateway.transport.GatewayHeaderReceiveDeadlineHandler"))
                    .count());

            String handlerPrefix = GatewayHeaderReceiveDeadlineHandler.class.getName();
            decoderOnly.pipeline().addLast(NettyPipeline.HttpCodec, new HttpServerCodec(httpDecoderConfig(decoder)));
            decoderOnly.pipeline().addBefore(NettyPipeline.HttpCodec, handlerPrefix + ".decoder",
                new ChannelInboundHandlerAdapter());
            Assertions.assertThrows(IllegalStateException.class,
                () -> GatewayHeaderReceiveDeadlineHandler.install(decoderOnly.pipeline(), settings));

            sinkOnly.pipeline().addLast(NettyPipeline.HttpCodec, new HttpServerCodec(httpDecoderConfig(decoder)));
            sinkOnly.pipeline().addBefore(NettyPipeline.HttpCodec, handlerPrefix + ".sink",
                new ChannelInboundHandlerAdapter());
            Assertions.assertThrows(IllegalStateException.class,
                () -> GatewayHeaderReceiveDeadlineHandler.install(sinkOnly.pipeline(), settings));
        } finally {
            channel.finishAndReleaseAll();
            decoderOnly.finishAndReleaseAll();
            sinkOnly.finishAndReleaseAll();
        }
    }

    @Test
    void test_effectiveServerAndBffClientConfigurationIsBounded() {
        NettyReactiveWebServerFactory factory = applicationContext.getBean(NettyReactiveWebServerFactory.class);
        HttpServer server = apply(factory.getServerCustomizers(), HttpServer.create());
        HttpClient client =
            applicationContext.getBeansOfType(HttpClient.class).values().stream().findFirst().orElseThrow();

        Assertions.assertAll(
            () -> Assertions.assertEquals(16_384, server.configuration().decoder().maxInitialLineLength()),
            () -> Assertions.assertEquals(65_536, server.configuration().decoder().maxHeaderSize()),
            () -> Assertions.assertEquals(Duration.ofSeconds(15), server.configuration().idleTimeout()),
            () -> Assertions.assertEquals(1_000, server.configuration().maxConnections()),
            () -> Assertions.assertEquals(1_000, server.configuration().maxKeepAliveRequests()),
            () -> Assertions.assertEquals(512, server.configuration().options().get(ChannelOption.SO_BACKLOG)),
            () -> Assertions.assertArrayEquals(new HttpProtocol[] {HttpProtocol.HTTP11},
                server.configuration().protocols()),
            () -> Assertions.assertNull(server.configuration().readTimeout()),
            () -> Assertions.assertNull(server.configuration().requestTimeout()),
            () -> Assertions.assertEquals(HttpClientProperties.Pool.PoolType.FIXED,
                httpClientProperties.getPool().getType()),
            () -> Assertions.assertEquals(1_000, httpClientProperties.getPool().getMaxConnections()),
            () -> Assertions.assertEquals(2_000L, httpClientProperties.getPool().getAcquireTimeout()),
            () -> Assertions.assertEquals(2_000, httpClientProperties.getConnectTimeout()),
            () -> Assertions.assertEquals(1_000, client.configuration().connectionProvider().maxConnections()),
            () -> Assertions.assertTrue(client.configuration().isRetryDisabled()),
            () -> Assertions.assertNull(client.configuration().followRedirectPredicate()));
    }

    @Test
    void test_exactInitialLineBoundaryParsesAndOneByteOverDoesNotReachExchange() throws IOException {
        Logger accessLogger = (Logger)LoggerFactory.getLogger("gateway.access");
        ListAppender<ILoggingEvent> accessProbe = new ListAppender<>();
        accessProbe.start();
        accessLogger.addAppender(accessProbe);
        try {
            String exactResponse = rawResponse(requestWithInitialLineLength(16_384));
            Awaitility.await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertEquals(1, accessProbe.list.size()));
            String overResponse = rawResponse(requestWithInitialLineLength(16_385));

            Assertions.assertAll(() -> Assertions.assertTrue(exactResponse.startsWith("HTTP/1.1 404 ")),
                () -> Assertions.assertTrue(overResponse.isEmpty() || !overResponse.startsWith("HTTP/1.1 404 ")),
                () -> Assertions.assertEquals(1, accessProbe.list.size(),
                    "16,385-byte initial line must stop before ServerWebExchange"),
                () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count()),
                () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.jwksSimulator().journal().count()),
                () -> Assertions.assertTrue(redisTestCleaner.guardKeys().isEmpty()));
        } finally {
            accessLogger.detachAppender(accessProbe);
            accessProbe.stop();
        }
    }

    @Test
    void test_exactAggregateHeaderBoundaryParsesAndOneByteOverStopsBeforeExchange() throws IOException {
        String exactStatus = rawStatus(requestWithAggregateHeaderSize(65_536));
        int keysAfterExact = redisTestCleaner.guardKeys().size();
        String overStatus = rawStatus(requestWithAggregateHeaderSize(65_537));

        Assertions.assertAll(() -> Assertions.assertTrue(exactStatus != null && exactStatus.contains(" 404 ")),
            () -> Assertions.assertTrue(overStatus != null && overStatus.contains(" 431 ")),
            () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count()),
            () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.jwksSimulator().journal().count()), () -> Assertions
                .assertEquals(keysAfterExact, redisTestCleaner.guardKeys().size()));
    }

    @Test
    void test_noByteAcceptedConnectionIsClosedByFifteenSecondIdlePolicy() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(17_000);
            Assertions.assertEquals(-1, socket.getInputStream().read());
        }
        assertNoGatewaySideEffects();
    }

    @Test
    void test_incompleteHeadersAreClosedByFiveSecondTransportDeadline() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(7_000);
            socket.getOutputStream().write(
                ("GET / HTTP/1.1\r\n" + GatewayPublicTestClient.operationsChannelLine()
                    + "Host: 127.0.0.1\r\nX-Drip: unfinished").getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();

            int terminal = socket.getInputStream().read();
            Assertions.assertEquals(-1, terminal);
        }
        assertNoGatewaySideEffects();
    }

    @Test
    void test_dripFedHeadersCloseAtNonResettingTotalDeadline() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
            socket.setSoTimeout(7_000);
            OutputStream output = socket.getOutputStream();
            output.write(("GET / HTTP/1.1\r\n" + GatewayPublicTestClient.operationsChannelLine()
                + "Host: 127.0.0.1\r\nX-Drip: ").getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            ScheduledFuture<?> drip =
                scheduler.scheduleAtFixedRate(() -> writeDripByte(output), 1, 1, TimeUnit.SECONDS);
            try {
                Assertions.assertEquals(-1, socket.getInputStream().read());
            } finally {
                drip.cancel(true);
            }
        }
        assertNoGatewaySideEffects();
    }

    @Test
    void test_sameBufferPartialPipelinedHeaderKeepsItsOriginalFirstByteDeadline() {
        EmbeddedChannel channel = deadlineChannel();
        try {
            channel.writeInbound(
                bytes("GET /one HTTP/1.1\r\nHost: gateway.test\r\n\r\n" + "GET /two HTTP/1.1\r\nX-Unfinished: a"));
            Assertions.assertEquals(1, releaseDecodedRequests(channel));

            channel.advanceTimeBy(4, TimeUnit.SECONDS);
            channel.writeInbound(bytes("b"));
            releaseDecodedRequests(channel);
            channel.advanceTimeBy(1_100, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            Assertions.assertFalse(channel.isOpen(), "partial request two must expire from its same-buffer first byte");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_bodyCompletionAndPartialNextHeaderPreserveSeparateDeadlineOwnership() {
        EmbeddedChannel channel = deadlineChannel();
        try {
            channel.writeInbound(bytes("POST /one HTTP/1.1\r\nHost: gateway.test\r\n" + "Content-Length: 4\r\n\r\nab"));
            Assertions.assertEquals(1, releaseDecodedRequests(channel));
            channel.advanceTimeBy(6, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
            Assertions.assertTrue(channel.isOpen(), "body bytes must not own the Header deadline");

            channel.writeInbound(bytes("cdGET /two HTTP/1.1\r\nX-Unfinished: a"));
            releaseDecodedRequests(channel);
            channel.advanceTimeBy(4, TimeUnit.SECONDS);
            channel.writeInbound(bytes("b"));
            releaseDecodedRequests(channel);
            channel.advanceTimeBy(1_100, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            Assertions.assertFalse(channel.isOpen(),
                "partial next request must expire from the body-ending buffer arrival");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_completedPriorCumulationAndNextPartialKeepTheNextOriginalFirstByte() {
        EmbeddedChannel channel = deadlineChannel();
        try {
            channel.writeInbound(bytes(
                "GET /one HTTP/1.1\r\nHost: gateway.test\r\n\r\n" + "GET /two HTTP/1.1\r\nHost: gateway.test\r\n"));
            Assertions.assertEquals(1, releaseDecodedRequests(channel));
            channel.advanceTimeBy(4, TimeUnit.SECONDS);

            channel.writeInbound(bytes("\r\nGET /three HTTP/1.1\r\nX-Unfinished: a"));
            Assertions.assertEquals(1, releaseDecodedRequests(channel));
            channel.advanceTimeBy(4, TimeUnit.SECONDS);
            channel.writeInbound(bytes("b"));
            releaseDecodedRequests(channel);
            channel.advanceTimeBy(1_100, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            Assertions.assertFalse(channel.isOpen(),
                "request three must expire from the buffer that completed request two");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_chunkedPriorCumulationBodyEndAndNextPartialKeepOriginalFirstByte() {
        EmbeddedChannel channel = deadlineChannel();
        try {
            channel.writeInbound(
                bytes("POST /one HTTP/1.1\r\nHost: gateway.test\r\n" + "Transfer-Encoding: chunked\r\n\r\n4\r"));
            Assertions.assertEquals(1, releaseDecodedRequests(channel));
            channel.advanceTimeBy(6, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
            Assertions.assertTrue(channel.isOpen(), "chunk framing and body bytes must not own the Header deadline");

            channel.writeInbound(bytes("\nABCD\r\n0\r\n\r\nGET /two HTTP/1.1\r\nX-Unfinished: a"));
            releaseDecodedRequests(channel);
            channel.advanceTimeBy(4, TimeUnit.SECONDS);
            channel.writeInbound(bytes("b"));
            releaseDecodedRequests(channel);
            channel.advanceTimeBy(1_100, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            Assertions.assertFalse(channel.isOpen(),
                "partial request after chunked LastHttpContent must retain its original deadline");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_multipleCompletePipelinedRequestsCancelEveryHeaderDeadline() {
        EmbeddedChannel channel = deadlineChannel();
        try {
            channel.writeInbound(bytes(
                "GET /one HTTP/1.1\r\nHost: gateway.test\r\n\r\n" + "GET /two HTTP/1.1\r\nHost: gateway.test\r\n\r\n"));
            Assertions.assertEquals(2, releaseDecodedRequests(channel));

            channel.advanceTimeBy(5_100, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            Assertions.assertAll(() -> Assertions.assertTrue(channel.isOpen()),
                () -> Assertions.assertEquals(-1L, channel.runScheduledPendingTasks()));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_trackingDecoderSharesThe64KiBEnvelopeAndReleasesDuplicateInput() {
        EmbeddedChannel channel = deadlineChannel();
        ByteBuf exact = Unpooled.wrappedBuffer(requestWithAggregateHeaderSize(65_536));
        try {
            channel.writeInbound(exact);
            Assertions.assertEquals(1, releaseDecodedRequests(channel));
            channel.advanceTimeBy(5_100, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();

            Assertions.assertAll(() -> Assertions.assertEquals(0, exact.refCnt()),
                () -> Assertions.assertTrue(channel.isOpen()),
                () -> Assertions.assertEquals(-1L, channel.runScheduledPendingTasks()));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_trackingPipelineForwardsNonByteMessagesWithoutProbeOwnership() {
        EmbeddedChannel channel = deadlineChannel();
        Object marker = new Object();
        try {
            Assertions.assertTrue(channel.writeInbound(marker));
            Assertions.assertSame(marker, channel.readInbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_partialProbeCumulationAndDeadlineTasksReleaseWhenChannelCloses() {
        EmbeddedChannel channel = deadlineChannel();
        ByteBuf partial = bytes("GET /one HTTP/1.1\r\nX-Unfinished: a");
        try {
            channel.writeInbound(partial);
            Assertions.assertTrue(channel.runScheduledPendingTasks() > 0);

            channel.close();
            channel.runPendingTasks();
            channel.finishAndReleaseAll();

            Assertions.assertAll(() -> Assertions.assertEquals(0, partial.refCnt()),
                () -> Assertions.assertFalse(channel.isOpen()),
                () -> Assertions.assertEquals(-1L, channel.runScheduledPendingTasks()));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_removingProbeNeverReplaysItsPrivateCumulationToTheActualCodec() {
        EmbeddedChannel channel = deadlineChannel();
        AtomicInteger forwardedBytes = new AtomicInteger();
        channel.pipeline().addBefore(NettyPipeline.HttpCodec, "actual-codec-input-probe",
            new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message)
                    throws Exception {
                    if (message instanceof ByteBuf buffer) {
                        forwardedBytes.addAndGet(buffer.readableBytes());
                    }
                    super.channelRead(context, message);
                }
            });
        ByteBuf partial = bytes("GET /one HTTP/1.1\r\nX-Unfinished: a");
        int originalBytes = partial.readableBytes();
        try {
            channel.writeInbound(partial);
            Assertions.assertEquals(originalBytes, forwardedBytes.get());

            String decoderName =
                channel.pipeline().names().stream().filter(name -> name.endsWith(".decoder")).findFirst().orElseThrow();
            channel.pipeline().remove(decoderName);
            channel.runPendingTasks();

            Assertions.assertAll(
                () -> Assertions.assertEquals(originalBytes, forwardedBytes.get(),
                    "probe cleanup must release, not replay, its retained duplicate"),
                () -> Assertions.assertFalse(channel.isOpen(),
                    "removing either half of the framing probe must fail closed"),
                () -> Assertions.assertEquals(-1L, channel.runScheduledPendingTasks()));
        } finally {
            channel.finishAndReleaseAll();
            Assertions.assertEquals(0, partial.refCnt());
        }
    }

    @Test
    void test_removingProbeSinkCannotLeakDecodedPrivateCumulationMessagesToTheActualCodec() {
        EmbeddedChannel channel = deadlineChannel();
        ActualCodecInputProbe inputProbe = observeActualCodecInput(channel, "sink-removal-input-probe");
        ByteBuf partial = bytes("GET /one HTTP/1.1\r\nX-Unfinished: a");
        int originalBytes = partial.readableBytes();
        try {
            channel.writeInbound(partial);
            inputProbe.assertOnlyOriginal(originalBytes, "probe lifecycle");

            String sinkName =
                channel.pipeline().names().stream().filter(name -> name.endsWith(".sink")).findFirst().orElseThrow();
            channel.pipeline().remove(sinkName);
            channel.runPendingTasks();

            Assertions.assertAll(() -> inputProbe.assertOnlyOriginal(originalBytes, "probe lifecycle"),
                () -> Assertions.assertFalse(channel.isOpen()),
                () -> Assertions.assertEquals(-1L, channel.runScheduledPendingTasks()));
        } finally {
            channel.finishAndReleaseAll();
            Assertions.assertEquals(0, partial.refCnt());
        }
    }

    @Test
    void test_removingProbeSinkThenDecoderCannotForwardPrivateCumulationBeforeCloseRuns() {
        EmbeddedChannel channel = deadlineChannel();
        AtomicInteger deferredCloseAttempts = new AtomicInteger();
        ActualCodecInputProbe inputProbe = observeActualCodecInput(channel, "ordered-removal-input-probe");
        ByteBuf partial = bytes("GET /one HTTP/1.1\r\nX-Unfinished: a");
        int originalBytes = partial.readableBytes();
        try {
            channel.writeInbound(partial);
            inputProbe.assertOnlyOriginal(originalBytes, "ordered probe removal");

            String sinkName =
                channel.pipeline().names().stream().filter(name -> name.endsWith(".sink")).findFirst().orElseThrow();
            String decoderName =
                channel.pipeline().names().stream().filter(name -> name.endsWith(".decoder")).findFirst().orElseThrow();
            channel.pipeline().addBefore(sinkName, "sink-close-gate",
                new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    @Override
                    public void close(io.netty.channel.ChannelHandlerContext context,
                        io.netty.channel.ChannelPromise promise) {
                        deferredCloseAttempts.incrementAndGet();
                    }
                });
            channel.pipeline().remove(sinkName);
            Assertions.assertAll(() -> Assertions.assertTrue(channel.isOpen()),
                () -> Assertions.assertEquals(1, deferredCloseAttempts.get()));
            channel.pipeline().remove(decoderName);
            channel.runPendingTasks();

            Assertions.assertAll(() -> inputProbe.assertOnlyOriginal(originalBytes, "ordered probe removal"),
                () -> Assertions.assertEquals(1, deferredCloseAttempts.get()),
                () -> Assertions.assertFalse(channel.isOpen()),
                () -> Assertions.assertEquals(-1L, channel.runScheduledPendingTasks()));
        } finally {
            channel.finishAndReleaseAll();
            Assertions.assertEquals(0, partial.refCnt());
        }
    }

    @Test
    void test_liveSameWritePipelinedPartialHeaderClosesAtOriginalDeadline() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
            socket.setSoTimeout(7_000);
            OutputStream output = socket.getOutputStream();
            output.write(("OPTIONS " + LOGIN_PATH + " HTTP/1.1\r\n" + GatewayPublicTestClient.operationsChannelLine()
                + "Host: 127.0.0.1\r\n" + CLIENT_IP_HEADER + ": "
                + DEFAULT_CLIENT_IP + "\r\n" + "Origin: " + ORIGIN + "\r\n" + "Access-Control-Request-Method: POST\r\n"
                + "Access-Control-Request-Headers: Content-Type\r\n\r\n" + "GET /two HTTP/1.1\r\n"
                + GatewayPublicTestClient.operationsChannelLine() + "X-Unfinished: a")
                .getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            Assertions.assertTrue(readResponseHeaders(socket.getInputStream()).startsWith("HTTP/1.1 204 "));

            ScheduledFuture<?> laterByte = scheduler.schedule(() -> writeDripByte(output), 4, TimeUnit.SECONDS);
            try {
                Assertions.assertEquals(-1, socket.getInputStream().read());
            } finally {
                laterByte.cancel(true);
            }
        }
        assertNoGatewaySideEffects();
    }

    @Test
    void test_repeatedCaseVariantAuthorizationOverBudgetReturnsLocal431WithoutSideEffects() {
        EntityExchangeResult<byte[]> response = gatewayClient().post().uri(LOGIN_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header("Authorization", "a".repeat(4_096))
            .header("authorization", "b".repeat(4_097)).contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
            .exchange().expectBody().returnResult();
        assertProblem(response, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        HttpHeaders headers = response.getResponseHeaders();

        Assertions.assertAll(() -> Assertions.assertEquals(HttpStatus.valueOf(431), response.getStatus()),
            () -> Assertions.assertDoesNotThrow(() -> UUID.fromString(headers.getFirst("X-Request-Id"))),
            () -> Assertions.assertEquals(List.of("no-store"), headers.getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertEquals(List.of("no-cache"), headers.getOrEmpty(HttpHeaders.PRAGMA)),
            () -> Assertions.assertEquals(List.of(HttpHeaders.ORIGIN), headers.getOrEmpty(HttpHeaders.VARY)),
            () -> Assertions.assertEquals(List.of(ORIGIN), headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)),
            () -> Assertions.assertEquals(List.of("true"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)),
            () -> Assertions.assertEquals(List.of("POST, OPTIONS"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)),
            () -> Assertions.assertEquals(List.of("content-type"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)),
            () -> Assertions.assertEquals(List.of("X-Request-Id, Retry-After"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)),
            () -> Assertions.assertTrue(redisTestCleaner.guardKeys().isEmpty()),
            () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count()),
            () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.jwksSimulator().journal().count()));
    }

    @Test
    void test_knownPreflightWithOversizedRequestedHeadersKeepsExactTerminalCorsContract() {
        EntityExchangeResult<byte[]> response = gatewayClient().options().uri(LOGIN_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x".repeat(2_049)).exchange().expectBody()
            .returnResult();
        assertProblem(response, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        HttpHeaders headers = response.getResponseHeaders();

        Assertions.assertAll(() -> Assertions.assertEquals(HttpStatus.valueOf(431), response.getStatus()),
            () -> Assertions.assertDoesNotThrow(() -> UUID.fromString(headers.getFirst("X-Request-Id"))),
            () -> Assertions.assertEquals(List.of("no-store"), headers.getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertEquals(List.of("no-cache"), headers.getOrEmpty(HttpHeaders.PRAGMA)),
            () -> Assertions.assertEquals(List.of(HttpHeaders.ORIGIN), headers.getOrEmpty(HttpHeaders.VARY)),
            () -> Assertions.assertEquals(List.of(ORIGIN), headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)),
            () -> Assertions.assertEquals(List.of("true"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)),
            () -> Assertions.assertEquals(List.of("POST, OPTIONS"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)),
            () -> Assertions.assertEquals(List.of("content-type"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)),
            () -> Assertions.assertEquals(List.of("X-Request-Id, Retry-After"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)));
        assertNoGatewaySideEffects();
    }

    @Test
    void test_untrustedPreflightMethodGetsOnlyUniqueRouteTerminalHeaders() {
        EntityExchangeResult<byte[]> oversizedMethod = gatewayClient().options().uri(LOGIN_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "P".repeat(2_049))
            .exchange().expectBody().returnResult();

        EntityExchangeResult<byte[]> invalidMethod = gatewayClient().options().uri(LOGIN_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "post")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x".repeat(2_049)).exchange().expectBody()
            .returnResult();

        EntityExchangeResult<byte[]> unknownRoute = gatewayClient().options().uri("/api/operations/unknown")
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x".repeat(2_049)).exchange().expectBody()
            .returnResult();

        EntityExchangeResult<byte[]> rejectedQuery = gatewayClient().options().uri(LOGIN_PATH + "?unexpected=1")
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x".repeat(2_049)).exchange().expectBody()
            .returnResult();

        assertProblem(oversizedMethod, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        assertProblem(invalidMethod, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        assertProblem(unknownRoute, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        assertProblem(rejectedQuery, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");

        Assertions.assertAll(() -> Assertions.assertEquals(HttpStatus.valueOf(431), oversizedMethod.getStatus()),
            () -> Assertions.assertEquals(List.of("no-store"),
                oversizedMethod.getResponseHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertEquals(List.of("no-cache"),
                oversizedMethod.getResponseHeaders().getOrEmpty(HttpHeaders.PRAGMA)),
            () -> Assertions.assertEquals(List.of(HttpHeaders.ORIGIN),
                oversizedMethod.getResponseHeaders().getOrEmpty(HttpHeaders.VARY)),
            () -> Assertions.assertTrue(
                oversizedMethod.getResponseHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()),
            () -> Assertions.assertEquals(HttpStatus.valueOf(431), invalidMethod.getStatus()),
            () -> Assertions.assertEquals(List.of("no-store"),
                invalidMethod.getResponseHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertTrue(
                invalidMethod.getResponseHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()),
            () -> Assertions.assertEquals(HttpStatus.valueOf(431), unknownRoute.getStatus()),
            () -> Assertions
                .assertTrue(unknownRoute.getResponseHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL).isEmpty()),
            () -> Assertions.assertTrue(
                unknownRoute.getResponseHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()),
            () -> Assertions.assertEquals(HttpStatus.valueOf(431), rejectedQuery.getStatus()),
            () -> Assertions
                .assertTrue(rejectedQuery.getResponseHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL).isEmpty()),
            () -> Assertions.assertTrue(
                rejectedQuery.getResponseHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()));
        assertNoGatewaySideEffects();
    }

    @Test
    void test_untrustedOriginUnknownRouteAndRejectedQueryNeverGainEarlyTrustedCorsContext() {
        EntityExchangeResult<byte[]> oversizedOrigin =
            gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, "x".repeat(2_049))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectBody().returnResult();

        EntityExchangeResult<byte[]> invalidOrigin = gatewayClient().post().uri(LOGIN_PATH)
            .header(HttpHeaders.ORIGIN, "not-an-origin").header(HttpHeaders.AUTHORIZATION, "x".repeat(8_193))
            .bodyValue("{}").exchange().expectBody().returnResult();

        EntityExchangeResult<byte[]> unknownRoute =
            gatewayClient().post().uri("/api/operations/unknown").header(HttpHeaders.AUTHORIZATION, "x".repeat(8_193))
                .bodyValue("{}").exchange().expectBody().returnResult();

        EntityExchangeResult<byte[]> rejectedQuery = gatewayClient().post().uri(LOGIN_PATH + "?unexpected=1")
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.AUTHORIZATION, "x".repeat(8_193)).bodyValue("{}")
            .exchange().expectBody().returnResult();

        assertProblem(oversizedOrigin, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        assertProblem(invalidOrigin, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        assertProblem(unknownRoute, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        assertProblem(rejectedQuery, 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");

        Assertions.assertAll(() -> Assertions.assertEquals(HttpStatus.valueOf(431), oversizedOrigin.getStatus()),
            () -> Assertions.assertEquals(List.of("no-store"),
                oversizedOrigin.getResponseHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertEquals(List.of(HttpHeaders.ORIGIN),
                oversizedOrigin.getResponseHeaders().getOrEmpty(HttpHeaders.VARY)),
            () -> Assertions.assertTrue(
                oversizedOrigin.getResponseHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()),
            () -> Assertions.assertEquals(HttpStatus.valueOf(431), invalidOrigin.getStatus()),
            () -> Assertions.assertEquals(List.of("no-store"),
                invalidOrigin.getResponseHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertTrue(
                invalidOrigin.getResponseHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()),
            () -> Assertions.assertEquals(HttpStatus.valueOf(431), unknownRoute.getStatus()),
            () -> Assertions
                .assertTrue(unknownRoute.getResponseHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL).isEmpty()),
            () -> Assertions.assertTrue(
                unknownRoute.getResponseHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()),
            () -> Assertions.assertEquals(HttpStatus.valueOf(431), rejectedQuery.getStatus()),
            () -> Assertions
                .assertTrue(rejectedQuery.getResponseHeaders().getOrEmpty(HttpHeaders.CACHE_CONTROL).isEmpty()),
            () -> Assertions.assertTrue(
                rejectedQuery.getResponseHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEmpty()));
        assertNoGatewaySideEffects();
    }

    @Test
    void test_eachControlledHeaderRejectsOneByteOverItsExactBudget() {
        Map<String,
            Integer> budgets = Map.of(HttpHeaders.AUTHORIZATION, 8_192, HttpHeaders.COOKIE, 8_192, HttpHeaders.ORIGIN,
                2_048, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 2_048, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                2_048);

        budgets.forEach((name, budget) -> assertProblem(gatewayClient().post().uri(LOGIN_PATH)
            .header(name, "x".repeat(budget + 1)).contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
            .exchange(), 431, "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE"));

        assertNoGatewaySideEffects();
    }

    @Test
    void test_exactAuthorizationBudgetPassesHeaderGate() {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(200, "{}"));

        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "a".repeat(8_192)).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}").exchange().expectStatus().isOk();

        Assertions.assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    @Test
    void test_redirectIsNotFollowedAndImmediateDisconnectIsNotRetried() {
        var simulator = GatewayAutoTestRuntime.operationsBffSimulator();
        simulator.scenarios().expect(HttpMethod.POST, "/auth/v1/login", BffScenarioStore.Response.json(302, "")
            .withHeader(HttpHeaders.LOCATION, simulator.uri().resolve("/redirect-target").toString()));

        loginRequest().exchange().expectStatus().isEqualTo(302);
        Assertions.assertEquals(1, simulator.journal().count());

        simulator.scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.immediateDisconnect());
        assertProblem(loginRequest().exchange(), 502, "UPSTREAM_PROTOCOL_ERROR");
        Assertions.assertEquals(2, simulator.journal().count());
    }

    @Test
    void test_fixedPoolAcquireTimeoutReturns503AndReleasesGatewayLeases() {
        RequestBarrier barrier = new RequestBarrier();
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(200, "{}").waitingFor(barrier));

        try (ConfigurableApplicationContext child = gatewayContext(1, 200, "PT3S")) {
            WebTestClient client = childClient(child);
            CompletableFuture<EntityExchangeResult<byte[]>> first =
                CompletableFuture.supplyAsync(() -> childLogin(client).exchange().expectBody().returnResult());
            barrier.awaitRequest(Duration.ofSeconds(3));
            try {
                assertProblem(childLogin(client).exchange(), 503, "GATEWAY_DEPENDENCY_UNAVAILABLE");
                Assertions.assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
            } finally {
                barrier.release();
            }
            Assertions.assertEquals(HttpStatus.OK, await(first).getStatus());
            awaitNoConcurrencyLeases();
        } finally {
            barrier.release();
        }
    }

    @Test
    void test_shortenedCompleteChainTimeoutReturns504AndReleasesGatewayLeases() {
        RequestBarrier barrier = new RequestBarrier();
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(200, "{}").waitingFor(barrier));

        try (ConfigurableApplicationContext child = gatewayContext(1, 2_000, "PT0.2S")) {
            WebTestClient client = childClient(child);
            try {
                assertProblem(childLogin(client).exchange(), 504, "GATEWAY_UPSTREAM_TIMEOUT");
                Assertions.assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
                awaitNoConcurrencyLeases();
            } finally {
                barrier.release();
            }
        } finally {
            barrier.release();
        }
    }

    @Test
    void test_slowBffResponseInsideCompleteBudgetIsNotClosedByServerIdlePolicy() {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.delayed(200, "{}", Duration.ofSeconds(6)));

        try (ConfigurableApplicationContext child = gatewayContext(1, 2_000, "PT8S")) {
            childLogin(childClient(child, Duration.ofSeconds(8))).exchange().expectStatus().isOk();
            Assertions.assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
            awaitNoConcurrencyLeases();
        }
    }

    private WebTestClient.RequestHeadersSpec<?> loginRequest() {
        return gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private WebTestClient gatewayClient() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader(CLIENT_IP_HEADER, DEFAULT_CLIENT_IP).responseTimeout(Duration.ofSeconds(8)).build();
    }

    private List<PropertySource<?>> yaml(String name) throws IOException {
        return new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
    }

    private List<Object> values(List<PropertySource<?>> sources, String name) {
        return sources.stream().map(source -> source.getProperty(name)).filter(Objects::nonNull).toList();
    }

    private HttpServer apply(Collection<NettyServerCustomizer> customizers, HttpServer server) {
        HttpServer configured = server;
        for (NettyServerCustomizer customizer : customizers) {
            configured = customizer.apply(configured);
        }
        return configured;
    }

    private byte[] requestWithInitialLineLength(int length) {
        String prefix = "GET /";
        String suffix = " HTTP/1.1";
        String requestLine = prefix + "a".repeat(length - prefix.length() - suffix.length()) + suffix;
        return (requestLine + "\r\n" + GatewayPublicTestClient.operationsChannelLine()
            + "Host: 127.0.0.1\r\n" + CLIENT_IP_HEADER + ": " + DEFAULT_CLIENT_IP
            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] requestWithAggregateHeaderSize(int length) {
        String host = "Host: 127.0.0.1";
        String clientIp = CLIENT_IP_HEADER + ": " + DEFAULT_CLIENT_IP;
        String channelLine = GatewayPublicTestClient.operationsChannelLine();
        String connection = "Connection: close";
        String fillPrefix = "X-Fill: ";
        int fillLength = length - host.length() - clientIp.length() - (channelLine.length() - 2)
            - connection.length() - fillPrefix.length();
        return ("GET / HTTP/1.1\r\n" + channelLine + host + "\r\n" + clientIp + "\r\n" + fillPrefix + "x".repeat(fillLength) + "\r\n"
            + connection + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private EmbeddedChannel deadlineChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HttpRequestDecoderSpec decoder = decoderSpec();
        channel.pipeline().addLast(NettyPipeline.HttpCodec, new HttpServerCodec(httpDecoderConfig(decoder)));
        GatewayHeaderReceiveDeadlineHandler.install(channel.pipeline(), decoderSettings(decoder));
        return channel;
    }

    private ActualCodecInputProbe observeActualCodecInput(EmbeddedChannel channel, String name) {
        ActualCodecInputProbe inputProbe = new ActualCodecInputProbe();
        channel.pipeline().addBefore(NettyPipeline.HttpCodec, name, inputProbe);
        return inputProbe;
    }

    private HttpRequestDecoderSpec decoderSpec() {
        return HttpServer.create().configuration().decoder().maxInitialLineLength(16_384).maxHeaderSize(65_536);
    }

    private io.netty.handler.codec.http.HttpDecoderConfig httpDecoderConfig(HttpRequestDecoderSpec decoder) {
        return new io.netty.handler.codec.http.HttpDecoderConfig()
            .setMaxInitialLineLength(decoder.maxInitialLineLength()).setMaxHeaderSize(decoder.maxHeaderSize())
            .setMaxChunkSize(decoder.maxChunkSize()).setValidateHeaders(decoder.validateHeaders())
            .setInitialBufferSize(decoder.initialBufferSize())
            .setAllowDuplicateContentLengths(decoder.allowDuplicateContentLengths())
            .setAllowPartialChunks(decoder.allowPartialChunks());
    }

    private GatewayHeaderReceiveDeadlineHandler.DecoderSettings decoderSettings(HttpRequestDecoderSpec decoder) {
        return new GatewayHeaderReceiveDeadlineHandler.DecoderSettings(decoder.maxInitialLineLength(),
            decoder.maxHeaderSize(), decoder.maxChunkSize(), decoder.validateHeaders(), decoder.initialBufferSize(),
            decoder.allowDuplicateContentLengths(), decoder.allowPartialChunks());
    }

    private io.netty.buffer.ByteBuf bytes(String value) {
        return Unpooled.copiedBuffer(value, StandardCharsets.ISO_8859_1);
    }

    private int releaseDecodedRequests(EmbeddedChannel channel) {
        int requests = 0;
        Object message;
        while ((message = channel.readInbound()) != null) {
            if (message instanceof HttpRequest) {
                requests++;
            }
            ReferenceCountUtil.release(message);
        }
        return requests;
    }

    private String readResponseHeaders(InputStream input) throws IOException {
        StringBuilder headers = new StringBuilder();
        int matched = 0;
        while (matched < 4) {
            int value = input.read();
            if (value < 0) {
                break;
            }
            headers.append((char)value);
            char expected = "\r\n\r\n".charAt(matched);
            matched = value == expected ? matched + 1 : value == '\r' ? 1 : 0;
        }
        return headers.toString();
    }

    private void writeDripByte(OutputStream output) {
        try {
            output.write('x');
            output.flush();
        } catch (IOException ignored) {
            // 总 Header 时限关闭连接后，调度器的并发尾写无需传播。
        }
    }

    private void assertNoGatewaySideEffects() {
        Assertions.assertAll(
            () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count()),
            () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.jwksSimulator().journal().count()),
            () -> Assertions.assertTrue(redisTestCleaner.guardKeys().isEmpty()));
    }

    private String rawStatus(byte[] request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3_000);
            OutputStream output = socket.getOutputStream();
            output.write(request);
            output.flush();
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
                .readLine();
        }
    }

    private String rawResponse(byte[] request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3_000);
            socket.getOutputStream().write(request);
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private void customize(ServerProperties server) {
        new GatewayNettyServerConfiguration(server).customize(new NettyReactiveWebServerFactory(0));
    }

    private ConfigurableApplicationContext gatewayContext(int maxConnections, long acquireTimeoutMillis,
        String responseTimeout) {
        Duration routeResponseTimeout = Duration.parse(responseTimeout);
        GatewayAutoTestRuntime.platformSnapshotSimulator()
            .publish(GatewaySnapshotFixture.operationsSnapshot(routeResponseTimeout));
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("spring.profiles.active", "auto");
        properties.put("spring.cloud.discovery.client.simple.instances.operations-console-bff[0].uri",
            GatewayAutoTestRuntime.operationsBffSimulator().uri().toString());
        properties.put("gateway.operations.response-timeout", responseTimeout);
        properties.put("gateway.platform-snapshot.base-uri",
            GatewayAutoTestRuntime.platformSnapshotSimulator().uri().toString());
        properties.put("gateway.platform-jwks.base-uri", GatewayAutoTestRuntime.jwksSimulator().uri().toString());
        properties.put("gateway.platform-credential.base-uri", GatewayAutoTestRuntime.credentialSimulator().uri().toString());
        properties.put("gateway.operations.guard.redis.namespace", GatewayAutoTestRuntime.redisNamespace().value());
        properties.put("spring.cloud.gateway.server.webflux.httpclient.pool.max-connections", maxConnections);
        properties.put("spring.cloud.gateway.server.webflux.httpclient.pool.acquire-timeout", acquireTimeoutMillis);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("task10-auto-override", properties));
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ApiGatewayApplication.class)
            .sources(IsolatedReactorResources.class).environment(environment).logStartupInfo(false).run();
        Boolean loaded = reactor.core.publisher.Mono.defer(context.getBean(GatewaySnapshotRefresher.class)::refresh)
            .filter(Boolean.TRUE::equals).repeatWhen(repeats -> repeats.delayElements(Duration.ofMillis(10)).take(20))
            .next().defaultIfEmpty(false).block();
        Assertions.assertEquals(Boolean.TRUE, loaded, "隔离 Gateway Context 必须加载自己的 Snapshot");
        return context;
    }

    private WebTestClient childClient(ConfigurableApplicationContext context) {
        return childClient(context, Duration.ofSeconds(4));
    }

    private WebTestClient childClient(ConfigurableApplicationContext context, Duration timeout) {
        int childPort = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        return GatewayPublicTestClient.operations(childPort)
            .defaultHeader(CLIENT_IP_HEADER, DEFAULT_CLIENT_IP).responseTimeout(timeout).build();
    }

    private WebTestClient.RequestHeadersSpec<?> childLogin(WebTestClient client) {
        return client.post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}");
    }

    private EntityExchangeResult<byte[]> await(CompletableFuture<EntityExchangeResult<byte[]>> response) {
        try {
            return response.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("等待 Gateway pool 测试请求结束失败", exception);
        }
    }

    private void awaitNoConcurrencyLeases() {
        Awaitility.await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> Assertions.assertTrue(redisTestCleaner.guardKeys().stream()
                .noneMatch(key -> key.contains(":concurrency:") && key.endsWith(":leases"))));
    }

    private static final class ActualCodecInputProbe extends ChannelInboundHandlerAdapter {
        private final AtomicInteger messages = new AtomicInteger();
        private final AtomicInteger bytes = new AtomicInteger();
        private final List<Class<?>> types = new ArrayList<>();

        @Override
        public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message) throws Exception {
            messages.incrementAndGet();
            types.add(message.getClass());
            if (message instanceof ByteBuf buffer) {
                bytes.addAndGet(buffer.readableBytes());
            }
            super.channelRead(context, message);
        }

        private void assertOnlyOriginal(int originalBytes, String lifecycle) {
            Assertions.assertAll(
                () -> Assertions.assertEquals(1, messages.get(), () -> lifecycle + " leaked message types: " + types),
                () -> Assertions.assertEquals(originalBytes, bytes.get()));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class IsolatedReactorResources {
        @Bean
        ReactorResourceFactory reactorResourceFactory() {
            ReactorResourceFactory resources = new ReactorResourceFactory();
            resources.setUseGlobalResources(false);
            return resources;
        }
    }
}
