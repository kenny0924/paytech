package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static com.paytech.acquiring.test.builder.external.CheckoutContextResponseBuilder.binding;
import static com.paytech.acquiring.test.mocker.external.CheckoutCommercialMocker.body;
import static com.paytech.acquiring.test.mocker.external.CheckoutCommercialMocker.delayed;
import static com.paytech.acquiring.test.mocker.external.CheckoutCommercialMocker.disconnect;
import static com.paytech.acquiring.test.mocker.external.CheckoutCommercialMocker.problem;
import static com.paytech.acquiring.test.mocker.external.CheckoutCommercialMocker.raw;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.api.client.v1.checkout.ClientCheckoutApi;
import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.builder.external.CheckoutContextResponseBuilder;
import com.paytech.acquiring.test.mocker.external.CheckoutCommercialMocker.Reply;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;
import com.paytech.commercial.api.enums.CheckoutErrorCode;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundFlow;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.CommonErrorCode;

/**
 * commercial 真实解析（resolution-mode=REAL）：请求映射与 Header、商业关系快照冻结、错误映射全表、CCT_CONTEXT_CHANGED 一次重试，以及
 * 删除 PLATFORM 来源后的拒绝
 * <li>解析经真实 CheckoutContextApi 客户端、服务发现与负载均衡访问测试 HTTP Mock；传输层不自动重试，次数完全由端口决定。</li>
 * <li>每个失败场景断言不落订单、commercial 调用次数与 Risk 未被调用。</li>
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
class CreateCheckoutCommercialResolutionTest extends AbstractCreateCheckoutApiTest {
    /** 超过测试读超时（500ms）的 commercial 处理耗时。 */
    private static final Duration SLOW = Duration.ofSeconds(1);

    /** 第二层 BeforeEach：本类共用 commercial 默认解析与 Risk 放行；失败场景用顺序响应覆盖。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 请求只带一个 X-Caller-Service，字段取订单冻结值，不传支付方式；商业时点为订单创建时刻（UTC、六位小数）。 */
    @Test
    void test_requestMapping() {
        var response = invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90",
            "2.00", "42.90");

        var calls = preparationMocker.calls();
        assertEquals(1, calls.size(), "commercial 解析调用次数");
        var request = calls.getFirst().request();
        assertAll(() -> assertEquals(List.of("acquiring-service"), calls.getFirst().callerServices()),
            () -> assertEquals(Long.toString(clientId()), request.getClientId()),
            () -> assertEquals(response.orderNo(), request.getOrderNo()),
            () -> assertEquals("US", request.getConsumerCountryCode()), () -> assertEquals("USD", request.getCurrency()),
            () -> assertEquals("en", request.getLocale()), () -> assertNull(request.getPaymentMethodCode()),
            () -> assertEquals("2026-09-08T00:00:00.000000Z", request.getContextAsOfTime()));
        assertEquals(List.of(false), cctTransactions.transactionStates(), "解析在事务外发起");
    }

    /** 成功解析：旧字段由解析结果填充，卖方公开文本原样冻结，8 行快照与三列同事务写入。 */
    @Test
    void test_freezeRelationshipSnapshot() {
        var response = invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90",
            "2.00", "42.90");

        checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, response.orderNo(), NOW, NOW.plusSeconds(1800));
        checkoutChecker.assertEntitySource(clientId(), CLIENT_ORDER_ID, EntitySourceType.PARTNER.getCode(),
            Long.valueOf(CheckoutContextResponseBuilder.LOCAL_PARTNER_ID));
        checkoutChecker.assertFrozenCommercial(clientId(), CLIENT_ORDER_ID, frozenIds());
        checkoutChecker.assertFrozenCommercial(clientId(), CLIENT_ORDER_ID,
            Map.of("public_legal_name", "Test Seller", "public_registration_number", "TEST-REG",
                "public_tax_registration_number", "TAX-001", "public_address_text", "Test address",
                "public_support_email", "support@example.invalid", "public_support_phone", "+971000000"));
        checkoutChecker.assertRelationship(clientId(), CLIENT_ORDER_ID, FundFlow.THROUGH_CUSTODIAN.getCode(), 1,
            CheckoutContextResponseBuilder.SNAPSHOT_MODEL_VERSION, CheckoutContextResponseBuilder.morBindings(clientId()));
        preparationMocker.assertCalls(clientId(), 1);
        riskMocker.assertCalls(1);
    }

    /** 生效版本 0 表示只记录：仍冻结全部绑定行，生效版本按原值记 0。 */
    @Test
    void test_recordOnlySnapshot() {
        preparationMocker.mockResolution(clientId(), "US", "USD",
            CheckoutContextResponseBuilder.template(clientId()).effectiveModelVersion(0).build());

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        checkoutChecker.assertRelationship(clientId(), CLIENT_ORDER_ID, FundFlow.THROUGH_CUSTODIAN.getCode(), 0,
            CheckoutContextResponseBuilder.SNAPSHOT_MODEL_VERSION, CheckoutContextResponseBuilder.morBindings(clientId()));
    }

    /**
     * commercial 未返回快照（CLIENT 来源）：不写绑定行，资金流向与快照版本为空，生效版本 0；责任方在 Client 一侧，tax 请求不带责任方、
     * 带合同业务模式，结果 NOT_MANAGED，订单金额 FINAL。
     */
    @Test
    void test_snapshotAbsent() {
        preparationMocker.mockResolution(clientId(), "US", "USD",
            CheckoutContextResponseBuilder.clientSource(clientId()).build());

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        checkoutChecker.assertEntitySource(clientId(), CLIENT_ORDER_ID, EntitySourceType.CLIENT.getCode(), null);
        checkoutChecker.assertRelationship(clientId(), CLIENT_ORDER_ID, null, 0, null, List.of());
        var taxRequest = taxMocker.requests().getFirst();
        assertNull(taxRequest.getTaxSubjectPartyId());
        assertNull(taxRequest.getSellerPartyId());
        assertEquals(BusinessMode.MOR.getCode(), taxRequest.getBusinessMode());
    }

    /** commercial 业务错误码与依赖故障按映射表转换为 Client API 错误，不重试、不落订单、不调用 Risk。 */
    @ParameterizedTest
    @MethodSource("errorMappings")
    void test_errorMapping(HttpStatus status, CodeEnum commercialCode, int expectedStatus,
        CommerceCheckoutErrorCode expectedCode) {
        preparationMocker.replies(clientId(), problem(status, commercialCode));

        assertRejected(expectedStatus, expectedCode, 1);
    }

    /** 错误体不是 Problem 的 5xx 与断连为依赖不可用，读超时为 504，200 畸形响应为协议错误。 */
    @ParameterizedTest
    @MethodSource("transportFailures")
    void test_transportFailure(Reply reply, int expectedStatus, CommerceCheckoutErrorCode expectedCode) {
        preparationMocker.replies(clientId(), reply);

        assertRejected(expectedStatus, expectedCode, 1);
    }

    /** 服务发现中没有 commercial 实例：503 DEPENDENCY_UNAVAILABLE，不发出请求。 */
    @Test
    void test_noInstance() {
        preparationMocker.removeInstances();

        assertRejected(503, CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, 0);
    }

    /** 复核期间配置变化：同一请求体（同一商业时点）重试一次后成功创建。 */
    @Test
    void test_contextChangedRetriedOnce() {
        preparationMocker.replies(clientId(), problem(HttpStatus.CONFLICT, CheckoutErrorCode.CCT_CONTEXT_CHANGED),
            body(CheckoutContextResponseBuilder.template(clientId()).build()));

        invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");

        var calls = preparationMocker.calls();
        assertEquals(2, calls.size(), "commercial 解析调用次数");
        assertAll(() -> assertEquals(calls.get(0).request().getOrderNo(), calls.get(1).request().getOrderNo()),
            () -> assertEquals(calls.get(0).request().getContextAsOfTime(), calls.get(1).request().getContextAsOfTime()));
        riskMocker.assertCalls(1);
    }

    /** 重试后仍变化：503 DEPENDENCY_UNAVAILABLE，只重试一次。 */
    @Test
    void test_contextChangedTwice() {
        preparationMocker.replies(clientId(), problem(HttpStatus.CONFLICT, CheckoutErrorCode.CCT_CONTEXT_CHANGED),
            problem(HttpStatus.CONFLICT, CheckoutErrorCode.CCT_CONTEXT_CHANGED));

        assertRejected(503, CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, 2);
    }

    /** 经营主体来源 PLATFORM 已删除：作为不合协议的响应拒绝（502）。 */
    @Test
    void test_platformSourceRejected() {
        preparationMocker.replies(clientId(), body(CheckoutContextResponseBuilder.clientSource(clientId())
            .with(response -> response.setSourceType("PLATFORM")).build()));

        assertRejected(502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR, 1);
    }

    /** 快照或 ID 不可冻结：502 UPSTREAM_PROTOCOL_ERROR，不落订单。 */
    @ParameterizedTest
    @MethodSource("invalidResponses")
    void test_invalidResponse(Consumer<ResolveCheckoutContextResponseBody> change) {
        preparationMocker.replies(clientId(), body(CheckoutContextResponseBuilder.template(clientId()).with(change).build()));

        assertRejected(502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR, 1);
    }

    /** 映射表：commercial 状态与错误码 → acquiring 状态与错误码。 */
    static Stream<Arguments> errorMappings() {
        return Stream.of(
            Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT, CheckoutErrorCode.NO_ELIGIBLE_ENTITY, 422,
                CommerceCheckoutErrorCode.CLIENT_CONFIGURATION_NOT_READY),
            Arguments.of(HttpStatus.CONFLICT, CheckoutErrorCode.CCT_CONTEXT_AMBIGUOUS, 422,
                CommerceCheckoutErrorCode.CLIENT_CONFIGURATION_NOT_READY),
            Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT, CheckoutErrorCode.CLIENT_NOT_ELIGIBLE, 422,
                CommerceCheckoutErrorCode.CHECKOUT_NOT_ELIGIBLE),
            Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, CheckoutErrorCode.OWNER_UNAVAILABLE, 503,
                CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE),
            Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR, 503,
                CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE),
            Arguments.of(HttpStatus.NOT_FOUND, CheckoutErrorCode.CLIENT_NOT_FOUND, 502,
                CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR),
            Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT, CheckoutErrorCode.INVALID_CHECKOUT_QUERY, 502,
                CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR),
            Arguments.of(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_REQUEST, 502,
                CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR),
            Arguments.of(HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST, 502,
                CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR));
    }

    /** 传输层故障。 */
    static Stream<Arguments> transportFailures() {
        return Stream.of(
            Arguments.of(raw(HttpStatus.BAD_GATEWAY, "<html>proxy</html>"), 503,
                CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE),
            Arguments.of(disconnect(), 503, CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE),
            Arguments.of(delayed(SLOW, body(CheckoutContextResponseBuilder.template(1L).build())), 504,
                CommerceCheckoutErrorCode.DEPENDENCY_TIMEOUT),
            Arguments.of(raw(HttpStatus.OK, "{broken"), 502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR));
    }

    /** 单项改写合法默认响应，使其不可冻结。 */
    static Stream<Arguments> invalidResponses() {
        Consumer<ResolveCheckoutContextResponseBody> duplicateRole = response -> response.getRelationshipSnapshot()
            .getBindings().add(binding(RelationshipRole.SELLER, RelationshipSubjectType.OPERATING_ENTITY,
                CheckoutContextResponseBuilder.ENTITY_NO, CheckoutContextResponseBuilder.OE_PARTY_ID));
        Consumer<ResolveCheckoutContextResponseBody> missingSeller = response -> response.getRelationshipSnapshot()
            .getBindings().removeIf(row -> RelationshipRole.SELLER.getCode().equals(row.getRole()));
        Consumer<ResolveCheckoutContextResponseBody> otherAcquiringEntity = response -> response
            .getRelationshipSnapshot().getBindings().replaceAll(row -> RelationshipRole.ACQUIRING_OE.getCode()
                .equals(row.getRole()) ? binding(RelationshipRole.ACQUIRING_OE, RelationshipSubjectType.OPERATING_ENTITY,
                    "AE002", CheckoutContextResponseBuilder.OE_PARTY_ID) : row);
        Consumer<ResolveCheckoutContextResponseBody> platformWithSubject = response -> response
            .getRelationshipSnapshot().getBindings().replaceAll(row -> RelationshipRole.PLATFORM.getCode()
                .equals(row.getRole()) ? binding(RelationshipRole.PLATFORM, RelationshipSubjectType.PLATFORM, "1",
                    CheckoutContextResponseBuilder.PLATFORM_SIGNATORY_PARTY_ID) : row);
        return Stream.of(Arguments.of(duplicateRole), Arguments.of(missingSeller), Arguments.of(otherAcquiringEntity),
            Arguments.of(platformWithSubject),
            Arguments.of(change(response -> response.getRelationshipSnapshot().getBindings().getFirst().setRole("OWNER"))),
            Arguments.of(change(response -> response.getRelationshipSnapshot().getBindings().getFirst()
                .setPartyId("party-1"))),
            Arguments.of(change(response -> response.getRelationshipSnapshot().setFundFlow("DIRECT"))),
            Arguments.of(change(response -> response.getRelationshipSnapshot().setEffectiveModelVersion(2))),
            Arguments.of(change(response -> response.getRelationshipSnapshot().setSnapshotModelVersion(0))),
            Arguments.of(change(response -> response.getRelationshipSnapshot().setBindings(new ArrayList<>()))),
            Arguments.of(change(response -> response.getEligibility().setAffiliationId("0"))),
            Arguments.of(change(response -> response.getClientTerms().setVersionId("9223372036854775808"))),
            Arguments.of(change(response -> response.getPartnerTerms().setShareVersionId(null))),
            Arguments.of(change(response -> response.setClassification(null))));
    }

    /** 为参数源保留类型明确的响应改写。 */
    static Consumer<ResolveCheckoutContextResponseBody> change(Consumer<ResolveCheckoutContextResponseBody> change) {
        return change;
    }

    /** 默认响应应冻结的 ID 与 Locale；期望由本类按默认 DTO 独立给出。 */
    static Map<String, Object> frozenIds() {
        return Map.ofEntries(Map.entry("affiliation_id", 101L), Map.entry("entity_scope_id", 102L),
            Map.entry("entity_source_relation_id", 103L), Map.entry("client_pricing_version_id", 104L),
            Map.entry("partner_share_version_id", 401L), Map.entry("platform_charge_version_id", 402L),
            Map.entry("platform_service_party_id", 403L), Map.entry("locale", "en"),
            Map.entry("client_agreement_id", 105L), Map.entry("partner_agreement_id", 404L),
            Map.entry("partner_agreement_operating_entity_id", 405L), Map.entry("seller_disclosure_id", 106L),
            Map.entry("consumer_terms_document_id", 201L), Map.entry("consumer_terms_file_id", 301L),
            Map.entry("refund_policy_file_id", 302L), Map.entry("privacy_notice_file_id", 303L));
    }

    /** 拒绝结果：状态与错误码、不落订单、commercial 调用次数，Risk 未被调用。 */
    private void assertRejected(int status, CommerceCheckoutErrorCode code, int commercialCalls) {
        invoke(template().build()).assertProblem(status, code.getCode());
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        preparationMocker.assertCalls(clientId(), commercialCalls);
        riskMocker.assertCalls(0);
    }
}
