package com.paytech.channel.connector.auto.callback;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.channel.connector.api.callback.ChannelCallbackApi;
import com.paytech.channel.connector.api.enums.ChannelCallbackErrorCode;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.api.event.ChannelPaymentNotifiedPayload;
import com.paytech.channel.connector.core.enums.CallbackMappingStatus;
import com.paytech.channel.connector.core.enums.CallbackSignatureStatus;
import com.paytech.channel.connector.core.enums.CallbackStatus;
import com.paytech.channel.connector.core.enums.CallbackTargetType;
import com.paytech.channel.connector.domain.ChannelAccountConfiguration;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.service.handler.ChannelPaymentNotifiedHandler;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;
import com.paytech.channel.connector.test.probe.ChannelEventProbe;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.awaitility.Awaitility.await;

/**
 * 渠道支付通知入站（3DS 全链路 technique 3.2、3.3.4）：从真实 HTTP 入口经 Vault 取账号公钥、SHA256withRSA 验签、去重入库，
 * 验签通过且映射到支付尝试时同事务登记 CHANNEL_PAYMENT_NOTIFIED，提交后真实发布到本地 Broker 的 channel-connector.events。
 *
 * <p>
 * 覆盖：验签通过入库并发布（AC-07）、验签失败 401 并留痕（不写指纹、不发布）、同一 Body 重复通知只记一条只发布一次（AC-08）、
 * Body 无法映射时应答成功不发布、渠道 / 账号 / 通知定义不可用 404 不入库、日志不含通知原文（AC-11）。密钥对为本例临时生成的合成值。
 *
 * @see ChannelCallbackApi#receive(String, String, org.springframework.http.HttpHeaders, byte[])
 */
@ExtendWith(OutputCaptureExtension.class)
class ReceiveChannelCallbackTest extends ChannelConnectorMockTestBase {
    private static final String ACK = "{\"code\":\"SUCCESS\",\"msg\":\"Success\"}";
    private static final String ATTEMPT_ID = "730000000000000301";
    private static final String TRADE_TOKEN = "T-PMX-NOTIFY-1";
    private static final String DEFINITION = """
        {"signature":{"algorithm":"RSA_SHA256","headerName":"sign","publicKeyParameter":"payermaxPublicKey"},
         "fields":{"merchantReference":"data.outTradeNo","channelTransactionRef":"data.tradeToken",
                   "status":"data.status","eventTime":"data.completeTime"},
         "acknowledgement":{"contentType":"application/json","body":"{\\"code\\":\\"SUCCESS\\",\\"msg\\":\\"Success\\"}"}}""";

    @LocalServerPort
    private int port;

    @Autowired
    private ChannelTestMapper mapper;

    @Autowired
    private ChannelEventProbe events;

    @Autowired
    private CompensationEventDispatcher dispatcher;

    private Fixture fixture;

    private KeyPair keys;

    /** 本类公共：启用的支付渠道配置 PayerMax 形状的通知定义，账号 Vault 参数含本例公钥；绑定事件观察队列。 */
    @BeforeEach
    void setUpClassMocks() throws GeneralSecurityException {
        fixture = data.activeFixture();
        mapper.callbackDefinition(fixture.getChannel().getChannelId(), DEFINITION);
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        vault.respond(fixture.getAccount().getAccountVaultToken(), accountConfiguration(
            Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())));
        events.bind();
    }

    /** 等待发布任务结束后删除观察队列，避免异步发布因无路由被退回。 */
    @AfterEach
    void tearDownClassMocks() {
        try {
            await().atMost(Duration.ofSeconds(15))
                .until(() -> dispatcher.activeTaskCount() == 0 && dispatcher.queuedTaskCount() == 0);
        } finally {
            events.unbind();
        }
    }

    /** 验签通过：200 + 定义的应答体；信封与回调项入库，发布 ChannelPaymentNotified 后信封置 PROCESSED（AC-07）。 */
    @Test
    void test_verifiedNoticePublished(CapturedOutput output) {
        byte[] body = notice(ATTEMPT_ID, "SUCCESS");

        var response = post(body, sign(body));

        var callback = onlyCallback();
        long callbackId = ((Number)callback.get("callbackId")).longValue();
        JsonNode envelope = events.awaitEnvelope(callbackId);
        JsonNode payload = envelope.path("payload");
        var item = mapper.callbackItems().getFirst();
        assertAll(() -> assertEquals(200, response.getStatusCode().value()), () -> assertEquals(ACK, response.getBody()),
            () -> assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType()),
            () -> assertEquals(CallbackSignatureStatus.VERIFIED.getCode(), callback.get("signatureStatus")),
            () -> assertEquals(64, ((String)callback.get("fingerprint")).length()), () -> assertNull(callback.get("fileId")),
            () -> assertEquals(ChannelPaymentNotifiedPayload.EVENT_TYPE, envelope.path("eventType").asString()),
            () -> assertEquals(ChannelPaymentNotifiedPayload.AGGREGATE_TYPE, envelope.path("aggregateType").asString()),
            () -> assertEquals(ChannelPaymentNotifiedPayload.AGGREGATE_VERSION, envelope.path("aggregateVersion").asLong()),
            () -> assertEquals(fixture.getChannel().getChannelId().toString(), payload.path("channelId").asString()),
            () -> assertEquals(fixture.getAccount().getChannelAccountId().toString(),
                payload.path("channelAccountId").asString()),
            () -> assertEquals(ATTEMPT_ID, payload.path("merchantReference").asString()),
            () -> assertEquals(TRADE_TOKEN, payload.path("channelTransactionRef").asString()),
            () -> assertEquals("SUCCESS", payload.path("reportedStatus").asString()),
            () -> assertEquals(Set.copyOf(ChannelPaymentNotifiedPayload.FIELDS), Set.copyOf(fieldNames(payload))),
            () -> assertEquals(CallbackTargetType.PAYMENT_ATTEMPT.getCode(), item.get("targetType")),
            () -> assertEquals(Long.valueOf(ATTEMPT_ID), ((Number)item.get("targetId")).longValue()),
            () -> assertEquals(CallbackMappingStatus.MAPPED.getCode(), item.get("mappingStatus")),
            () -> assertEquals(TRADE_TOKEN, item.get("externalTransactionId")),
            () -> assertNotNull(item.get("channelEventTime")));
        assertEquals(CallbackStatus.PROCESSED.getCode(), onlyCallback().get("callbackStatus"));
        assertAll(() -> assertFalse(output.getAll().contains(TRADE_TOKEN), "日志不得含通知原文"),
            () -> assertFalse(output.getAll().contains(sign(body)), "日志不得含签名"));
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
    }

    /** 验签失败：入库留痕（REJECTED，不写指纹与回调项），返回 401 Problem，不登记发布任务、不发布。 */
    @Test
    void test_signatureInvalidRejected() {
        byte[] body = notice(ATTEMPT_ID, "SUCCESS");
        byte[] tampered = notice(ATTEMPT_ID, "FAILED");

        var failure = assertThrows(RestClientResponseException.class, () -> post(tampered, sign(body)));
        var missing = assertThrows(RestClientResponseException.class, () -> post(body, null));

        var rows = mapper.callbacks();
        assertAll(() -> assertEquals(401, failure.getStatusCode().value()),
            () -> assertEquals(ChannelCallbackErrorCode.CHANNEL_CALLBACK_SIGNATURE_INVALID.getCode(),
                JsonMapper.builder().build().readTree(failure.getResponseBodyAsString()).path("code").asString()),
            () -> assertEquals(401, missing.getStatusCode().value()), () -> assertEquals(2, rows.size()),
            () -> assertTrue(rows.stream().allMatch(row -> CallbackSignatureStatus.REJECTED.getCode()
                .equals(row.get("signatureStatus")) && CallbackStatus.REJECTED.getCode().equals(row.get("callbackStatus"))
                && row.get("fingerprint") == null)),
            () -> assertTrue(mapper.callbackItems().isEmpty()),
            () -> assertTrue(mapper.compensationEvents(ChannelPaymentNotifiedHandler.EVENT_TYPE).isEmpty()));
        events.assertNoEnvelope();
    }

    /** 同一 Body 重复到达：两次都应答成功，只入库一次、只发布一次（AC-08）；伪造通知不会占用真实通知的去重键。 */
    @Test
    void test_duplicateNoticeRecordedOnce() {
        byte[] body = notice(ATTEMPT_ID, "SUCCESS");
        assertThrows(RestClientResponseException.class, () -> post(body, sign(notice(ATTEMPT_ID, "FAILED"))));

        var first = post(body, sign(body));
        var second = post(body, sign(body));

        var verified = mapper.callbacks().stream()
            .filter(row -> CallbackSignatureStatus.VERIFIED.getCode().equals(row.get("signatureStatus"))).toList();
        long callbackId = ((Number)verified.getFirst().get("callbackId")).longValue();
        events.awaitEnvelope(callbackId);
        assertAll(() -> assertEquals(ACK, first.getBody()), () -> assertEquals(ACK, second.getBody()),
            () -> assertEquals(1, verified.size()), () -> assertEquals(1, mapper.callbackItems().size()));
    }

    /** Body 无法映射（商户订单号不是支付尝试 ID）：仍应答成功避免渠道无限重发；回调项 UNMATCHED，信封 PROCESSED，不发布。 */
    @Test
    void test_unmatchedNoticeAcknowledged() {
        byte[] body = notice("ORDER-ABC", "SUCCESS");

        var response = post(body, sign(body));

        var callback = onlyCallback();
        var item = mapper.callbackItems().getFirst();
        assertAll(() -> assertEquals(ACK, response.getBody()),
            () -> assertEquals(CallbackStatus.PROCESSED.getCode(), callback.get("callbackStatus")),
            () -> assertEquals(CallbackMappingStatus.UNMATCHED.getCode(), item.get("mappingStatus")),
            () -> assertEquals(CallbackTargetType.UNKNOWN.getCode(), item.get("targetType")),
            () -> assertNull(item.get("targetId")),
            () -> assertTrue(mapper.compensationEvents(ChannelPaymentNotifiedHandler.EVENT_TYPE).isEmpty()));
        events.assertNoEnvelope();
    }

    /** 渠道编码不存在、账号不属于该渠道或渠道没有通知定义：404，不读 Vault、不入库。 */
    @Test
    void test_unavailableNotFound() {
        byte[] body = notice(ATTEMPT_ID, "SUCCESS");
        var otherChannel = data.channel("PAYERMAX", "合成渠道", ChannelType.PAYMENT, ChannelStatus.ACTIVE);

        int unknownChannel = status("/channel-callbacks/v1/UNKNOWN_CHANNEL/" + fixture.getAccount().getChannelAccountId(),
            body);
        int foreignAccount = status("/channel-callbacks/v1/PAYERMAX/" + fixture.getAccount().getChannelAccountId(), body);
        mapper.callbackDefinition(fixture.getChannel().getChannelId(), null);
        int noDefinition = status(path(), body);

        assertAll(() -> assertEquals(404, unknownChannel), () -> assertEquals(404, foreignAccount),
            () -> assertEquals(404, noDefinition), () -> assertTrue(mapper.callbacks().isEmpty()),
            () -> assertNotNull(otherChannel));
        vault.assertNoCalls();
    }

    /** 本例渠道与账号的通知路径。 */
    private String path() {
        return "/channel-callbacks/v1/" + fixture.getChannel().getChannelCode() + "/"
            + fixture.getAccount().getChannelAccountId();
    }

    /** 经真实 HTTP 入口提交通知；4xx/5xx 抛出。 */
    private ResponseEntity<String> post(byte[] body, String signature) {
        return client().post().uri(path()).contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> {
                if (signature != null) {
                    headers.add("sign", signature);
                }
            }).body(body).retrieve().toEntity(String.class);
    }

    /** 提交通知并返回 HTTP 状态。 */
    private int status(String uri, byte[] body) {
        try {
            return client().post().uri(uri).contentType(MediaType.APPLICATION_JSON).header("sign", sign(body)).body(body)
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** JDK 传输的客户端（不自动重试）。 */
    private RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient())).build();
    }

    /** PayerMax 形状的支付结果通知原文。 */
    private static byte[] notice(String outTradeNo, String status) {
        return """
            {"code":"APPLY_SUCCESS","msg":"","keyVersion":"1","appId":"synthetic-app","merchantNo":"SYN001",
             "notifyTime":"2026-10-22T08:15:31.000Z","notifyType":"PAYMENT",
             "data":{"outTradeNo":"%s","tradeToken":"%s","totalAmount":10.00,"currency":"AED","status":"%s",
                     "completeTime":"2026-10-22T08:15:30.123Z"}}""".formatted(outTradeNo, TRADE_TOKEN, status)
            .getBytes(StandardCharsets.UTF_8);
    }

    /** 以本例私钥对原始 Body 字节做 SHA256withRSA 签名，Base64。 */
    private String sign(byte[] body) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keys.getPrivate());
            signer.update(body);
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException failure) {
            throw new AssertionError(failure);
        }
    }

    /** 账号 Vault 配置：参数含渠道公钥，归属与本例账号一致。 */
    private String accountConfiguration(String publicKey) {
        var config = new ChannelAccountConfiguration();
        config.setSchemaVersion(1);
        config.setChannelCode(fixture.getChannel().getChannelCode());
        config.setChannelAccountId(fixture.getAccount().getChannelAccountId().toString());
        config.setEntityNo(fixture.getAccount().getEntityNo());
        config.setParameters(Map.of("payermaxPublicKey", publicKey));
        config.setCredentials(Map.of("merchantPrivateKey", "synthetic-private-key"));
        return JsonMapper.builder().build().writeValueAsString(config);
    }

    /** 唯一的回调信封。 */
    private Map<String, Object> onlyCallback() {
        var rows = mapper.callbacks();
        assertEquals(1, rows.size(), "回调信封数量");
        return rows.getFirst();
    }

    /** Payload 字段名（信封按 RFC 8785 规范化，字段按名称排序）。 */
    private static List<String> fieldNames(JsonNode payload) {
        return payload.propertyNames().stream().toList();
    }
}
