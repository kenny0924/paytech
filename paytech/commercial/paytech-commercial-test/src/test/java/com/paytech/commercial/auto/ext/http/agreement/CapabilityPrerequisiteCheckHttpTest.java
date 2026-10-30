package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.api.enums.ChannelIdentityReadinessReason;
import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.CapabilityPrerequisiteCode;
import com.paytech.commercial.api.enums.CapabilityPrerequisiteResult;
import com.paytech.commercial.api.event.AgreementCapabilityStatusChangedPayload;
import com.paytech.commercial.core.constant.CapabilityPrerequisiteReasons;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.checker.write.CommercialWriteResponseChecker;
import com.paytech.commercial.test.mocker.channelconnector.ChannelIdentityReadinessMocker;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.commercial.test.mocker.local.agreement.CapabilityGateMocker;
import com.paytech.commercial.test.mocker.local.term.TermPricingMocker;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;

import tools.jackson.databind.JsonNode;

/**
 * 开通项前置门禁核对接口（开通项前置门禁 technique.md §9、§10.2、§15.1；REQ-02 technique.md §10.2）：①资格、②合同与价格、④渠道身份的逐项结论与状态迁移
 * <li>全部满足 → ENABLED，Root 推进、系统身份快照与 AgreementCapabilityStatusChanged；任一明确不满足时等前置保持、已开通暂停；恢复满足后
 * 自动恢复；资格服务不可用 → UNKNOWN 不迁移；手动暂停不被触碰；PARTNER 合同 400。</li>
 * <li>④：价格已覆盖的币种逐一经 channel-connector 就绪查询（Client 主 MCC 取自 client-master）；任一币种就绪即满足（部分不就绪带
 * CHANNEL_IDENTITY_PARTIAL），全部不就绪不满足、查询失败 UNKNOWN、MCC 未确认 CLIENT_MCC_MISSING、价格未覆盖任何币种 AWAITING_PRICE。</li>
 *
 * @see AgreementApi#checkPrerequisites(String)
 */
class CapabilityPrerequisiteCheckHttpTest extends AbstractCommercialWriteApiTest {
    private static final String PATH = "/agreements/v1/" + AgreementBuilder.AGREEMENT_ID + "/prerequisite-checks";

    /** CARD 开通项 ID。 */
    private static final long CARD_ID = 930000000000501L;

    /** TOKEN_STORAGE（功能）开通项 ID。 */
    private static final long TOKEN_ID = 930000000000502L;

    @Autowired
    private CapabilityGateMocker gate;

    @Autowired
    private TermPricingMocker pricing;

    /** 每例：目录与 ACTIVE 通用卡画像。 */
    @BeforeEach
    void setUpClassMocks() {
        gate.mockCatalogAndProfile();
    }

    /** 全部满足：CARD 与功能项从等前置变为 ENABLED，版本 6，系统身份快照，提交后发布一条事件列出两项变化；资格只查一次。 */
    @Test
    void test_allSatisfied_enabled() {
        AgreementWriteMocker.Mocker agreement = coveredAgreement();
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        gate.mockCapability(agreement, TOKEN_ID, CommercialCodes.Capability.TOKEN_STORAGE,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        mockAgreementQualified();
        mockChannelIdentityReady("USD");

        CommercialWriteResponseChecker response = check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION + 1);

        JsonNode card = capability(response, CARD_ID);
        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), card.path("executionStatus").asString()),
            () -> assertEquals(AgreementCapabilityStatus.ACTIVE.getCode(), card.path("status").asString()),
            () -> assertEquals("PAYMENT_METHOD", card.path("capabilityKind").asString()),
            () -> assertCheck(card, CapabilityPrerequisiteCode.QUALIFICATION, CapabilityPrerequisiteResult.SATISFIED, null),
            () -> assertCheck(card, CapabilityPrerequisiteCode.AGREEMENT_TERM, CapabilityPrerequisiteResult.SATISFIED, null),
            () -> assertCheck(card, CapabilityPrerequisiteCode.CHANNEL_IDENTITY, CapabilityPrerequisiteResult.SATISFIED, null),
            () -> assertCheck(capability(response, TOKEN_ID), CapabilityPrerequisiteCode.CHANNEL_IDENTITY,
                CapabilityPrerequisiteResult.SKIPPED, CapabilityPrerequisiteReasons.NOT_APPLICABLE),
            () -> assertEquals(executionStatus(CARD_ID), AgreementCapabilityExecutionStatus.ENABLED.getCode()),
            () -> assertEquals(executionStatus(TOKEN_ID), AgreementCapabilityExecutionStatus.ENABLED.getCode()),
            () -> assertEquals(List.of(QualificationRoleType.CLIENT.getCode() + ":" + AgreementBuilder.CLIENT_ID,
                QualificationRoleType.OPERATING_ENTITY.getCode() + ":" + AgreementBuilder.ENTITY_NO),
                compliance.onlyRequestSubjects()),
            () -> assertEquals(1, classification.count()),
            () -> assertEquals(List.of(ChannelIdentityReadinessMocker.key(AgreementBuilder.ENTITY_NO,
                CommercialCodes.PaymentMethod.CARD, "USD", CLIENT_MCC)), channelIdentity.onlyRequestItems()));
        history.record(BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY.getCode(), CARD_ID).assertVersions("5:CREATE", "6:UPDATE")
            .assertLastReason(BusinessChangeReason.AGREEMENT_CAPABILITY_EXECUTION_CHANGED.getCode()).assertLastActor(0)
            .assertLastData("executionStatus", AgreementCapabilityExecutionStatus.ENABLED.getCode()).assertIntegrity();
        assertEquals(BusinessChangeSnapshot.TRIGGER_SYSTEM, history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(),
            AgreementBuilder.AGREEMENT_ID).last().path("_meta").path("triggerType").asString());

        JsonNode payload = statusChangedPayload(CapabilityGateMocker.ROOT_VERSION + 1);
        assertAll(() -> assertEquals(2, payload.path("changes").size()),
            () -> assertEquals(Long.toString(AgreementBuilder.CLIENT_ID), payload.path("clientId").asString()),
            () -> assertChange(payload.path("changes").get(0), CARD_ID, AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES,
                AgreementCapabilityExecutionStatus.ENABLED, "PREREQUISITE"),
            () -> assertEquals("PAYMENT_METHOD", payload.path("changes").get(0).path("capabilityKind").asString()),
            () -> assertChange(payload.path("changes").get(1), TOKEN_ID,
                AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES, AgreementCapabilityExecutionStatus.ENABLED,
                "PREREQUISITE"));
    }

    /** Client 或 OE 资格不合格：等前置保持，原因与参数正确，不写库、不发布。 */
    @Test
    void test_qualificationUnsatisfied_staysPending() {
        gate.mockCapability(coveredAgreement(), CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.REVOKED);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, AgreementBuilder.ENTITY_NO,
            QualificationEvaluationStatus.ELIGIBLE);
        mockChannelIdentityReady("USD");

        JsonNode clientRejected = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION), CARD_ID);

        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, AgreementBuilder.ENTITY_NO,
            QualificationEvaluationStatus.SETTLEMENT_BLOCKED);
        JsonNode entityRejected = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION), CARD_ID);

        JsonNode clientCheck = check(clientRejected, CapabilityPrerequisiteCode.QUALIFICATION);
        JsonNode entityCheck = check(entityRejected, CapabilityPrerequisiteCode.QUALIFICATION);
        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(),
                clientRejected.path("executionStatus").asString()),
            () -> assertEquals(CapabilityPrerequisiteResult.UNSATISFIED.getCode(), clientCheck.path("result").asString()),
            () -> assertEquals(CapabilityPrerequisiteReasons.CLIENT_NOT_QUALIFIED, clientCheck.path("reasonCode").asString()),
            () -> assertEquals("CLIENT", clientCheck.path("params").path("subjectType").asString()),
            () -> assertEquals(Long.toString(AgreementBuilder.CLIENT_ID), clientCheck.path("params").path("subjectId").asString()),
            () -> assertEquals(CapabilityPrerequisiteReasons.OPERATING_ENTITY_NOT_QUALIFIED,
                entityCheck.path("reasonCode").asString()),
            () -> assertEquals(AgreementBuilder.ENTITY_NO, entityCheck.path("params").path("entityNo").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(), executionStatus(CARD_ID)));
        events.assertNoEnvelope(statusChangedKey(CapabilityGateMocker.ROOT_VERSION + 1));
    }

    /** 合同不是 ACTIVE（SIGNED）：② AGREEMENT_NOT_ACTIVE，带合同状态参数。 */
    @Test
    void test_agreementNotActive() {
        AgreementWriteMocker.Mocker agreement = gate.mockAgreement(AgreementBuilder.template().status(AgreementStatus.SIGNED));
        gate.mockCardPrice(gate.mockClientPricingVersion(CommercialTermVersionStatus.ACTIVE), 0);
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        mockAgreementQualified();
        mockChannelIdentityReady("USD");

        JsonNode term = check(capability(check().assertOk(), CARD_ID), CapabilityPrerequisiteCode.AGREEMENT_TERM);

        assertAll(() -> assertEquals(CapabilityPrerequisiteReasons.AGREEMENT_NOT_ACTIVE, term.path("reasonCode").asString()),
            () -> assertEquals(AgreementStatus.SIGNED.getCode(), term.path("params").path("agreementStatus").asString()));
    }

    /** 方案只有已批准、未生效的版本：② TERM_VERSION_NOT_ACTIVE，带条款类型参数。 */
    @Test
    void test_termVersionNotActive() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        gate.mockClientPricingVersion(CommercialTermVersionStatus.APPROVED);
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        mockAgreementQualified();

        JsonNode card = capability(check().assertOk(), CARD_ID);
        JsonNode term = check(card, CapabilityPrerequisiteCode.AGREEMENT_TERM);

        assertAll(() -> assertEquals(CapabilityPrerequisiteReasons.TERM_VERSION_NOT_ACTIVE, term.path("reasonCode").asString()),
            () -> assertCheck(card, CapabilityPrerequisiteCode.CHANNEL_IDENTITY, CapabilityPrerequisiteResult.SKIPPED,
                CapabilityPrerequisiteReasons.AWAITING_PRICE),
            () -> assertEquals(0, classification.count() + channelIdentity.count()),
            () -> assertEquals(CommercialCodes.TermType.CLIENT_PRICING, term.path("params").path("termType").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(), executionStatus(CARD_ID)));
    }

    /** ACTIVE 版本只有 AED 价格，CURRENCY=USD 的 CARD 没有命中：② PRICE_NOT_COVERED，参数带出未覆盖币种 USD。 */
    @Test
    void test_priceNotCovered() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        CommercialTermVersion version = gate.mockClientPricingVersion(CommercialTermVersionStatus.ACTIVE);
        pricing.mockPrice(CommercialTermPriceBuilder.template().termVersionId(version.getTermVersionId()).currency("AED"),
            CommercialTermComponentBuilder.template().rate(new BigDecimal("0.03")));
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        mockAgreementQualified();

        JsonNode card = capability(check().assertOk(), CARD_ID);
        JsonNode term = check(card, CapabilityPrerequisiteCode.AGREEMENT_TERM);
        JsonNode channel = check(card, CapabilityPrerequisiteCode.CHANNEL_IDENTITY);

        assertAll(() -> assertEquals(CapabilityPrerequisiteReasons.PRICE_NOT_COVERED, term.path("reasonCode").asString()),
            () -> assertEquals(AgreementBuilder.ENTITY_NO, term.path("params").path("entityNo").asString()),
            () -> assertEquals("USD", term.path("params").path("currencies").asString()),
            () -> assertEquals(CapabilityPrerequisiteResult.SKIPPED.getCode(), channel.path("result").asString()),
            () -> assertEquals(CapabilityPrerequisiteReasons.AWAITING_PRICE, channel.path("reasonCode").asString()),
            () -> assertEquals(AgreementBuilder.ENTITY_NO, channel.path("params").path("entityNo").asString()),
            () -> assertEquals(0, classification.count() + channelIdentity.count()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(), executionStatus(CARD_ID)));
    }

    /** 部分币种未覆盖：只有 USD 价格，CARD 接受 USD / SAR / AED：PRICE_NOT_COVERED，currencies 只列未覆盖的 AED,SAR（字典序）。 */
    @Test
    void test_priceNotCovered_partialCurrencies() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        CommercialTermVersion version = gate.mockClientPricingVersion(CommercialTermVersionStatus.ACTIVE);
        pricing.mockPrice(CommercialTermPriceBuilder.template().termVersionId(version.getTermVersionId()).currency("USD"),
            CommercialTermComponentBuilder.template().rate(new BigDecimal("0.03")));
        gate.mockCapability(agreement, CARD_ID, AgreementBuilder.ENTITY_NO, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES, "USD", "SAR", "AED");
        mockAgreementQualified();
        mockChannelIdentityReady("USD");

        JsonNode card = capability(check().assertOk(), CARD_ID);
        JsonNode term = check(card, CapabilityPrerequisiteCode.AGREEMENT_TERM);

        assertAll(() -> assertEquals(CapabilityPrerequisiteReasons.PRICE_NOT_COVERED, term.path("reasonCode").asString()),
            () -> assertEquals("AED,SAR", term.path("params").path("currencies").asString()),
            () -> assertCheck(card, CapabilityPrerequisiteCode.CHANNEL_IDENTITY, CapabilityPrerequisiteResult.SATISFIED, null),
            () -> assertEquals(List.of(ChannelIdentityReadinessMocker.key(AgreementBuilder.ENTITY_NO,
                CommercialCodes.PaymentMethod.CARD, "USD", CLIENT_MCC)), channelIdentity.onlyRequestItems()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(), executionStatus(CARD_ID)));
    }

    /** 已开通后 Client 资格撤销 → SUSPENDED_BY_PREREQUISITE（版本 6）；资格恢复 → ENABLED（版本 7）；两次各发布一条事件。 */
    @Test
    void test_enabledRevokedThenRestored() {
        gate.mockCapability(coveredAgreement(), CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.ENABLED);
        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.REVOKED);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, AgreementBuilder.ENTITY_NO,
            QualificationEvaluationStatus.ELIGIBLE);
        mockChannelIdentityReady("USD");

        check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION + 1);
        String suspended = executionStatus(CARD_ID);
        JsonNode suspension = statusChangedPayload(CapabilityGateMocker.ROOT_VERSION + 1).path("changes").get(0);
        mockAgreementQualified();
        check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION + 2);

        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE.getCode(), suspended),
            () -> assertChange(suspension, CARD_ID, AgreementCapabilityExecutionStatus.ENABLED,
                AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE, "PREREQUISITE"),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(CARD_ID)),
            () -> assertChange(statusChangedPayload(CapabilityGateMocker.ROOT_VERSION + 2).path("changes").get(0), CARD_ID,
                AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE, AgreementCapabilityExecutionStatus.ENABLED,
                "PREREQUISITE"));
    }

    /** 资格服务 503：仍返回 200，①为 UNKNOWN（CHECK_UNAVAILABLE），已开通项不被暂停、不写库。 */
    @Test
    void test_qualificationUnavailable_unknown() {
        gate.mockCapability(coveredAgreement(), CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.ENABLED);
        compliance.mockProblem(503, "SERVICE_UNAVAILABLE");
        mockChannelIdentityReady("USD");

        JsonNode card = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION), CARD_ID);

        JsonNode qualification = check(card, CapabilityPrerequisiteCode.QUALIFICATION);
        assertAll(() -> assertEquals(CapabilityPrerequisiteResult.UNKNOWN.getCode(), qualification.path("result").asString()),
            () -> assertEquals(CapabilityPrerequisiteReasons.CHECK_UNAVAILABLE, qualification.path("reasonCode").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), card.path("executionStatus").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(CARD_ID)));
    }

    /** 手动暂停不被门禁触碰；关闭的开通项没有检查项、执行状态为空。 */
    @Test
    void test_manualSuspensionAndDisabledUntouched() {
        AgreementWriteMocker.Mocker agreement = coveredAgreement();
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY);
        gate.mockCapability(agreement, TOKEN_ID, CommercialCodes.Capability.TOKEN_STORAGE, null);
        testMapper.overwriteCapabilityStatus(TOKEN_ID, AgreementCapabilityStatus.DISABLED);
        mockAgreementQualified();
        mockChannelIdentityReady("USD");

        CommercialWriteResponseChecker response = check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION);

        JsonNode disabled = capability(response, TOKEN_ID);
        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY.getCode(),
                capability(response, CARD_ID).path("executionStatus").asString()),
            () -> assertEquals(3, capability(response, CARD_ID).path("checks").size()),
            () -> assertEquals(AgreementCapabilityStatus.DISABLED.getCode(), disabled.path("status").asString()),
            () -> assertTrue(disabled.path("executionStatus").isNull()),
            () -> assertEquals(0, disabled.path("checks").size()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY.getCode(), executionStatus(CARD_ID)));
    }

    /** PARTNER 合同与不存在的合同：400，不查询资格。 */
    @Test
    void test_partnerAgreementRejected() {
        long partnerId = AgreementBuilder.AGREEMENT_ID + 500;
        gate.mockAgreement(AgreementBuilder.template().agreementId(partnerId).agreementNo("GATE-PARTNER-1")
            .agreementType(AgreementAgreementType.PARTNER).businessMode(null).fundMode(null).pricingAuthority(null)
            .clientId(null).entityNo(null).localPartnerId(930000000000985L).status(AgreementStatus.ACTIVE));

        post("/agreements/v1/" + partnerId + "/prerequisite-checks", null)
            .assertBusinessCheckFailed("PARTNER 合同的开通项不进入前置门禁");
        post("/agreements/v1/" + (partnerId + 1) + "/prerequisite-checks", null).assertBusinessCheckFailed("合同不存在");

        assertEquals(0, compliance.count() + classification.count() + channelIdentity.count());
    }

    /**
     * AC-06 ①② 满足、④ 不就绪（OE 下无可用 MID）：等前置保持，"还差什么"给出 ④ 原因与参数（OE、支付方式、MCC、币种），不写库、不发布
     */
    @Test
    void test_channelIdentityNotReady_staysPending() {
        gate.mockCapability(coveredAgreement(), CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        mockAgreementQualified();
        classification.mockConfirmed(AgreementBuilder.CLIENT_ID, CLIENT_MCC);
        channelIdentity.mockNotReady(AgreementBuilder.ENTITY_NO, CommercialCodes.PaymentMethod.CARD, CLIENT_MCC,
            ChannelIdentityReadinessReason.MID_NOT_FOUND, "USD");

        JsonNode card = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION), CARD_ID);

        JsonNode channel = check(card, CapabilityPrerequisiteCode.CHANNEL_IDENTITY);
        assertAll(() -> assertEquals(CapabilityPrerequisiteResult.UNSATISFIED.getCode(), channel.path("result").asString()),
            () -> assertEquals(ChannelIdentityReadinessReason.MID_NOT_FOUND.getCode(), channel.path("reasonCode").asString()),
            () -> assertEquals(Map.of("entityNo", AgreementBuilder.ENTITY_NO, "paymentMethodCode",
                CommercialCodes.PaymentMethod.CARD, "mccCode", CLIENT_MCC, "currencies", "USD"), params(channel)),
            () -> assertCheck(card, CapabilityPrerequisiteCode.QUALIFICATION, CapabilityPrerequisiteResult.SATISFIED, null),
            () -> assertCheck(card, CapabilityPrerequisiteCode.AGREEMENT_TERM, CapabilityPrerequisiteResult.SATISFIED, null),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(), executionStatus(CARD_ID)));
        events.assertNoEnvelope(statusChangedKey(CapabilityGateMocker.ROOT_VERSION + 1));
    }

    /**
     * AC-08（Q-4 修订：任一币种就绪即满足）开通项被 SAR / USD 两个币种的价格覆盖，只有 SAR 就绪：④ 满足并带
     * CHANNEL_IDENTITY_PARTIAL，参数列出不就绪币种 USD；等前置变为 ENABLED 并发布事件；两个币种在一次就绪查询中提交
     */
    @Test
    void test_channelIdentityPartialCurrencies_enabled() {
        mockTwoCurrencyCapability();
        channelIdentity.mockReady(AgreementBuilder.ENTITY_NO, CommercialCodes.PaymentMethod.CARD, CLIENT_MCC, "SAR");
        channelIdentity.mockNotReady(AgreementBuilder.ENTITY_NO, CommercialCodes.PaymentMethod.CARD, CLIENT_MCC,
            ChannelIdentityReadinessReason.MID_NOT_FOUND, "USD");

        JsonNode card = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION + 1), CARD_ID);

        JsonNode channel = check(card, CapabilityPrerequisiteCode.CHANNEL_IDENTITY);
        assertAll(() -> assertEquals(CapabilityPrerequisiteResult.SATISFIED.getCode(), channel.path("result").asString()),
            () -> assertEquals(CapabilityPrerequisiteReasons.CHANNEL_IDENTITY_PARTIAL, channel.path("reasonCode").asString()),
            () -> assertEquals(Map.of("entityNo", AgreementBuilder.ENTITY_NO, "paymentMethodCode",
                CommercialCodes.PaymentMethod.CARD, "mccCode", CLIENT_MCC, "currencies", "USD"), params(channel)),
            () -> assertEquals(2, channelIdentity.onlyRequestItems().size()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(CARD_ID)),
            () -> assertChange(statusChangedPayload(CapabilityGateMocker.ROOT_VERSION + 1).path("changes").get(0), CARD_ID,
                AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES, AgreementCapabilityExecutionStatus.ENABLED,
                "PREREQUISITE"));
    }

    /**
     * AC-08 两个覆盖币种都不就绪：④ 不满足，原因取字典序第一个不就绪币种 SAR 的原因，currencies 列出全部不就绪币种 SAR,USD；等前置保持
     */
    @Test
    void test_channelIdentityAllCurrenciesNotReady() {
        mockTwoCurrencyCapability();
        channelIdentity.mockNotReady(AgreementBuilder.ENTITY_NO, CommercialCodes.PaymentMethod.CARD, CLIENT_MCC,
            ChannelIdentityReadinessReason.CURRENCY_NOT_SUPPORTED, "SAR");
        channelIdentity.mockNotReady(AgreementBuilder.ENTITY_NO, CommercialCodes.PaymentMethod.CARD, CLIENT_MCC,
            ChannelIdentityReadinessReason.MID_NOT_FOUND, "USD");

        JsonNode card = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION), CARD_ID);

        JsonNode channel = check(card, CapabilityPrerequisiteCode.CHANNEL_IDENTITY);
        assertAll(() -> assertEquals(CapabilityPrerequisiteResult.UNSATISFIED.getCode(), channel.path("result").asString()),
            () -> assertEquals(ChannelIdentityReadinessReason.CURRENCY_NOT_SUPPORTED.getCode(),
                channel.path("reasonCode").asString()),
            () -> assertEquals("SAR,USD", channel.path("params").path("currencies").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(), executionStatus(CARD_ID)));
        events.assertNoEnvelope(statusChangedKey(CapabilityGateMocker.ROOT_VERSION + 1));
    }

    /** 等前置的 CARD 接受 SAR / USD，两个币种都有价格；资格合格、主 MCC 已确认。 */
    void mockTwoCurrencyCapability() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        mockCardPrices(gate.mockClientPricingVersion(CommercialTermVersionStatus.ACTIVE), "USD", "SAR");
        gate.mockCapability(agreement, CARD_ID, AgreementBuilder.ENTITY_NO, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES, "USD", "SAR");
        mockAgreementQualified();
        classification.mockConfirmed(AgreementBuilder.CLIENT_ID, CLIENT_MCC);
    }

    /** AC-09 Client 主 MCC 未确认：④ 不满足 CLIENT_MCC_MISSING（参数 clientId），不再调用 channel-connector。 */
    @Test
    void test_clientMccMissing() {
        gate.mockCapability(coveredAgreement(), CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        mockAgreementQualified();
        classification.mockUnconfirmed(AgreementBuilder.CLIENT_ID);

        JsonNode channel = check(capability(check().assertOk(), CARD_ID), CapabilityPrerequisiteCode.CHANNEL_IDENTITY);

        assertAll(() -> assertEquals(CapabilityPrerequisiteResult.UNSATISFIED.getCode(), channel.path("result").asString()),
            () -> assertEquals(CapabilityPrerequisiteReasons.CLIENT_MCC_MISSING, channel.path("reasonCode").asString()),
            () -> assertEquals(Long.toString(AgreementBuilder.CLIENT_ID), channel.path("params").path("clientId").asString()),
            () -> assertEquals(1, classification.count()), () -> assertEquals(0, channelIdentity.count()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(), executionStatus(CARD_ID)));
    }

    /**
     * AC-10 channel-connector 断连、返回配置损坏 500，或 client-master 不可用：④ UNKNOWN（CHECK_UNAVAILABLE），已开通项不被暂停、不写库
     */
    @Test
    void test_channelIdentityUnavailable_unknown() {
        gate.mockCapability(coveredAgreement(), CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.ENABLED);
        mockAgreementQualified();
        classification.mockConfirmed(AgreementBuilder.CLIENT_ID, CLIENT_MCC);
        channelIdentity.mockUnavailable();
        JsonNode disconnected = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION), CARD_ID);
        channelIdentity.mockProblem(500, "INTERNAL_ERROR");
        JsonNode broken = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION), CARD_ID);
        classification.mockUnavailable();
        JsonNode noClassification = capability(check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION), CARD_ID);

        assertAll(() -> assertUnknownChannelIdentity(disconnected), () -> assertUnknownChannelIdentity(broken),
            () -> assertUnknownChannelIdentity(noClassification), () -> assertEquals(2, channelIdentity.count()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(CARD_ID)));
        events.assertNoEnvelope(statusChangedKey(CapabilityGateMocker.ROOT_VERSION + 1));
    }

    /** AC-07 已开通后 OE 下 MID 关闭（④ MID_NOT_FOUND）→ SUSPENDED_BY_PREREQUISITE 并发布事件；MID 恢复后 → ENABLED 并发布事件。 */
    @Test
    void test_enabledMidClosedThenRestored() {
        gate.mockCapability(coveredAgreement(), CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.ENABLED);
        mockAgreementQualified();
        classification.mockConfirmed(AgreementBuilder.CLIENT_ID, CLIENT_MCC);
        channelIdentity.mockNotReady(AgreementBuilder.ENTITY_NO, CommercialCodes.PaymentMethod.CARD, CLIENT_MCC,
            ChannelIdentityReadinessReason.MID_NOT_FOUND, "USD");

        check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION + 1);
        String suspended = executionStatus(CARD_ID);
        JsonNode suspension = statusChangedPayload(CapabilityGateMocker.ROOT_VERSION + 1).path("changes").get(0);
        channelIdentity.mockReady(AgreementBuilder.ENTITY_NO, CommercialCodes.PaymentMethod.CARD, CLIENT_MCC, "USD");
        check().assertOk().assertVersion(CapabilityGateMocker.ROOT_VERSION + 2);

        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE.getCode(), suspended),
            () -> assertChange(suspension, CARD_ID, AgreementCapabilityExecutionStatus.ENABLED,
                AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE, "PREREQUISITE"),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(CARD_ID)),
            () -> assertChange(statusChangedPayload(CapabilityGateMocker.ROOT_VERSION + 2).path("changes").get(0), CARD_ID,
                AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE, AgreementCapabilityExecutionStatus.ENABLED,
                "PREREQUISITE"));
    }

    /** ④ 为 UNKNOWN（CHECK_UNAVAILABLE），执行状态不变。 */
    static void assertUnknownChannelIdentity(JsonNode capability) {
        assertCheck(capability, CapabilityPrerequisiteCode.CHANNEL_IDENTITY, CapabilityPrerequisiteResult.UNKNOWN,
            CapabilityPrerequisiteReasons.CHECK_UNAVAILABLE);
        assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), capability.path("executionStatus").asString());
    }

    /** 版本下各币种一条默认 OE 的通用卡价格。 */
    void mockCardPrices(CommercialTermVersion version, String... currencies) {
        for (int index = 0; index < currencies.length; index++) {
            pricing.mockPrice(CommercialTermPriceBuilder.template().termPriceId(CommercialTermPriceBuilder.TERM_PRICE_ID + index)
                    .termVersionId(version.getTermVersionId()).currency(currencies[index]),
                CommercialTermComponentBuilder.template()
                    .termComponentId(CommercialTermComponentBuilder.TERM_COMPONENT_ID + index).currency(currencies[index])
                    .rate(new BigDecimal("0.03")));
        }
    }

    /** 一项结论的文案参数。 */
    static Map<String, String> params(JsonNode check) {
        Map<String, String> params = new LinkedHashMap<>();
        check.path("params").properties().forEach(entry -> params.put(entry.getKey(), entry.getValue().asString()));
        return params;
    }

    /** 默认合同：ACTIVE、ACTIVE 版本与覆盖 CARD/USD 的价格。 */
    AgreementWriteMocker.Mocker coveredAgreement() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        gate.mockCardPrice(gate.mockClientPricingVersion(CommercialTermVersionStatus.ACTIVE), 0);
        return agreement;
    }

    /** 调用核对接口。 */
    CommercialWriteResponseChecker check() {
        return post(PATH, null);
    }

    /** 响应中的一个开通项。 */
    static JsonNode capability(CommercialWriteResponseChecker response, long agreementCapabilityId) {
        for (JsonNode capability : response.node("capabilities")) {
            if (capability.path("agreementCapabilityId").asString().equals(Long.toString(agreementCapabilityId))) {
                return capability;
            }
        }
        throw new AssertionError("响应缺少开通项: " + agreementCapabilityId);
    }

    /** 开通项的一项结论。 */
    static JsonNode check(JsonNode capability, CapabilityPrerequisiteCode code) {
        for (JsonNode check : capability.path("checks")) {
            if (check.path("code").asString().equals(code.getCode())) {
                return check;
            }
        }
        throw new AssertionError("开通项缺少检查项: " + code.getCode());
    }

    /** 断言一项结论与原因码（null 表示原因码为空）。 */
    static void assertCheck(JsonNode capability, CapabilityPrerequisiteCode code, CapabilityPrerequisiteResult result,
        String reasonCode) {
        JsonNode check = check(capability, code);
        assertEquals(result.getCode(), check.path("result").asString(), code.getCode());
        assertEquals(reasonCode, check.path("reasonCode").isNull() ? null : check.path("reasonCode").asString(), code.getCode());
    }

    /** 断言事件中的一条变化。 */
    static void assertChange(JsonNode change, long agreementCapabilityId, AgreementCapabilityExecutionStatus from,
        AgreementCapabilityExecutionStatus to, String trigger) {
        assertEquals(Map.of("id", Long.toString(agreementCapabilityId), "from", from.getCode(), "to", to.getCode(), "trigger",
            trigger), Map.of("id", change.path("agreementCapabilityId").asString(), "from",
            change.path("fromExecutionStatus").asString(), "to", change.path("toExecutionStatus").asString(), "trigger",
            change.path("trigger").asString()));
    }

    /** 数据库中的执行状态。 */
    String executionStatus(long agreementCapabilityId) {
        return (String) testMapper.agreementCapability(agreementCapabilityId).get("execution_status");
    }

    /** 某合同版本的执行状态变更事件键。 */
    static String statusChangedKey(long changeVersion) {
        return AgreementCapabilityStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID, changeVersion).eventKey();
    }

    /** 等待该版本的执行状态变更事件并返回 Payload。 */
    JsonNode statusChangedPayload(long changeVersion) {
        JsonNode envelope = events.awaitEnvelope(AgreementCapabilityStatusChangedHandler.EVENT_TYPE,
            statusChangedKey(changeVersion));
        assertEquals(AgreementCapabilityStatusChangedPayload.EVENT_TYPE, envelope.path("eventType").asString());
        return envelope.path("payload");
    }
}
