package com.paytech.payment.auto.card;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody;
import com.paytech.card.api.instrument.response.CreateCardInstrumentResponseBody;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.payment.api.instrument.PaymentInstrumentApi;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.CardApiMocker;
import com.paytech.payment.test.mocker.PlatformMocker;

/**
 * 卡工具申请的 Payment 一侧（design/05 §2.2 两段创建；卡的加密存储、DEK 与 CVV 暂存见 card 服务的 CardInstrumentHttpTest）：从工具申请、
 * pay 的真实 HTTP 入口，经真实 card 出站客户端访问 card-api 测试 HTTP 服务，再在本地事务保存支付工具 Token。
 *
 * <p>
 * 覆盖：卡原值只交给 card、Token 行只存 card 的工具引用、期限与风控属性；BIN 与指纹取自 card 响应；同卡在另一交易重新申请；有效期与持卡人原样转交；
 * Payment 库不含卡号；并发首用；凭票据读卡与票据一次性；不同交易的工具各自签票；原提交同卡重放与换卡 409；card 业务拒绝 400 与 card 不可用 503
 * 时不写 Token；卡值不进日志。卡号均为公开测试卡号。
 *
 * <p>
 * BIN 库卡属性快照（BIN 库需求 AC-02、AC-04、AC-08）：识别时 Token 冻结 8 列；未识别仍成功且只记版本号；没有生效版本时全空；
 * 原提交重放沿用首次冻结的快照，BIN 库切换版本后的新工具使用新版本；旧 Card 缺快照对象时按未识别（全空）并记 WARN；
 * Card 快照违约时失败关闭（500），不写 Token。
 *
 * @see PaymentInstrumentApi
 */
@ExtendWith(OutputCaptureExtension.class)
class CardStorageTest extends AbstractPayCardTest {
    private static final String OTHER_PAN = "5555555555554444";
    /** 15 位公开测试卡号（Amex），BIN 取 6 位。 */
    private static final String SHORT_PAN = "378282246310005";

    @Autowired
    private PlatformMocker platform;

    private static final String CARD_LEVEL = "CLASSIC";
    private static final String ISSUER_REGION = "GCC";

    /** 本类公共：MID 候选与风控放行，pay 用例按需配置渠道结论。 */
    @BeforeEach
    void setUpClassMocks() {
        channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 新卡：卡原值只发给 card；Token 行的工具引用、期限、BIN 与指纹取自 card 响应，期限不晚于申请窗口；访客无 Customer、不保存。 */
    @Test
    void test_applyStoresEncryptedCard() {
        var paid = newPayment();

        var create = cardApi.creates().getFirst();
        var token = mapper.instrumentTokens().getFirst();
        Instant tokenExpiresAt = ((LocalDateTime)token.get("expiresAt")).toInstant(ZoneOffset.UTC);
        assertAll(() -> assertEquals(1, cardApi.creates().size()),
            () -> assertEquals(List.of(CLIENT_ID, String.valueOf(paid.submissionId)),
                List.of(create.getClientId(), create.getSourceSubmissionId())),
            () -> assertEquals(PAN, create.getCard().getPan()), () -> assertEquals(CVV, create.getCard().getCvv()),
            () -> assertNull(create.getStoredCard()), () -> assertNull(create.getCustomerId()),
            () -> assertFalse(create.getConsentToSave()),
            () -> assertTrue(token.get("instrumentRef").toString().matches("[1-9][0-9]{0,18}"), "工具引用为 card 工具 ID"),
            () -> assertEquals("41111111", token.get("cardBin")),
            () -> assertEquals(CardApiMocker.fingerprint(PAN), token.get("cardFingerprint")),
            () -> assertNull(token.get("customerId")), () -> assertEquals(Boolean.FALSE, consent(token)),
            () -> assertFalse(tokenExpiresAt.isAfter(Instant.parse(create.getExpiresAt())), "Token 期限超过申请上限"),
            () -> assertFalse(mapper.executionText().contains(PAN), "Payment 库包含卡号"),
            () -> assertTrue(cardApi.ticketRequests().isEmpty(), "申请工具时不签发读卡票据"));
    }

    /** 少于 16 位的卡号：Token 行保存 card 返回的 6 位 BIN。 */
    @Test
    void test_shortPanBin() {
        var payment = newIntent();

        int status = applyStatus(payment.intentId, payment.submissionId, card(SHORT_PAN, EXPIRY_YEAR, CARDHOLDER_NAME));

        assertAll(() -> assertEquals(HttpStatus.OK.value(), status),
            () -> assertEquals("378282", mapper.instrumentTokens().getFirst().get("cardBin")));
    }

    /** 同一卡号在另一笔交易中使用：各自以自己的来源提交申请，得到不同工具引用、相同卡指纹。 */
    @Test
    void test_sameCardReused() {
        newPayment();
        newPayment();

        var tokens = mapper.instrumentTokens();
        assertAll(() -> assertEquals(2, cardApi.creates().size()),
            () -> assertNotEquals(cardApi.creates().get(0).getSourceSubmissionId(),
                cardApi.creates().get(1).getSourceSubmissionId()),
            () -> assertNotEquals(tokens.get(0).get("instrumentRef"), tokens.get(1).get("instrumentRef")),
            () -> assertEquals(tokens.get(0).get("cardFingerprint"), tokens.get(1).get("cardFingerprint")));
    }

    /** 有效期与持卡人原样交给 card（是否新建凭证由 card 判断），Payment 不规范化卡输入。 */
    @Test
    void test_credentialPerExpiryAndName() {
        newPayment();

        int otherExpiry = applyNew(card(PAN, "2031", CARDHOLDER_NAME));
        int otherName = applyNew(card(PAN, EXPIRY_YEAR, "ANOTHER HOLDER"));
        int sameNormalized = applyNew(card(PAN, EXPIRY_YEAR, "  test   Cardholder "));

        var creates = cardApi.creates();
        assertAll(() -> assertEquals(List.of(200, 200, 200), List.of(otherExpiry, otherName, sameNormalized)),
            () -> assertEquals(List.of(EXPIRY_YEAR, "2031", EXPIRY_YEAR, EXPIRY_YEAR),
                creates.stream().map(create -> create.getCard().getExpiryYear()).toList()),
            () -> assertEquals(List.of(CARDHOLDER_NAME, CARDHOLDER_NAME, "ANOTHER HOLDER", "  test   Cardholder "),
                creates.stream().map(create -> create.getCard().getCardholderName()).toList()));
    }

    /** Payment 不再按卡号定位卡：本库只有工具引用与风控属性，任何文本列都不含卡号或持卡人。 */
    @Test
    void test_findCardByPan() {
        newPayment();
        applyNew(card(OTHER_PAN, EXPIRY_YEAR, CARDHOLDER_NAME));

        String text = mapper.executionText();
        assertAll(() -> assertEquals(2, mapper.instrumentTokens().size()),
            () -> assertFalse(text.contains(PAN) || text.contains(OTHER_PAN), "Payment 库包含卡号"),
            () -> assertFalse(text.contains(CARDHOLDER_NAME), "Payment 库包含持卡人"));
    }

    /** 不同 Intent 并发首次使用同一张新卡：两次都成功，各一个工具与 Token。 */
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
            () -> assertEquals(2, mapper.instrumentTokens().size()), () -> assertEquals(2, cardApi.creates().size()));
    }

    /** 读卡：Platform 凭票据从 card 取得与申请一致的卡值；票据只能用一次，再次读卡被拒且无卡数据。 */
    @Test
    void test_revealReturnsStoredCardOnce() {
        channel.pending("T-CARD-3DS", "https://3ds.local.invalid/c");
        var paid = newPayment();

        var response = paid.pay();

        String ticket = cardApi.tickets().getFirst();
        assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), response.getBody().get("status")),
            () -> assertEquals(PlatformMocker.materialDigest(PAN, EXPIRY_MONTH, EXPIRY_YEAR, CARDHOLDER_NAME, CVV),
                channel.only().materialDigest(), "读卡取得的卡值与申请不一致"),
            () -> assertArrayEquals(new String[] {"REJECTED 400", null}, platform.reveal(ticket), "票据不能再次使用"),
            () -> assertEquals(1, cardApi.tickets().size()));
    }

    /** 同一张卡的两笔交易：只为支付的那笔工具签发票据，另一笔不受影响。 */
    @Test
    void test_securityCodePerInstrument() {
        channel.succeeded("T-CARD-CVV");
        var paid = newPayment();
        newPayment();
        var tokens = mapper.instrumentTokens();

        paid.pay();

        assertEquals(List.of(tokens.get(0).get("instrumentRef")),
            cardApi.ticketRequests().stream().map(request -> (Object)request.getInstrumentRef()).toList());
    }

    /** 同卡重放：返回原 Token，以同一来源提交再调 card 得到原工具，不新增 Token。 */
    @Test
    void test_replaySameCard() {
        var paid = newPayment();

        String replayed = applyCard(String.valueOf(paid.intentId), paid.submissionId);

        assertAll(() -> assertEquals(paid.token, replayed), () -> assertEquals(1, mapper.instrumentTokens().size()),
            () -> assertEquals(2, cardApi.creates().size()),
            () -> assertEquals(cardApi.creates().get(0).getSourceSubmissionId(),
                cardApi.creates().get(1).getSourceSubmissionId()));
    }

    /** 换卡重放：card 以 409 拒绝更换卡工具，Payment 返回 409，不新增 Token。 */
    @Test
    void test_replayDifferentCardRejected() {
        var paid = newPayment();

        int status = applyStatus(paid.intentId, paid.submissionId, card(OTHER_PAN, EXPIRY_YEAR, CARDHOLDER_NAME));

        assertAll(() -> assertEquals(HttpStatus.CONFLICT.value(), status),
            () -> assertEquals(1, mapper.instrumentTokens().size()));
    }

    /** card 业务拒绝（卡号 Luhn 失败、卡已过期）：Payment 返回 400，不写 Token。 */
    @ParameterizedTest
    @CsvSource({"4111111111111112, 2030", "4111111111111111, 2020"})
    void test_invalidCardRejected(String pan, String expiryYear) {
        cardApi.rejectInstruments(CardApiMocker.fault(400, CommonErrorCode.BUSINESS_CHECK_FAILED));
        var payment = newIntent();

        int status = applyStatus(payment.intentId, payment.submissionId, card(pan, expiryYear, CARDHOLDER_NAME));

        CreateCardInstrumentRequestBody create = cardApi.creates().getFirst();
        assertAll(() -> assertEquals(HttpStatus.BAD_REQUEST.value(), status),
            () -> assertEquals(List.of(pan, expiryYear), List.of(create.getCard().getPan(), create.getCard().getExpiryYear())),
            () -> assertTrue(mapper.instrumentTokens().isEmpty()));
    }

    /** card 不可用（含其 Vault 或 Redis 不可用）：申请失败关闭（503），不写 Token。 */
    @Test
    void test_vaultUnavailableFailsClosed() {
        cardApi.rejectInstruments(CardApiMocker.fault(503, CommonErrorCode.SERVICE_UNAVAILABLE));
        var payment = newIntent();

        int status = applyStatus(payment.intentId, payment.submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME));

        assertAll(() -> assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), status),
            () -> assertTrue(mapper.instrumentTokens().isEmpty()), () -> assertEquals(1, cardApi.creates().size()));
    }

    /** 申请、签票、读卡与支付全过程的日志不含卡号、持卡人、CVV 请求体、卡指纹与读卡票据。 */
    @Test
    void test_cardValuesNotLogged(CapturedOutput output) {
        channel.succeeded("T-CARD-LOG");
        newPayment().pay();

        String logs = output.getAll();
        assertAll(() -> assertFalse(logs.contains(PAN), "日志包含卡号"),
            () -> assertFalse(logs.contains(CARDHOLDER_NAME), "日志包含持卡人"),
            () -> assertFalse(logs.contains(CardApiMocker.fingerprint(PAN)), "日志包含卡指纹"),
            () -> assertFalse(logs.contains(cardApi.tickets().getFirst()), "日志包含读卡票据"));
    }

    /** 识别的 BIN：Token 冻结卡组织、借贷记、等级、产品、发卡国、区域、BIN 库版本与命中 8 位前缀。 */
    @Test
    void test_cardAttributesFrozen() {
        cardApi.binRange(CardApiMocker.bin(PAN), visaCredit());

        newPayment();

        assertEquals(List.of(CardBrand.VISA.getCode(), CardFundingType.CREDIT.getCode(), CARD_LEVEL,
            CardProduct.CONSUMER.getCode(), COUNTRY_CODE, ISSUER_REGION, CardApiMocker.BIN_LIBRARY_VERSION_NO, 8),
            snapshot(mapper.instrumentTokens().getFirst()));
    }

    /** 未识别的 BIN（AC-04）：工具照常创建，卡属性全空，只记查询时的 BIN 库版本号。 */
    @Test
    void test_cardAttributesUnrecognized() {
        String token = newPayment().token;

        assertAll(() -> assertNotNull(token),
            () -> assertEquals(Arrays.asList(null, null, null, null, null, null, CardApiMocker.BIN_LIBRARY_VERSION_NO, null),
                snapshot(mapper.instrumentTokens().getFirst())));
    }

    /** 没有生效 BIN 库版本：工具照常创建，快照 8 列全空。 */
    @Test
    void test_cardAttributesWithoutActiveLibrary() {
        cardApi.binRange(CardApiMocker.bin(PAN), visaCredit());
        cardApi.activeBinLibrary(null);

        newPayment();

        assertEquals(Arrays.asList(null, null, null, null, null, null, null, null),
            snapshot(mapper.instrumentTokens().getFirst()));
    }

    /**
     * 快照冻结（AC-02、B4）：首次按 6 位号段识别；BIN 库切到新版本（新增 8 位号段、另一卡组织）后原提交重放沿用原快照，新工具使用新版本。
     */
    @Test
    void test_replayKeepsFrozenAttributes() {
        cardApi.binRange(PAN.substring(0, 6), visaCredit());
        var paid = newPayment();
        cardApi.activeBinLibrary(CardApiMocker.BIN_LIBRARY_VERSION_NO + 1);
        cardApi.binRange(CardApiMocker.bin(PAN), CardApiMocker.attributes(CardBrand.MASTERCARD.getCode(),
            CardFundingType.DEBIT.getCode(), null, null, COUNTRY_CODE, ISSUER_REGION));

        String replayed = applyCard(String.valueOf(paid.intentId), paid.submissionId);
        newPayment();

        var tokens = mapper.instrumentTokens();
        assertAll(() -> assertEquals(paid.token, replayed), () -> assertEquals(2, tokens.size()),
            () -> assertEquals(List.of(CardBrand.VISA.getCode(), CardFundingType.CREDIT.getCode(), CARD_LEVEL,
                CardProduct.CONSUMER.getCode(), COUNTRY_CODE, ISSUER_REGION, CardApiMocker.BIN_LIBRARY_VERSION_NO, 6),
                snapshot(tokens.get(0)), "重放不得改写已冻结快照"),
            () -> assertEquals(Arrays.asList(CardBrand.MASTERCARD.getCode(), CardFundingType.DEBIT.getCode(), null, null,
                COUNTRY_CODE, ISSUER_REGION, CardApiMocker.BIN_LIBRARY_VERSION_NO + 1, 8), snapshot(tokens.get(1))));
    }

    /** 旧 Card 响应缺少卡属性快照对象：工具照常创建，8 列全空（按未识别），记一条不含卡值的 WARN。 */
    @Test
    void test_cardAttributesMissingFromOldCard(CapturedOutput output) {
        cardApi.binRange(CardApiMocker.bin(PAN), visaCredit());
        cardApi.rawCardAttributes(null);

        String token = newPayment().token;

        String logs = output.getAll();
        assertAll(() -> assertNotNull(token),
            () -> assertEquals(Arrays.asList(null, null, null, null, null, null, null, null),
                snapshot(mapper.instrumentTokens().getFirst())),
            () -> assertTrue(logs.contains("缺少卡属性快照"), "缺对象应记 WARN"),
            () -> assertFalse(logs.contains(PAN), "日志包含卡号"));
    }

    /** Card 卡属性快照违反合同（识别却缺卡组织、未知卡组织、未识别却带属性）：失败关闭返回 500，不写 Token。 */
    @ParameterizedTest
    @MethodSource("invalidCardAttributes")
    void test_invalidCardAttributesRejected(CreateCardInstrumentResponseBody.CardAttributes attributes) {
        cardApi.rawCardAttributes(attributes);
        var payment = newIntent();

        int status = applyStatus(payment.intentId, payment.submissionId, card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME));

        assertAll(() -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), status),
            () -> assertTrue(mapper.instrumentTokens().isEmpty()), () -> assertEquals(1, cardApi.creates().size()));
    }

    /** 违反 Card 合同的卡属性快照。 */
    static Stream<CreateCardInstrumentResponseBody.CardAttributes> invalidCardAttributes() {
        var withoutBrand = CardApiMocker.attributes(null, null, null, null, COUNTRY_CODE, ISSUER_REGION);
        withoutBrand.setBinLibraryVersionNo(CardApiMocker.BIN_LIBRARY_VERSION_NO);
        withoutBrand.setMatchedPrefixLength(8);
        var unknownBrand = CardApiMocker.attributes("UNKNOWN_SCHEME", null, null, null, COUNTRY_CODE, ISSUER_REGION);
        unknownBrand.setBinLibraryVersionNo(CardApiMocker.BIN_LIBRARY_VERSION_NO);
        unknownBrand.setMatchedPrefixLength(8);
        var unrecognizedWithBrand = CardApiMocker.attributes(CardBrand.VISA.getCode(), null, null, null, null, null);
        unrecognizedWithBrand.setRecognized(false);
        return Stream.of(withoutBrand, unknownBrand, unrecognizedWithBrand);
    }

    /** 本类识别号段的卡属性：VISA 信用卡、个人卡、AE 发卡。 */
    static CreateCardInstrumentResponseBody.CardAttributes visaCredit() {
        return CardApiMocker.attributes(CardBrand.VISA.getCode(), CardFundingType.CREDIT.getCode(), CARD_LEVEL,
            CardProduct.CONSUMER.getCode(), COUNTRY_CODE, ISSUER_REGION);
    }

    /** Token 行冻结的 8 列快照，按 DDL 列序；数字列统一为 Integer。 */
    static List<Object> snapshot(Map<String, Object> token) {
        return Arrays.asList(token.get("cardBrand"), token.get("fundingType"), token.get("cardLevel"),
            token.get("cardProduct"), token.get("issuerCountry"), token.get("issuerRegion"),
            integer(token.get("binLibraryVersionNo")), integer(token.get("binMatchLength")));
    }

    /** MySQL INT / TINYINT 映射的数字；NULL 保持为空。 */
    static Integer integer(Object value) {
        return value == null ? null : ((Number)value).intValue();
    }

    /** Token 行的保存意愿（MySQL TINYINT 映射为 Boolean 或数字）。 */
    static Boolean consent(Map<String, Object> token) {
        Object value = token.get("consentToSave");
        return value instanceof Boolean flag ? flag : ((Number)value).intValue() == 1;
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

    /** 卡输入：月份与 CVV 取默认值。 */
    static Map<String, String> card(String pan, String expiryYear, String cardholderName) {
        return Map.of("pan", pan, "expiryMonth", EXPIRY_MONTH, "expiryYear", expiryYear, "cardholderName",
            cardholderName, "cvv", CVV);
    }
}
