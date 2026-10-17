package com.paytech.payment.auto.card;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.instrument.PaymentInstrumentApi;
import com.paytech.payment.card.integration.impl.CardSecurityCodeClientImpl;
import com.paytech.payment.card.service.CardStorageService;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.PlatformMocker;

/**
 * Card 加密存储（需求 20261015 payment-Card加密存储，技术方案第 3 版）：从工具申请、pay 与读卡的真实 HTTP 入口，经真实 Card Processor、
 * MySQL 卡 / 卡凭证 / 卡交易工具三表、Redis CVV 暂存，以及真实 Vault 客户端访问 Vault 测试 HTTP 服务（按 REFERENCE 语义托管 DEK）。
 *
 * <p>
 * 覆盖：三表只有密文与 BIN/后四位/指纹，BIN 按卡号长度取 8 或 6 位；同一卡号只存一张卡、一把 DEK，有效期或持卡人不同才新增凭证；
 * 按明文卡号定位卡；并发首用同一卡号只留一张卡；CVV 按交易工具暂存、互不影响、只能领取一次；原提交同卡重放不访问 Vault、换卡拒绝；
 * Luhn 失败与过期卡拒绝；Vault 不可用时申请失败关闭；卡值不进日志。卡号均为公开测试卡号。
 *
 * @see PaymentInstrumentApi
 */
@ExtendWith(OutputCaptureExtension.class)
class CardStorageTest extends AbstractPayCardTest {
    private static final String OTHER_PAN = "5555555555554444";
    /** 15 位公开测试卡号（Amex），BIN 取 6 位。 */
    private static final String SHORT_PAN = "378282246310005";
    /** IV(12) + 3 位 CVV + Tag(16)。 */
    private static final int ENCRYPTED_CVV_BYTES = 31;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CardStorageService cardStorageService;

    /** 本类公共：MID 候选与风控放行，pay 用例按需配置渠道结论。 */
    @BeforeEach
    void setUpClassMocks() {
        channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 新卡：三表各一条，只有密文与 BIN/后四位/指纹；工具引用为交易工具 ID；DEK 交 Vault；CVV 加密暂存且 TTL 不超过工具期限。 */
    @Test
    void test_applyStoresEncryptedCard() {
        newPayment();

        var card = mapper.cards().getFirst();
        var credential = mapper.cardCredentials().getFirst();
        var instrument = mapper.cardInstruments().getFirst();
        long paymentCardId = id(card, "paymentCardId");
        long instrumentId = id(instrument, "instrumentId");
        var token = mapper.instrumentTokens().getFirst();
        var protect = vault.cardKeyProtects().getFirst();
        String key = CardSecurityCodeClientImpl.key(instrumentId);
        long ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
        Instant tokenExpiresAt = ((LocalDateTime)token.get("expiresAt")).toInstant(ZoneOffset.UTC);
        assertAll(() -> assertEquals(List.of(1, 1, 1), counts()),
            () -> assertEquals("41111111", card.get("cardBin")), () -> assertEquals("1111", card.get("panLast4")),
            () -> assertTrue(((String)card.get("cardFingerprint")).matches("[0-9a-f]{64}"), "卡指纹格式"),
            () -> assertFalse(contains((byte[])card.get("encryptedPan"), PAN), "卡号密文包含卡号"),
            () -> assertEquals(paymentCardId, id(credential, "paymentCardId")),
            () -> assertFalse(contains((byte[])credential.get("encryptedDetails"), CARDHOLDER_NAME), "凭证密文包含持卡人"),
            () -> assertEquals(id(credential, "credentialId"), id(instrument, "credentialId")),
            () -> assertEquals(Long.parseLong(CLIENT_ID), id(instrument, "clientId")),
            () -> assertEquals(String.valueOf(instrumentId), token.get("instrumentRef")),
            () -> assertEquals(1, vault.cardKeyProtects().size()),
            () -> assertEquals("payment-card-" + paymentCardId, protect.idempotencyKey()),
            () -> assertEquals(32, protect.dataKeyBytes()),
            () -> assertTrue(((String)card.get("dekVaultToken")).matches("[A-Za-z0-9_-]{43}"), "DEK Vault Token"),
            () -> assertEquals(ENCRYPTED_CVV_BYTES, Base64.getDecoder().decode(redis.opsForValue().get(key)).length),
            () -> assertTrue(ttlMillis > 0, "CVV 未设置存活时间"),
            () -> assertFalse(Instant.now().plusMillis(ttlMillis).isAfter(tokenExpiresAt.plusSeconds(1)),
                "CVV 存活时间超过工具期限"),
            () -> assertFalse(mapper.executionText().contains(PAN), "执行链路与卡表文本列含卡号"));
    }

    /** 少于 16 位的卡号：BIN 取前 6 位，后四位照存。 */
    @Test
    void test_shortPanBin() {
        var payment = newIntent();

        int status = applyStatus(payment.intentId, payment.submissionId, card(SHORT_PAN, EXPIRY_YEAR, CARDHOLDER_NAME));

        var card = mapper.cards().getFirst();
        assertAll(() -> assertEquals(HttpStatus.OK.value(), status), () -> assertEquals("378282", card.get("cardBin")),
            () -> assertEquals("0005", card.get("panLast4")));
    }

    /** 同一卡号、同一凭证在另一笔交易中使用：不新增卡与凭证、不再托管 DEK，只取回原 DEK 并新增交易工具。 */
    @Test
    void test_sameCardReused() {
        newPayment();
        newPayment();

        assertAll(() -> assertEquals(List.of(1, 1, 2), counts()),
            () -> assertEquals(1, vault.cardKeyProtects().size()),
            () -> assertEquals(1, vault.cardKeyRevealCount(), "第二笔取回已有卡的 DEK"));
    }

    /** 同一卡号换有效期、换持卡人各新增一条凭证；持卡人只有大小写和空白差异时复用凭证。 */
    @Test
    void test_credentialPerExpiryAndName() {
        newPayment();

        int otherExpiry = applyNew(card(PAN, "2031", CARDHOLDER_NAME));
        int otherName = applyNew(card(PAN, EXPIRY_YEAR, "ANOTHER HOLDER"));
        int sameNormalized = applyNew(card(PAN, EXPIRY_YEAR, "  test   Cardholder "));

        assertAll(() -> assertEquals(List.of(200, 200, 200), List.of(otherExpiry, otherName, sameNormalized)),
            () -> assertEquals(List.of(1, 3, 4), counts()), () -> assertEquals(1, vault.cardKeyProtects().size()));
    }

    /** 按明文卡号定位：已保存的卡号返回对应的卡，未保存的返回空。 */
    @Test
    void test_findCardByPan() {
        newPayment();

        var found = cardStorageService.findCard(PAN);

        assertAll(() -> assertEquals(id(mapper.cards().getFirst(), "paymentCardId"), found.getPaymentCardId()),
            () -> assertNull(cardStorageService.findCard(OTHER_PAN)));
    }

    /** 不同 Intent 并发首次使用同一张新卡：两次都成功，只留一张卡、一条凭证。 */
    @Test
    void test_concurrentFirstUse() throws Exception {
        var first = newIntent();
        var second = newIntent();
        var start = new CountDownLatch(1);

        var firstStatus = CompletableFuture.supplyAsync(() -> awaitAndApply(start, first));
        var secondStatus = CompletableFuture.supplyAsync(() -> awaitAndApply(start, second));
        start.countDown();

        assertAll(() -> assertEquals(200, firstStatus.get(30, TimeUnit.SECONDS)),
            () -> assertEquals(200, secondStatus.get(30, TimeUnit.SECONDS)),
            () -> assertEquals(List.of(1, 1, 2), counts()));
    }

    /** 读卡：经 Vault 取回 DEK 解密，Platform 取得的卡值与申请一致；CVV 领取后删除，再次读卡拒绝。 */
    @Test
    void test_revealReturnsStoredCardOnce() {
        channel.pending("T-CARD-3DS", "https://3ds.local.invalid/c");
        var paid = newPayment();

        var response = paid.pay();

        long attemptId = attemptId(response);
        long instrumentId = id(mapper.cardInstruments().getFirst(), "instrumentId");
        assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), response.getBody().get("status")),
            () -> assertEquals(PlatformMocker.materialDigest(PAN, EXPIRY_MONTH, EXPIRY_YEAR, CARDHOLDER_NAME, CVV),
                channel.only().materialDigest(), "读卡取得的卡值与申请不一致"),
            () -> assertFalse(redis.hasKey(CardSecurityCodeClientImpl.key(instrumentId)), "CVV 领取后仍存在"),
            () -> assertEquals(HttpStatus.BAD_REQUEST.value(), reveal(paid.token, attemptId), "CVV 不能再次领取"),
            () -> assertEquals(2, vault.cardKeyRevealCount(), "每次读卡都先取回 DEK 解密"));
    }

    /** 同一张卡的两笔交易各自暂存 CVV：一笔读卡领取后，另一笔的 CVV 仍在。 */
    @Test
    void test_securityCodePerInstrument() {
        channel.succeeded("T-CARD-CVV");
        var paid = newPayment();
        newPayment();
        var instruments = mapper.cardInstruments();

        paid.pay();

        String paidKey = CardSecurityCodeClientImpl.key(id(instruments.get(0), "instrumentId"));
        String otherKey = CardSecurityCodeClientImpl.key(id(instruments.get(1), "instrumentId"));
        assertAll(() -> assertFalse(redis.hasKey(paidKey), "已读卡交易的 CVV 仍存在"),
            () -> assertTrue(redis.hasKey(otherKey), "另一笔交易的 CVV 被领取"));
    }

    /** 同卡重放：返回原 Token，不新增任何记录、不访问 Vault，CVV 保持原样。 */
    @Test
    void test_replaySameCard() {
        var paid = newPayment();
        String key = CardSecurityCodeClientImpl.key(id(mapper.cardInstruments().getFirst(), "instrumentId"));
        String securityCode = redis.opsForValue().get(key);

        String replayed = applyCard(String.valueOf(paid.intentId), paid.submissionId);

        assertAll(() -> assertEquals(paid.token, replayed), () -> assertEquals(List.of(1, 1, 1), counts()),
            () -> assertEquals(1, vault.cardKeyProtects().size()),
            () -> assertEquals(0, vault.cardKeyRevealCount(), "重放只比较指纹与凭证摘要"),
            () -> assertEquals(securityCode, redis.opsForValue().get(key), "重放覆盖了 CVV"));
    }

    /** 换卡重放：原提交不可更换卡工具，返回 409，不新增记录。 */
    @Test
    void test_replayDifferentCardRejected() {
        var paid = newPayment();

        int status = applyStatus(paid.intentId, paid.submissionId, card(OTHER_PAN, EXPIRY_YEAR, CARDHOLDER_NAME));

        assertAll(() -> assertEquals(HttpStatus.CONFLICT.value(), status), () -> assertEquals(List.of(1, 1, 1), counts()),
            () -> assertEquals(1, vault.cardKeyProtects().size()));
    }

    /** 卡号 Luhn 失败或卡已过期：400，不写任何卡数据与 Token，不访问 Vault。 */
    @ParameterizedTest
    @CsvSource({"4111111111111112, 2030", "4111111111111111, 2020"})
    void test_invalidCardRejected(String pan, String expiryYear) {
        var payment = newIntent();

        int status = applyStatus(payment.intentId, payment.submissionId, card(pan, expiryYear, CARDHOLDER_NAME));

        assertAll(() -> assertEquals(HttpStatus.BAD_REQUEST.value(), status), () -> assertEquals(List.of(0, 0, 0), counts()),
            () -> assertTrue(mapper.instrumentTokens().isEmpty()), () -> assertTrue(vault.cardKeyProtects().isEmpty()));
    }

    /** Vault 不可用：申请失败关闭（503），不写任何卡数据与 Token。 */
    @Test
    void test_vaultUnavailableFailsClosed() {
        vault.failCardKeyProtect(MockHttpFault.json(503, Map.of("type", "about:blank", "title", "Service Unavailable",
            "status", 503, "code", "DEPENDENCY_UNAVAILABLE")));
        var payment = newIntent();

        int status = applyStatus(payment.intentId, payment.submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME));

        assertAll(() -> assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), status),
            () -> assertEquals(List.of(0, 0, 0), counts()), () -> assertTrue(mapper.instrumentTokens().isEmpty()),
            () -> assertEquals(1, vault.cardKeyProtects().size()));
    }

    /** 申请、读卡与支付全过程的日志不含卡号、持卡人、卡指纹、凭证摘要与 DEK Vault Token。 */
    @Test
    void test_cardValuesNotLogged(CapturedOutput output) {
        channel.succeeded("T-CARD-LOG");
        newPayment().pay();

        var card = mapper.cards().getFirst();
        String logs = output.getAll();
        assertAll(() -> assertFalse(logs.contains(PAN), "日志包含卡号"),
            () -> assertFalse(logs.contains(CARDHOLDER_NAME), "日志包含持卡人"),
            () -> assertFalse(logs.contains((String)card.get("cardFingerprint")), "日志包含卡指纹"),
            () -> assertFalse(logs.contains((String)mapper.cardCredentials().getFirst().get("credentialDigest")),
                "日志包含凭证摘要"),
            () -> assertFalse(logs.contains((String)card.get("dekVaultToken")), "日志包含 DEK Vault Token"));
    }

    /** 卡、卡凭证、卡交易工具三表行数。 */
    List<Integer> counts() {
        return List.of(mapper.cards().size(), mapper.cardCredentials().size(), mapper.cardInstruments().size());
    }

    /** 新建 Intent 并以指定卡输入申请工具，返回 HTTP 状态。 */
    int applyNew(Map<String, String> card) {
        var payment = newIntent();
        return applyStatus(payment.intentId, payment.submissionId, card);
    }

    /** 等待同时起跑后以主卡号申请，返回 HTTP 状态。 */
    int awaitAndApply(CountDownLatch start, Payment payment) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return applyStatus(payment.intentId, payment.submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME));
    }

    /** 以指定卡输入申请工具，返回 HTTP 状态（含 4xx/5xx）。 */
    int applyStatus(long intentId, long submissionId, Map<String, String> card) {
        var body = Map.of("clientId", CLIENT_ID, "intentId", String.valueOf(intentId), "sourceSubmissionId",
            String.valueOf(submissionId), "paymentMethodCode", PAYMENT_METHOD_CODE, "card", card);
        try {
            return noRetryClient().post().uri("/payment-instrument/v1/card-tokens").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** 不自动重发的客户端：默认 Apache HttpClient 收到 503 会自动重发，导致一次申请被执行两次。 */
    RestClient noRetryClient() {
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).baseUrl(baseUrl()).build();
    }

    /** 以 Platform 身份直接调用读卡接口，返回 HTTP 状态。 */
    int reveal(String token, long attemptId) {
        try {
            return client().post().uri("/payment-card/v1/channel-card-materials/reveal")
                .header("X-Caller-Service", "platform-support-service").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("paymentInstrumentToken", token, "paymentAttemptId", String.valueOf(attemptId), "purpose",
                    "CHANNEL_EXECUTION"))
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** 卡输入：月份与 CVV 取默认值。 */
    static Map<String, String> card(String pan, String expiryYear, String cardholderName) {
        return Map.of("pan", pan, "expiryMonth", EXPIRY_MONTH, "expiryYear", expiryYear, "cardholderName",
            cardholderName, "cvv", CVV);
    }

    /** 查询结果中的数字列。 */
    static long id(Map<String, Object> row, String column) {
        return ((Number)row.get(column)).longValue();
    }

    /** 密文字节中是否出现明文的 UTF-8 字节序列。 */
    static boolean contains(byte[] haystack, String needle) {
        byte[] target = needle.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i + target.length <= haystack.length; i++) {
            int j = 0;
            while (j < target.length && haystack[i + j] == target[j]) {
                j++;
            }
            if (j == target.length) {
                return true;
            }
        }
        return false;
    }
}
