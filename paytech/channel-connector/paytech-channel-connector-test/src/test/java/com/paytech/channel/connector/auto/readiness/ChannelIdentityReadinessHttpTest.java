package com.paytech.channel.connector.auto.readiness;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.channel.connector.api.candidate.request.MidCriteriaBody;
import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.candidate.response.QueryMidCandidatesResponseBody;
import com.paytech.channel.connector.api.enums.ChannelIdentityReadinessReason;
import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.readiness.ChannelIdentityReadinessApi;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessItemBody;
import com.paytech.channel.connector.api.readiness.request.ChannelIdentityReadinessRequestBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResponseBody;
import com.paytech.channel.connector.api.readiness.response.ChannelIdentityReadinessResultBody;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;
import com.paytech.channel.connector.test.builder.MidCapabilityBuilder;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;

/**
 * 渠道身份就绪查询的 HTTP 合同（REQ-02 AC-01—AC-05）：与运行期候选查询同一筛选口径，体系 / 国家任一取值命中即可，并要求收款动作
 * <li>基线：OE 00001 的 ACTIVE 渠道、账号、MID 与 CARD / * / * / AED / 5734 的 ACTION.PAY=true。</li>
 *
 * @see ChannelIdentityReadinessApi#checkReadiness(ChannelIdentityReadinessRequestBody)
 */
class ChannelIdentityReadinessHttpTest extends ChannelConnectorMockTestBase {
    private static final String PATH = "/channel-connector/v1/channel-identities/readiness";

    private static final String ENTITY_NO = "00001";

    private static final String CARD = "CARD";

    private static final String APPLE_PAY = "APPLE_PAY";

    private static final String MCC = "5734";

    private static final String ACTION_PAY = ChannelCapabilityCodes.ACTION_PREFIX + ChannelOperation.PAY.getCode();

    private static final String ACTION_AUTHORIZE = ChannelCapabilityCodes.ACTION_PREFIX + ChannelOperation.AUTHORIZE.getCode();

    private static final String ACTION_AUTHENTICATE =
        ChannelCapabilityCodes.ACTION_PREFIX + ChannelOperation.AUTHENTICATE.getCode();

    @LocalServerPort
    private int port;

    @Autowired
    private ChannelTestMapper mapper;

    private Fixture fixture;

    /** 本类公共：OE 00001 的启用三元组与 CARD/AED/5734 PAY 能力。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.activeFixture();
    }

    /** AC-01 精确币种就绪；响应回显 OE 与入参，原因为空；不读取 Vault。 */
    @Test
    void test_exactCurrencyReady() {
        ChannelIdentityReadinessResponseBody response = query(ENTITY_NO, item(CARD, "AED", MCC));

        ChannelIdentityReadinessResultBody result = response.getItems().getFirst();
        assertAll(() -> assertEquals(ENTITY_NO, response.getEntityNo()), () -> assertEquals(1, response.getItems().size()),
            () -> assertEquals(CARD, result.getPaymentMethodCode()), () -> assertEquals("AED", result.getCurrency()),
            () -> assertEquals(MCC, result.getMccCode()), () -> assertTrue(result.isReady()),
            () -> assertNull(result.getReasonCode()));
        vault.assertNoCalls();
    }

    /**
     * AC-02 币种 * 的能力行使任意币种就绪；只配了具体体系 / 国家的能力行同样计入（运行期传空体系只匹配 *，就绪查询不受此限）。
     */
    @Test
    void test_wildcardCurrencyAndAnySchemeCountry() {
        data.capability(fixture.getMid(), MidCapabilityBuilder.template().scheme("VISA").country("SA").currency("*").mcc("5999"),
            ACTION_AUTHORIZE, true);

        List<ChannelIdentityReadinessResultBody> results = query(ENTITY_NO, item(CARD, "EUR", "5999"),
            item(CARD, "JPY", "5999")).getItems();

        assertAll(() -> assertTrue(results.get(0).isReady()), () -> assertTrue(results.get(1).isReady()));
    }

    /** AC-03 五种缺口各返回对应原因；同组重复返回同一结论（批量去重）。 */
    @Test
    void test_reasonsInOrderAndDuplicates() {
        data.capability(fixture.getMid(), MidCapabilityBuilder.template().currency("SAR"), ACTION_AUTHENTICATE, true);

        List<ChannelIdentityReadinessResultBody> results = query(ENTITY_NO, item(APPLE_PAY, "AED", MCC),
            item(CARD, "AED", "5999"), item(CARD, "USD", MCC), item(CARD, "SAR", MCC), item(CARD, "AED", MCC),
            item(CARD, "AED", MCC)).getItems();
        List<ChannelIdentityReadinessResultBody> missing = query("00002", item(CARD, "AED", MCC)).getItems();

        assertAll(() -> assertEquals(6, results.size()),
            () -> assertReason(results.get(0), ChannelIdentityReadinessReason.PAYMENT_METHOD_NOT_SUPPORTED),
            () -> assertReason(results.get(1), ChannelIdentityReadinessReason.MCC_NOT_COVERED),
            () -> assertReason(results.get(2), ChannelIdentityReadinessReason.CURRENCY_NOT_SUPPORTED),
            () -> assertReason(results.get(3), ChannelIdentityReadinessReason.PAYMENT_ACTION_NOT_SUPPORTED),
            () -> assertTrue(results.get(4).isReady()), () -> assertTrue(results.get(5).isReady()),
            () -> assertReason(missing.getFirst(), ChannelIdentityReadinessReason.MID_NOT_FOUND));
    }

    /** AC-04 MID、渠道账号、渠道非 ACTIVE 时该 MID 不计入（无可用 MID）；能力行 DISABLED 视为没有该支付方式。 */
    @Test
    void test_inactiveConfigurationExcluded() {
        List<ChannelIdentityReadinessReason> reasons = new ArrayList<>();
        mapper.midStatus(ChannelStatus.CLOSED.getCode());
        reasons.add(reason(query(ENTITY_NO, item(CARD, "AED", MCC)).getItems().getFirst()));
        mapper.midStatus(ChannelStatus.ACTIVE.getCode());
        mapper.accountStatus(ChannelStatus.SUSPENDED.getCode());
        reasons.add(reason(query(ENTITY_NO, item(CARD, "AED", MCC)).getItems().getFirst()));
        mapper.accountStatus(ChannelStatus.ACTIVE.getCode());
        mapper.channelStatus(ChannelStatus.SUSPENDED.getCode());
        reasons.add(reason(query(ENTITY_NO, item(CARD, "AED", MCC)).getItems().getFirst()));
        mapper.channelStatus(ChannelStatus.ACTIVE.getCode());
        mapper.disableCapabilities(fixture.getMid().getMidId());
        reasons.add(reason(query(ENTITY_NO, item(CARD, "AED", MCC)).getItems().getFirst()));

        assertEquals(List.of(ChannelIdentityReadinessReason.MID_NOT_FOUND, ChannelIdentityReadinessReason.MID_NOT_FOUND,
            ChannelIdentityReadinessReason.MID_NOT_FOUND, ChannelIdentityReadinessReason.PAYMENT_METHOD_NOT_SUPPORTED), reasons);
    }

    /** AC-04 非 ACTIVE 的 MID 不计入，但同 OE 另一个可用 MID 仍使组合就绪；关闭的 MID 上的能力不产生就绪。 */
    @Test
    void test_otherUsableMidCounts() {
        var closed = data.mid(fixture.getChannel().getChannelId(), fixture.getAccount().getChannelAccountId(), ENTITY_NO,
            ChannelStatus.CLOSED);
        data.capability(closed, MidCapabilityBuilder.template().currency("USD"), ACTION_PAY, true);

        List<ChannelIdentityReadinessResultBody> results = query(ENTITY_NO, item(CARD, "AED", MCC), item(CARD, "USD", MCC))
            .getItems();

        assertAll(() -> assertTrue(results.get(0).isReady()),
            () -> assertReason(results.get(1), ChannelIdentityReadinessReason.CURRENCY_NOT_SUPPORTED));
    }

    /** 能力行明确把收款动作设为 false，或更精确的行（币种 SAR）把通配行的 PAY 覆盖为 false：该组合无收款动作。 */
    @Test
    void test_collectionActionRequired() {
        var second = data.mid(fixture.getChannel().getChannelId(), fixture.getAccount().getChannelAccountId(), ENTITY_NO,
            ChannelStatus.ACTIVE);
        data.capability(second, MidCapabilityBuilder.template().currency("*"), ACTION_PAY, true);
        data.capability(second, MidCapabilityBuilder.template().currency("SAR"), ACTION_PAY, false);
        data.capability(second, MidCapabilityBuilder.template().currency("*").mcc("5999"), ACTION_PAY, false);

        List<ChannelIdentityReadinessResultBody> results = query(ENTITY_NO, item(CARD, "SAR", MCC), item(CARD, "EUR", MCC),
            item(CARD, "EUR", "5999")).getItems();

        assertAll(() -> assertReason(results.get(0), ChannelIdentityReadinessReason.PAYMENT_ACTION_NOT_SUPPORTED),
            () -> assertTrue(results.get(1).isReady()),
            () -> assertReason(results.get(2), ChannelIdentityReadinessReason.PAYMENT_ACTION_NOT_SUPPORTED));
    }

    /**
     * AC-05 与运行期候选查询对照：对每组组合，就绪为 true 当且仅当某个体系 / 国家取值下候选查询返回支持 PAY 或 AUTHORIZE 的候选
     * <li>取值范围覆盖配置中出现的体系 / 国家与一个未配置的值（等价于只匹配 *）。</li>
     */
    @Test
    void test_consistentWithCandidateQuery() {
        var second = data.mid(fixture.getChannel().getChannelId(), fixture.getAccount().getChannelAccountId(), ENTITY_NO,
            ChannelStatus.ACTIVE);
        data.capability(second, MidCapabilityBuilder.template().scheme("VISA").currency("*"), ACTION_AUTHORIZE, true);
        data.capability(second, MidCapabilityBuilder.template().scheme("VISA").country("AE").currency("USD"),
            ACTION_AUTHORIZE, false);
        data.capability(second, MidCapabilityBuilder.template().country("SA").currency("USD").mcc("5999"), ACTION_PAY, true);
        data.capability(second, MidCapabilityBuilder.template().paymentMethod(APPLE_PAY).currency("USD"), ACTION_AUTHENTICATE,
            true);
        List<ChannelIdentityReadinessItemBody> items = new ArrayList<>();
        for (String method : List.of(CARD, APPLE_PAY)) {
            for (String currency : List.of("AED", "USD", "EUR")) {
                for (String mcc : List.of(MCC, "5999")) {
                    items.add(item(method, currency, mcc));
                }
            }
        }

        List<ChannelIdentityReadinessResultBody> results = query(ENTITY_NO, items.toArray(ChannelIdentityReadinessItemBody[]::new))
            .getItems();

        for (ChannelIdentityReadinessResultBody result : results) {
            assertEquals(collectableCandidateExists(result), result.isReady(),
                result.getPaymentMethodCode() + "/" + result.getCurrency() + "/" + result.getMccCode());
        }
        assertTrue(results.stream().anyMatch(ChannelIdentityReadinessResultBody::isReady));
        assertTrue(results.stream().anyMatch(result -> !result.isReady()));
    }

    /** 配置损坏（MID 与账号 OE 不一致、同等精确度冲突）返回 500，不当作不就绪；请求不合法返回 422。 */
    @Test
    void test_brokenConfigurationAndInvalidRequest() {
        data.capability(fixture.getMid(), MidCapabilityBuilder.template().scheme("VISA").currency("*"), ACTION_PAY, true);
        data.capability(fixture.getMid(), MidCapabilityBuilder.template().currency("USD"), ACTION_PAY, false);
        int conflict = status(body(ENTITY_NO, item(CARD, "USD", MCC)));
        mapper.midEntity("00002");
        int broken = status(body(ENTITY_NO, item(CARD, "AED", MCC)));

        var wildcard = body(ENTITY_NO, item(CARD, "*", MCC));
        var empty = body(ENTITY_NO);
        var tooMany = body(ENTITY_NO, IntStream.range(0, 201).mapToObj(index -> item(CARD, "AED", MCC))
            .toArray(ChannelIdentityReadinessItemBody[]::new));
        var badEntity = body("0001", item(CARD, "AED", MCC));
        assertAll(() -> assertEquals(500, conflict), () -> assertEquals(500, broken),
            () -> assertEquals(422, status(wildcard)), () -> assertEquals(422, status(empty)),
            () -> assertEquals(422, status(tooMany)), () -> assertEquals(422, status(badEntity)));
    }

    /** 候选查询在取值范围内存在含收款动作的候选。 */
    boolean collectableCandidateExists(ChannelIdentityReadinessResultBody item) {
        for (String scheme : Arrays.asList("VISA", "MASTERCARD", null)) {
            for (String country : List.of("AE", "SA", "US")) {
                var criteria = new MidCriteriaBody();
                criteria.setPaymentMethodCode(item.getPaymentMethodCode());
                criteria.setSchemeCode(scheme);
                criteria.setCountryCode(country);
                criteria.setCurrency(item.getCurrency());
                criteria.setMccCode(item.getMccCode());
                var request = new QueryMidCandidatesRequestBody();
                request.setEntityNo(ENTITY_NO);
                request.setCriteria(criteria);
                if (candidates(request).getCandidates().stream().anyMatch(candidate -> candidate.getSupportedOperations()
                    .stream().anyMatch(operation -> ChannelOperation.PAY.getCode().equals(operation)
                        || ChannelOperation.AUTHORIZE.getCode().equals(operation)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 断言不就绪及原因。 */
    static void assertReason(ChannelIdentityReadinessResultBody result, ChannelIdentityReadinessReason reason) {
        assertFalse(result.isReady(), result.getPaymentMethodCode() + "/" + result.getCurrency());
        assertEquals(reason.getCode(), result.getReasonCode());
    }

    /** 结论的原因；就绪为 null。 */
    static ChannelIdentityReadinessReason reason(ChannelIdentityReadinessResultBody result) {
        return Arrays.stream(ChannelIdentityReadinessReason.values())
            .filter(reason -> reason.getCode().equals(result.getReasonCode())).findFirst().orElse(null);
    }

    /** 一组组合。 */
    static ChannelIdentityReadinessItemBody item(String paymentMethodCode, String currency, String mccCode) {
        var item = new ChannelIdentityReadinessItemBody();
        item.setPaymentMethodCode(paymentMethodCode);
        item.setCurrency(currency);
        item.setMccCode(mccCode);
        return item;
    }

    /** 请求体。 */
    static ChannelIdentityReadinessRequestBody body(String entityNo, ChannelIdentityReadinessItemBody... items) {
        var body = new ChannelIdentityReadinessRequestBody();
        body.setEntityNo(entityNo);
        body.setItems(List.of(items));
        return body;
    }

    /** 经真实 HTTP 入口查询；4xx/5xx 使测试失败。 */
    ChannelIdentityReadinessResponseBody query(String entityNo, ChannelIdentityReadinessItemBody... items) {
        return client().post().uri(PATH).contentType(MediaType.APPLICATION_JSON).body(body(entityNo, items)).retrieve()
            .body(ChannelIdentityReadinessResponseBody.class);
    }

    /** 经真实 HTTP 入口查询运行期候选。 */
    QueryMidCandidatesResponseBody candidates(QueryMidCandidatesRequestBody body) {
        return client().post().uri("/channel-connector/v1/mid-candidates").contentType(MediaType.APPLICATION_JSON).body(body)
            .retrieve().body(QueryMidCandidatesResponseBody.class);
    }

    /** 查询并返回 HTTP 状态。 */
    int status(ChannelIdentityReadinessRequestBody body) {
        try {
            return client().post().uri(PATH).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity()
                .getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** JDK 客户端：不自动重试 POST。 */
    RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient())).build();
    }
}
