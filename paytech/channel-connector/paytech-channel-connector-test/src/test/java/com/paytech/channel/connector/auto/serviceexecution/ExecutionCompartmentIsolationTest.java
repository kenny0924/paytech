package com.paytech.channel.connector.auto.serviceexecution;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.api.enums.ExecuteStatus;
import com.paytech.channel.connector.api.enums.ServiceExecutionErrorCode;
import com.paytech.channel.connector.api.serviceexecution.ServiceChannelExecutionApi;
import com.paytech.channel.connector.api.serviceexecution.request.ExecuteServiceChannelRequestBody;
import com.paytech.channel.connector.api.serviceexecution.response.ServiceChannelExecutionResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.ext.outbound.ChannelExecutionCompartments;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.test.base.AbstractServiceExecutionTest;
import com.paytech.channel.connector.test.builder.ServiceChannelSamples;
import com.paytech.channel.connector.test.mocker.ChannelMocker;

/**
 * 支付渠道与服务渠道的执行舱隔离（AC-09，technique 第 6.3 节）：测试配置把服务渠道执行舱上限设为 2
 * <li>两个服务渠道调用在外部挂起（慢响应）占满服务执行舱时，第三个服务渠道调用立即 503 CHANNEL_EXECUTION_BUSY、不排队、不发出请求；</li>
 * <li>同一时刻支付渠道的执行在独立执行舱（独立许可与 HttpClient）中照常成功；挂起的调用结束后许可全部归还。</li>
 *
 * @see ServiceChannelExecutionApi#execute(String, ExecuteServiceChannelRequestBody)
 * @see ExecuteStandardApiProcessor#process(ExecuteStandardApiRequest)
 */
class ExecutionCompartmentIsolationTest extends AbstractServiceExecutionTest {
    private static final String GMAP = "GMAP";
    private static final String PAYDEMO = "PAYDEMO";
    private static final long SLOW_MILLIS = 3000;

    @LocalServerPort
    private int port;

    @Autowired
    private ChannelExecutionCompartments compartments;

    @Autowired
    private ExecuteStandardApiProcessor executeStandardApiProcessor;

    /** 本类公共：ACTIVE 服务渠道 GMAP（授权 commerce-service）与支付渠道 PAYDEMO 都以无凭据流程实现 LOOKUP，出站路径分开。 */
    @BeforeEach
    void setUpClassMocks() {
        StandardApi lookup = lookupStandardApi();
        Channel gmap = channelWithEndpoint(GMAP, ChannelType.SERVICE, ChannelStatus.ACTIVE);
        implementLookup(gmap, lookup);
        externalApi.grant(CALLER, gmap, lookup, ChannelCallGrantStatus.ACTIVE);
        Channel payment = externalApi.channel(PAYDEMO, ChannelType.PAYMENT, ChannelStatus.ACTIVE,
            externalApi.endpointsJson(channelBaseUrl() + "/pay"));
        implementLookup(payment, lookup);
    }

    /** 服务执行舱被慢调用占满：第三个服务调用 503 不排队，支付渠道执行照常成功，结束后许可归还。 */
    @Test
    void test_serviceCompartmentFullDoesNotBlockPayment() {
        channel.reply("POST", "/api/lookup", ChannelMocker.Reply.delayed(201, ServiceChannelSamples.captured("S-1"),
            SLOW_MILLIS));
        channel.reply("POST", "/api/lookup", ChannelMocker.Reply.delayed(201, ServiceChannelSamples.captured("S-2"),
            SLOW_MILLIS));
        channel.respond("POST", "/api/pay/lookup", 201, ServiceChannelSamples.captured("P-1"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var slow = List.of(CompletableFuture.supplyAsync(() -> serviceCall("9001"), executor),
                CompletableFuture.supplyAsync(() -> serviceCall("9002"), executor));
            await().atMost(Duration.ofSeconds(5)).until(() -> channel.requests().size() == 2);

            var busy = execute(CALLER, GMAP, ServiceChannelSamples.LOOKUP, ServiceChannelSamples.input("9003"));
            ExecutionResult payment = executeStandardApiProcessor.process(new ExecuteStandardApiRequest(
                ServiceChannelSamples.LOOKUP, PAYDEMO, ServiceChannelSamples.input("9004"), "payment-service"));

            busy.assertProblem(503, ServiceExecutionErrorCode.CHANNEL_EXECUTION_BUSY.getCode());
            assertAll(() -> assertEquals(ExecuteStatus.SUCCESS, payment.getExecuteStatus()),
                () -> assertEquals(0, compartments.available(ChannelType.SERVICE)),
                () -> assertEquals(3, channel.requests().size(), "第三个服务调用不应发出请求"));
            var results = slow.stream().map(future -> future.orTimeout(15, TimeUnit.SECONDS).join()).toList();
            assertAll(() -> assertEquals(ExecuteStatus.SUCCESS.getCode(), results.get(0).getExecuteStatus()),
                () -> assertEquals(ExecuteStatus.SUCCESS.getCode(), results.get(1).getExecuteStatus()));
        } finally {
            executor.shutdownNow();
        }
        await().atMost(Duration.ofSeconds(5)).until(() -> compartments.available(ChannelType.SERVICE) == 2);
        assertEquals(64, compartments.available(ChannelType.PAYMENT));
    }

    /** 经真实 HTTP 入口调用服务渠道；4xx/5xx 使调用失败。 */
    private ServiceChannelExecutionResponseBody serviceCall(String attemptId) {
        ExecuteServiceChannelRequestBody body = new ExecuteServiceChannelRequestBody();
        body.setChannelCode(GMAP);
        body.setStandardApiCode(ServiceChannelSamples.LOOKUP);
        body.setInput(ServiceChannelSamples.input(attemptId));
        return client().post().uri(SERVICE_PATH).contentType(MediaType.APPLICATION_JSON)
            .header(ChannelConnectorHeaders.CALLER_HEADER, CALLER).body(body).retrieve()
            .body(ServiceChannelExecutionResponseBody.class);
    }

    /** JDK 客户端：不自动重试非幂等 POST。 */
    private RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient())).build();
    }
}
