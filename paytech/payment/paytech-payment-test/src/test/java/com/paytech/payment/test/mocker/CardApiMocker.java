package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody;
import com.paytech.card.api.instrument.response.CreateCardInstrumentResponseBody;
import com.paytech.card.api.material.response.RevealChannelCardMaterialResponseBody;
import com.paytech.card.api.ticket.request.IssueCardMaterialTicketRequestBody;
import com.paytech.card.api.ticket.response.IssueCardMaterialTicketResponseBody;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

import lombok.RequiredArgsConstructor;

/**
 * card-service 的 card-api DTO（第 12 步，design/05）：按用例返回卡工具创建结果、一次性读卡票据与凭票据读卡的卡材料，并记录每次请求。
 *
 * <p>
 * 只保留合同可观察的行为：同一 Client 同一来源提交返回同一工具，换卡、换 Customer 或保存意愿返回 409；BIN 按卡号长度取 8 或 6 位，
 * 指纹为卡号的 SHA-256（仅测试合成值）；票据只能使用一次，使用后本工具的 CVV 视为已领取，不能再签票。卡号校验、过期等 card 业务拒绝
 * 由用例以 {@link #rejectInstruments(MockHttpFault)} 显式配置。调用方不符记为意外调用。卡值只在内存中保存到本例结束。
 *
 * <p>
 * 卡属性快照（BIN 库需求 technique 3.4）：每次创建（含重放）按当前生效 BIN 库查询一次，卡号 8、7、6 位前缀依次匹配用例登记的号段；
 * 默认生效版本为 {@link #BIN_LIBRARY_VERSION_NO} 且无号段，即“查询过但未识别”。负例可用 {@link #rawCardAttributes} 原样返回违约快照。
 */
@TestComponent
@RequiredArgsConstructor
public class CardApiMocker implements Clear {
    /** 故障 Problem 与响应头使用的 Request ID。 */
    public static final String ERROR_REQUEST_ID = "card-test-error";

    /** 默认生效 BIN 库版本号。 */
    public static final int BIN_LIBRARY_VERSION_NO = 3;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdGenerator ids;
    private final Map<String, Instrument> bySubmission = new LinkedHashMap<>();
    private final Map<String, Instrument> byRef = new LinkedHashMap<>();
    private final Map<String, StoredCard> storedCards = new LinkedHashMap<>();
    private final Map<String, Ticket> tickets = new LinkedHashMap<>();
    private final List<CreateCardInstrumentRequestBody> creates = new ArrayList<>();
    private final List<IssueCardMaterialTicketRequestBody> ticketRequests = new ArrayList<>();
    private final List<String> reveals = new ArrayList<>();
    private final Map<String, CreateCardInstrumentResponseBody.CardAttributes> binRanges = new LinkedHashMap<>();
    private Integer binLibraryVersionNo = BIN_LIBRARY_VERSION_NO;
    private CreateCardInstrumentResponseBody.CardAttributes rawCardAttributes;
    private boolean rawCardAttributesSet;
    private MockHttpFault instrumentFault;
    private MockHttpFault ticketFault;
    private int unexpected;

    /** 登记一张已存卡：只有登记的 Client 与 Customer 可以用它创建工具。 */
    public synchronized String storedCard(String clientId, String customerId, String pan, String expiryMonth,
        String expiryYear, String cardholderName) {
        byte[] bytes = new byte[21];
        RANDOM.nextBytes(bytes);
        String token = "sc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        storedCards.put(token, new StoredCard(clientId, customerId, pan, expiryMonth, expiryYear, cardholderName));
        return token;
    }

    /**
     * 在生效 BIN 库登记一个号段的已识别卡属性
     *
     * @param prefix 号段前缀，6 至 8 位
     * @param attributes 卡属性，见 {@link #attributes}；命中位数与版本号在响应时填写
     */
    public synchronized void binRange(String prefix, CreateCardInstrumentResponseBody.CardAttributes attributes) {
        binRanges.put(prefix, attributes);
    }

    /** 清空已登记号段：之后的查询为“有生效版本但未识别”。 */
    public synchronized void clearBinRanges() {
        binRanges.clear();
    }

    /** 切换生效 BIN 库版本；null 表示没有生效版本。已登记号段保留，供“新版本”命中。 */
    public synchronized void activeBinLibrary(Integer versionNo) {
        binLibraryVersionNo = versionNo;
    }

    /** 之后的工具创建原样返回该卡属性快照（可为 null），用于违反 Card 合同的负例。 */
    public synchronized void rawCardAttributes(CreateCardInstrumentResponseBody.CardAttributes attributes) {
        rawCardAttributes = attributes;
        rawCardAttributesSet = true;
    }

    /** 已识别号段的卡属性（各项可为 null 以表达号段未提供）。 */
    public static CreateCardInstrumentResponseBody.CardAttributes attributes(String cardBrand, String fundingType,
        String cardLevel, String cardProduct, String issuerCountry, String issuerRegion) {
        var attributes = new CreateCardInstrumentResponseBody.CardAttributes();
        attributes.setRecognized(true);
        attributes.setCardBrand(cardBrand);
        attributes.setFundingType(fundingType);
        attributes.setCardLevel(cardLevel);
        attributes.setCardProduct(cardProduct);
        attributes.setIssuerCountry(issuerCountry);
        attributes.setIssuerRegion(issuerRegion);
        return attributes;
    }

    /** 之后的工具创建一律返回指定故障（card 业务拒绝或不可用）。 */
    public synchronized void rejectInstruments(MockHttpFault fault) {
        instrumentFault = fault;
    }

    /** 之后的票据签发一律返回指定故障（如 CVV 已领取的 400 或 card 不可用）。 */
    public synchronized void rejectTickets(MockHttpFault fault) {
        ticketFault = fault;
    }

    /** HTTP Mock 实际调用此处：创建或重放卡交易工具。 */
    public synchronized CreateCardInstrumentResponseBody createInstrument(String caller,
        CreateCardInstrumentRequestBody body) {
        if (!CardApiConstants.PAYMENT_SERVICE.equals(caller)) {
            unexpected++;
            throw new AssertionError("工具创建调用方不符: " + caller);
        }
        creates.add(body);
        if (instrumentFault != null) {
            throw instrumentFault;
        }
        Material material = material(body);
        String key = body.getClientId() + ":" + body.getSourceSubmissionId();
        Instrument original = bySubmission.get(key);
        if (original != null) {
            if (!original.sameAs(material, body)) {
                throw fault(409, CommonErrorCode.CONCURRENT_REQUEST);
            }
            return response(original);
        }
        var instrument = new Instrument(Long.toString(ids.nextId()), material, body.getCustomerId(),
            Boolean.TRUE.equals(body.getConsentToSave()), body.getStoredCard() == null ? null
                : body.getStoredCard().getStoredCardToken(), Instant.parse(body.getExpiresAt()));
        bySubmission.put(key, instrument);
        byRef.put(instrument.ref, instrument);
        return response(instrument);
    }

    /** HTTP Mock 实际调用此处：CVV 未领取时签发一次性票据。 */
    public synchronized IssueCardMaterialTicketResponseBody issueTicket(String caller,
        IssueCardMaterialTicketRequestBody body) {
        if (!CardApiConstants.PAYMENT_SERVICE.equals(caller)) {
            unexpected++;
            throw new AssertionError("票据签发调用方不符: " + caller);
        }
        ticketRequests.add(body);
        if (ticketFault != null) {
            throw ticketFault;
        }
        Instrument instrument = byRef.get(body.getInstrumentRef());
        if (instrument == null) {
            throw fault(404, CommonErrorCode.NOT_FOUND);
        }
        if (instrument.securityCodeClaimed) {
            throw fault(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(instrument));
        var response = new IssueCardMaterialTicketResponseBody();
        response.setTicket(ticket);
        response.setExpiresAt(Instant.now().plusSeconds(body.getTtlSeconds()).toString());
        return response;
    }

    /** HTTP Mock 实际调用此处（Platform 读卡）：票据只能使用一次，使用后该工具的 CVV 已领取。 */
    public synchronized RevealChannelCardMaterialResponseBody reveal(String caller, String ticket) {
        if (!CardApiConstants.PLATFORM_SUPPORT_SERVICE.equals(caller)) {
            unexpected++;
            throw new AssertionError("读卡调用方不符: " + caller);
        }
        reveals.add(ticket);
        Ticket issued = tickets.get(ticket);
        if (issued == null) {
            throw fault(404, CommonErrorCode.NOT_FOUND);
        }
        if (issued.used) {
            throw fault(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        }
        issued.used = true;
        issued.instrument.securityCodeClaimed = true;
        Material material = issued.instrument.material;
        var response = new RevealChannelCardMaterialResponseBody();
        response.setPan(material.pan());
        response.setExpiryMonth(material.expiryMonth());
        response.setExpiryYear(material.expiryYear());
        response.setCardholderName(material.cardholderName());
        response.setCvv(material.cvv());
        return response;
    }

    /** 本例工具创建请求（含卡原值，只在内存中）。 */
    public synchronized List<CreateCardInstrumentRequestBody> creates() {
        return List.copyOf(creates);
    }

    /** 本例票据签发请求。 */
    public synchronized List<IssueCardMaterialTicketRequestBody> ticketRequests() {
        return List.copyOf(ticketRequests);
    }

    /** 本例签发的票据，按签发顺序。 */
    public synchronized List<String> tickets() {
        return List.copyOf(tickets.keySet());
    }

    /** 本例读卡请求使用的票据，按调用顺序。 */
    public synchronized List<String> reveals() {
        return List.copyOf(reveals);
    }

    /** 合成卡号的期望 BIN：不少于 16 位取 8 位，否则 6 位。 */
    public static String bin(String pan) {
        return pan.substring(0, pan.length() >= 16 ? 8 : 6);
    }

    /** 合成卡号的期望指纹（测试替身规则，card 真实规则为 HMAC-SHA256）。 */
    public static String fingerprint(String pan) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("card-mock:" + pan).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(bySubmission.isEmpty() && storedCards.isEmpty() && tickets.isEmpty() && creates.isEmpty()
            && ticketRequests.isEmpty() && reveals.isEmpty() && instrumentFault == null && ticketFault == null
            && unexpected == 0 && binRanges.isEmpty() && !rawCardAttributesSet
            && Objects.equals(BIN_LIBRARY_VERSION_NO, binLibraryVersionNo));
    }

    /** 每例结束检查没有调用方不符的请求。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在调用方不符的 card 请求");
    }

    @Override
    public synchronized void clear() {
        bySubmission.clear();
        byRef.clear();
        storedCards.clear();
        tickets.clear();
        creates.clear();
        ticketRequests.clear();
        reveals.clear();
        instrumentFault = null;
        ticketFault = null;
        unexpected = 0;
        binRanges.clear();
        binLibraryVersionNo = BIN_LIBRARY_VERSION_NO;
        rawCardAttributes = null;
        rawCardAttributesSet = false;
    }

    /** 手输卡取请求原值；已存卡取登记的卡与本次 CVV，Client 或 Customer 不符为 400。 */
    Material material(CreateCardInstrumentRequestBody body) {
        if (body.getCard() != null) {
            var card = body.getCard();
            return new Material(card.getPan(), card.getExpiryMonth(), card.getExpiryYear(), card.getCardholderName(),
                card.getCvv());
        }
        StoredCard stored = storedCards.get(body.getStoredCard().getStoredCardToken());
        if (stored == null || !stored.clientId().equals(body.getClientId())
            || !stored.customerId().equals(body.getCustomerId())) {
            throw fault(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        }
        return new Material(stored.pan(), stored.expiryMonth(), stored.expiryYear(), stored.cardholderName(),
            body.getStoredCard().getCvv());
    }

    /** 合同响应：卡属性快照按本次查询时的生效 BIN 库计算。 */
    CreateCardInstrumentResponseBody response(Instrument instrument) {
        var risk = new CreateCardInstrumentResponseBody.RiskAttributes();
        risk.setBin(bin(instrument.material.pan()));
        risk.setFingerprint(fingerprint(instrument.material.pan()));
        var body = new CreateCardInstrumentResponseBody();
        body.setInstrumentRef(instrument.ref);
        body.setMaskedPan(instrument.material.pan().substring(0, 6) + "******"
            + instrument.material.pan().substring(instrument.material.pan().length() - 4));
        body.setExpiresAt(instrument.expiresAt.toString());
        body.setRiskAttributes(risk);
        body.setCardAttributes(rawCardAttributesSet ? rawCardAttributes : cardAttributes(instrument.material.pan()));
        return body;
    }

    /** 当前生效 BIN 库按 8、7、6 位前缀匹配；没有生效版本或未命中为未识别。 */
    CreateCardInstrumentResponseBody.CardAttributes cardAttributes(String pan) {
        var snapshot = new CreateCardInstrumentResponseBody.CardAttributes();
        snapshot.setBinLibraryVersionNo(binLibraryVersionNo);
        if (binLibraryVersionNo == null) {
            return snapshot;
        }
        for (int length = 8; length >= 6; length--) {
            var range = binRanges.get(pan.substring(0, length));
            if (range != null) {
                var matched = attributes(range.getCardBrand(), range.getFundingType(), range.getCardLevel(),
                    range.getCardProduct(), range.getIssuerCountry(), range.getIssuerRegion());
                matched.setBinLibraryVersionNo(binLibraryVersionNo);
                matched.setMatchedPrefixLength(length);
                return matched;
            }
        }
        return snapshot;
    }

    /**
     * card 合同的 RFC 9457 Problem（Code-MoR Profile），requestId 与 {@link com.paytech.payment.test.mock.MockCardController} 写出的
     * X-Request-Id 一致，供 Foundation 客户端严格解码。
     *
     * @param status HTTP 状态
     * @param code 公共错误码
     * @return 故障
     */
    public static MockHttpFault fault(int status, CommonErrorCode code) {
        var problem = new ApiProblemResponse("about:blank", HttpStatus.valueOf(status).getReasonPhrase(), "Card Mock 错误",
            code.getCode(), ERROR_REQUEST_ID, "2026-10-18T00:00:00.000000Z", null);
        return MockHttpFault.json(status, problem);
    }

    /** 卡原值（本次 CVV 不参与身份比较）。 */
    record Material(String pan, String expiryMonth, String expiryYear, String cardholderName, String cvv) {
        boolean sameCard(Material other) {
            return pan.equals(other.pan) && expiryMonth.equals(other.expiryMonth) && expiryYear.equals(other.expiryYear)
                && cardholderName.equals(other.cardholderName);
        }

        @Override
        public String toString() {
            return "Material[REDACTED]";
        }
    }

    /** 登记的已存卡。 */
    record StoredCard(String clientId, String customerId, String pan, String expiryMonth, String expiryYear,
        String cardholderName) {
        @Override
        public String toString() {
            return "StoredCard[REDACTED]";
        }
    }

    /** 一个卡交易工具。 */
    static final class Instrument {
        final String ref;
        final Material material;
        final String customerId;
        final boolean consentToSave;
        final String storedCardToken;
        final Instant expiresAt;
        boolean securityCodeClaimed;

        Instrument(String ref, Material material, String customerId, boolean consentToSave, String storedCardToken,
            Instant expiresAt) {
            this.ref = ref;
            this.material = material;
            this.customerId = customerId;
            this.consentToSave = consentToSave;
            this.storedCardToken = storedCardToken;
            this.expiresAt = expiresAt;
        }

        /** 原提交重放：同卡（或同一已存卡）、同 Customer、同保存意愿。 */
        boolean sameAs(Material other, CreateCardInstrumentRequestBody body) {
            String token = body.getStoredCard() == null ? null : body.getStoredCard().getStoredCardToken();
            return material.sameCard(other) && Objects.equals(storedCardToken, token)
                && Objects.equals(customerId, body.getCustomerId())
                && consentToSave == Boolean.TRUE.equals(body.getConsentToSave());
        }
    }

    /** 一张票据。 */
    static final class Ticket {
        final Instrument instrument;
        boolean used;

        Ticket(Instrument instrument) {
            this.instrument = instrument;
        }
    }
}
