package com.paytech.card.auto.instrument;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.instrument.CardInstrumentApi;
import com.paytech.card.integration.impl.CardSecurityCodeClientImpl;
import com.paytech.card.test.base.AbstractCardApiTest;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * 卡交易工具创建（design/05 §2.2 两段创建的 card 一段；原 Payment CardStorageTest 的卡侧用例）：从 card 的真实 HTTP 入口，经真实 Processor、
 * mor_card 卡 / 凭证 / 工具三表、Redis CVV 暂存，以及真实 Vault 客户端访问 Vault 测试 HTTP 服务（按 REFERENCE 语义托管 DEK）。
 *
 * <p>
 * 覆盖：三表只有密文与 BIN/后四位/指纹，BIN 按卡号长度取 8 或 6 位；同一卡号只存一张卡、一把 DEK，有效期或持卡人不同才新增凭证；并发首用只留一张卡；
 * 同一来源提交幂等返回原工具且不访问 Vault、不覆盖 CVV，换卡 409；Luhn 失败与过期卡 400；Vault 不可用 503；非 Payment 调用方 404；卡值不进日志。
 *
 * @see CardInstrumentApi
 */
@ExtendWith(OutputCaptureExtension.class)
class CardInstrumentHttpTest extends AbstractCardApiTest {
    private static final String OTHER_PAN = "5555555555554444";
    /** 15 位公开测试卡号（Amex），BIN 取 6 位。 */
    private static final String SHORT_PAN = "378282246310005";
    /** IV(12) + 3 位 CVV + Tag(16)。 */
    private static final int ENCRYPTED_CVV_BYTES = 31;

    @Autowired
    private StringRedisTemplate redis;

    /** 新卡：三表各一条，只有密文与 BIN/后四位/指纹；响应含工具引用、掩码卡号、期限与风控属性；DEK 交 Vault；CVV 加密暂存且 TTL 不超过工具期限。 */
    @Test
    void test_createStoresEncryptedCard() {
        long submissionId = submission();
        var created = createInstrument(instrumentBody(submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false));

        var card = mapper.cards().getFirst();
        var credential = mapper.cardCredentials().getFirst();
        var instrument = mapper.cardInstruments().getFirst();
        long instrumentId = id(instrument, "instrumentId");
        long paymentCardId = id(card, "paymentCardId");
        var protect = vault.cardKeyProtects().getFirst();
        String key = CardSecurityCodeClientImpl.key(instrumentId);
        long ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
        Instant expiresAt = ((LocalDateTime)instrument.get("expiresAt")).toInstant(ZoneOffset.UTC);
        @SuppressWarnings("unchecked")
        var risk = (Map<String, Object>)created.get("riskAttributes");
        assertAll(() -> assertEquals(List.of(1, 1, 1), counts()),
            () -> assertEquals(String.valueOf(instrumentId), created.get("instrumentRef")),
            () -> assertEquals("411111******1111", created.get("maskedPan")),
            () -> assertEquals(expiresAt, Instant.parse((String)created.get("expiresAt"))),
            () -> assertEquals("41111111", risk.get("bin")), () -> assertEquals(card.get("cardFingerprint"), risk.get("fingerprint")),
            () -> assertEquals("41111111", card.get("cardBin")), () -> assertEquals("1111", card.get("panLast4")),
            () -> assertTrue(((String)card.get("cardFingerprint")).matches("[0-9a-f]{64}"), "卡指纹格式"),
            () -> assertFalse(contains((byte[])card.get("encryptedPan"), PAN), "卡号密文包含卡号"),
            () -> assertFalse(contains((byte[])credential.get("encryptedDetails"), CARDHOLDER_NAME), "凭证密文包含持卡人"),
            () -> assertEquals(id(credential, "credentialId"), id(instrument, "credentialId")),
            () -> assertEquals(submissionId, id(instrument, "sourceSubmissionId")),
            () -> assertEquals("payment-card-" + paymentCardId, protect.idempotencyKey()),
            () -> assertEquals(32, protect.dataKeyBytes()),
            () -> assertTrue(((String)card.get("dekVaultToken")).matches("[A-Za-z0-9_-]{43}"), "DEK Vault Token"),
            () -> assertEquals(ENCRYPTED_CVV_BYTES, Base64.getDecoder().decode(redis.opsForValue().get(key)).length),
            () -> assertTrue(ttlMillis > 0, "CVV 未设置存活时间"),
            () -> assertFalse(Instant.now().plusMillis(ttlMillis).isAfter(expiresAt.plusSeconds(1)), "CVV 存活时间超过工具期限"));
        transactions.assertOutsideTransactions();
    }

    /** 少于 16 位的卡号：BIN 取前 6 位，掩码同样前 6 后 4。 */
    @Test
    void test_shortPanBin() {
        var created = createInstrument(instrumentBody(submission(), card(SHORT_PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false));

        assertAll(() -> assertEquals("378282", mapper.cards().getFirst().get("cardBin")),
            () -> assertEquals("378282******0005", created.get("maskedPan")));
    }

    /** 同一卡号、同一凭证在另一来源提交中使用：不新增卡与凭证、不再托管 DEK，只取回原 DEK 并新增交易工具。 */
    @Test
    void test_sameCardReused() {
        createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false));
        createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false));

        assertAll(() -> assertEquals(List.of(1, 1, 2), counts()), () -> assertEquals(1, vault.cardKeyProtects().size()),
            () -> assertEquals(1, vault.cardKeyRevealCount(), "第二次取回已有卡的 DEK"));
    }

    /** 同一卡号换有效期、换持卡人各新增一条凭证；持卡人只有大小写和空白差异时复用凭证。 */
    @Test
    void test_credentialPerExpiryAndName() {
        createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false));
        createInstrument(instrumentBody(submission(), card(PAN, "2031", CARDHOLDER_NAME), null, false));
        createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, "ANOTHER HOLDER"), null, false));
        createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, "  test   Cardholder "), null, false));

        assertAll(() -> assertEquals(List.of(1, 3, 4), counts()), () -> assertEquals(1, vault.cardKeyProtects().size()));
    }

    /** 不同来源提交并发首次使用同一张新卡：两次都成功，只留一张卡、一条凭证。 */
    @Test
    void test_concurrentFirstUse() throws Exception {
        var start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> awaitAndCreate(start));
        var second = CompletableFuture.supplyAsync(() -> awaitAndCreate(start));
        start.countDown();

        assertAll(() -> assertEquals(200, first.get(30, TimeUnit.SECONDS)), () -> assertEquals(200, second.get(30, TimeUnit.SECONDS)),
            () -> assertEquals(List.of(1, 1, 2), counts()));
    }

    /** 同一来源提交重放（Payment 两段创建的重试）：返回同一工具，不新增记录、不访问 Vault，CVV 保持原样。 */
    @Test
    void test_sameSubmissionIdempotent() {
        long submissionId = submission();
        var body = instrumentBody(submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), "70001", true);
        var created = createInstrument(body);
        String key = CardSecurityCodeClientImpl.key(Long.parseLong((String)created.get("instrumentRef")));
        String securityCode = redis.opsForValue().get(key);

        var replayed = createInstrument(body);

        assertAll(() -> assertEquals(created.get("instrumentRef"), replayed.get("instrumentRef")),
            () -> assertEquals(created.get("expiresAt"), replayed.get("expiresAt")),
            () -> assertEquals(List.of(1, 1, 1), counts()), () -> assertEquals(1, vault.cardKeyProtects().size()),
            () -> assertEquals(0, vault.cardKeyRevealCount(), "重放只比较指纹与凭证摘要"),
            () -> assertEquals(securityCode, redis.opsForValue().get(key), "重放覆盖了 CVV"));
    }

    /** 同一来源提交换卡、换 Customer 或改变保存意愿：409，不新增记录。 */
    @Test
    void test_sameSubmissionDifferentCardRejected() {
        long submissionId = submission();
        createInstrument(instrumentBody(submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), "70001", true));

        int otherCard = create(instrumentBody(submissionId, card(OTHER_PAN, EXPIRY_YEAR, CARDHOLDER_NAME), "70001", true));
        int otherCustomer = create(instrumentBody(submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), "70002", true));
        int noConsent = create(instrumentBody(submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), "70001", false));

        assertAll(() -> assertEquals(List.of(409, 409, 409), List.of(otherCard, otherCustomer, noConsent)),
            () -> assertEquals(1, mapper.cardInstruments().size()), () -> assertEquals(1, vault.cardKeyProtects().size()));
    }

    /** 卡号 Luhn 失败或卡已过期：400，不写任何卡数据，不访问 Vault。 */
    @ParameterizedTest
    @CsvSource({"4111111111111112, 2030", "4111111111111111, 2020"})
    void test_invalidCardRejected(String pan, String expiryYear) {
        int status = create(instrumentBody(submission(), card(pan, expiryYear, CARDHOLDER_NAME), null, false));

        assertAll(() -> assertEquals(400, status), () -> assertEquals(List.of(0, 0, 0), counts()),
            () -> assertTrue(vault.cardKeyProtects().isEmpty()));
    }

    /** Vault 不可用：创建失败关闭（503），不写任何卡数据。 */
    @Test
    void test_vaultUnavailableFailsClosed() {
        vault.failCardKeyProtect(MockHttpFault.json(503, Map.of("type", "about:blank", "title", "Service Unavailable",
            "status", 503, "code", "DEPENDENCY_UNAVAILABLE")));

        int status = create(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false));

        assertAll(() -> assertEquals(503, status), () -> assertEquals(List.of(0, 0, 0), counts()),
            () -> assertEquals(1, vault.cardKeyProtects().size()));
    }

    /** 只接受 Payment：其他调用方 404，不写任何卡数据；同时提供卡与已存卡为 400。 */
    @Test
    void test_wrongCallerRejected() {
        var body = instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false);

        var acquiring = call(HttpMethod.POST, "/card/v1/instruments", CardApiConstants.ACQUIRING_SERVICE, body);
        var platform = call(HttpMethod.POST, "/card/v1/instruments", CardApiConstants.PLATFORM_SUPPORT_SERVICE, body);
        body.put("storedCard", Map.of("storedCardToken", "sc_" + "A".repeat(28), "cvv", CVV));
        int bothSources = create(body);

        assertAll(() -> assertEquals(List.of(404, 404, 400), List.of(acquiring.status(), platform.status(), bothSources)),
            () -> assertEquals(List.of(0, 0, 0), counts()), () -> assertTrue(vault.cardKeyProtects().isEmpty()));
    }

    /** 创建与重放全过程的日志不含卡号、持卡人、CVV、卡指纹、凭证摘要与 DEK Vault Token。 */
    @Test
    void test_cardValuesNotLogged(CapturedOutput output) {
        var body = instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false);
        createInstrument(body);
        createInstrument(body);

        var card = mapper.cards().getFirst();
        String logs = output.getAll();
        assertAll(() -> assertFalse(logs.contains(PAN), "日志包含卡号"),
            () -> assertFalse(logs.contains(CARDHOLDER_NAME), "日志包含持卡人"),
            () -> assertFalse(logs.contains("\"cvv\""), "日志包含 CVV 字段"),
            () -> assertFalse(logs.contains((String)card.get("cardFingerprint")), "日志包含卡指纹"),
            () -> assertFalse(logs.contains((String)mapper.cardCredentials().getFirst().get("credentialDigest")), "日志包含凭证摘要"),
            () -> assertFalse(logs.contains((String)card.get("dekVaultToken")), "日志包含 DEK Vault Token"));
    }

    /** 卡、卡凭证、卡交易工具三表行数。 */
    List<Integer> counts() {
        return List.of(mapper.cards().size(), mapper.cardCredentials().size(), mapper.cardInstruments().size());
    }

    /** 以 Payment 身份创建工具，返回 HTTP 状态。 */
    int create(Map<String, Object> body) {
        return call(HttpMethod.POST, "/card/v1/instruments", CardApiConstants.PAYMENT_SERVICE, body).status();
    }

    /** 等待同时起跑后以主卡号和新来源提交创建，返回 HTTP 状态。 */
    int awaitAndCreate(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return create(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false));
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
