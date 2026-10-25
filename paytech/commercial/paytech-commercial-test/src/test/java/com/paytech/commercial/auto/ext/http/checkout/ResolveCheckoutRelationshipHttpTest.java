package com.paytech.commercial.auto.ext.http.checkout;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.commercial.api.checkout.CheckoutContextApi;
import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundFlow;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;
import com.paytech.commercial.test.base.checkout.AbstractCheckoutApiTest;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.compliance.QualificationEvaluationMocker;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;

/**
 * 真实解析的 HTTP 成功路径、资格与商业关系约束（technique.md §3.1、§3.2，AC-04 / 05 / 06 / 10）。
 * <li>夹具：AE991（优先级 10，MOR 合同 106）与 AE992（优先级 20，MOR 合同 206）都是 PARTNER 来源，提供方分别为 Local Partner 150 / 250，
 * 法律主体与各自 OE 相同，Partner 合同 160 / 260 含 PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE，平台签约方 Party 900；两个 OE 的收单资质都是
 * PAYFAC。</li>
 * <li>compliance 与 client-master 均为测试 HTTP 服务：资格按主体配置实时状态，主档变体在 DTO 上修改。</li>
 *
 * @see CheckoutContextApi#resolve(String, ResolveCheckoutContextRequestBody)
 */
class ResolveCheckoutRelationshipHttpTest extends AbstractCheckoutApiTest {
    private static final String FIRST_ENTITY = "AE991";
    private static final String SECOND_ENTITY = "AE992";
    private static final long CLIENT_AGREEMENT_ID = 910000000000106L;
    private static final String CLIENT_ID = Long.toString(CheckoutFixture.CLIENT_ID);
    private static final String CLIENT_PARTY = "910000000000002";
    private static final String FIRST_ENTITY_PARTY = "910000000000102";
    private static final String FIRST_PARTNER = Long.toString(CheckoutFixture.FIRST_PARTNER_ID);
    private static final String SECOND_PARTNER = Long.toString(CheckoutFixture.SECOND_PARTNER_ID);
    private static final String PLATFORM_PARTY = Long.toString(CheckoutFixture.PLATFORM_PARTY_ID);

    @Autowired
    CommercialTestMapper data;

    /** 每例准备商务事实、默认主档候选与复核通过；资格由各用例配置。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture.install();
        clientMaster.mockCandidates();
        clientMaster.mockVerified(true);
    }

    /** MOR（C1）首个真实成功路径：选中 AE991，快照 8 个角色、卖方公开文本透传，资格一次批量判定且只复核一次。 */
    @Test
    void test_normalFlow_morSnapshot() {
        compliance.mockAllEligible();

        var body = invoke(request(), headers()).success();

        var snapshot = body.getRelationshipSnapshot();
        var seller = body.getLegal().getSeller();
        assertAll(() -> assertEquals(FIRST_ENTITY, body.getEntityNo()),
            () -> assertEquals("PARTNER", body.getSourceType()),
            () -> assertEquals(FIRST_PARTNER, body.getPartnerTerms().getLocalPartnerId()),
            () -> assertEquals(PLATFORM_PARTY, body.getPartnerTerms().getPlatformSignatoryPartyId()),
            () -> assertEquals(0, snapshot.getEffectiveModelVersion()),
            () -> assertEquals(1, snapshot.getSnapshotModelVersion()),
            () -> assertEquals(FundFlow.THROUGH_CUSTODIAN.getCode(), snapshot.getFundFlow()),
            () -> assertEquals(BusinessMode.MOR.getCode(), snapshot.getBusinessMode()),
            () -> assertEquals(List.of(
                binding(RelationshipRole.CLIENT, RelationshipSubjectType.CLIENT, CLIENT_ID, CLIENT_PARTY),
                binding(RelationshipRole.SELLER, RelationshipSubjectType.OPERATING_ENTITY, FIRST_ENTITY, FIRST_ENTITY_PARTY),
                binding(RelationshipRole.ACQUIRING_OE, RelationshipSubjectType.OPERATING_ENTITY, FIRST_ENTITY,
                    FIRST_ENTITY_PARTY),
                binding(RelationshipRole.OE_PROVIDER, RelationshipSubjectType.LOCAL_PARTNER, FIRST_PARTNER, FIRST_ENTITY_PARTY),
                binding(RelationshipRole.FUNDS_CUSTODIAN, RelationshipSubjectType.OPERATING_ENTITY, FIRST_ENTITY,
                    FIRST_ENTITY_PARTY),
                binding(RelationshipRole.TAX_RESPONSIBLE, RelationshipSubjectType.OPERATING_ENTITY, FIRST_ENTITY,
                    FIRST_ENTITY_PARTY),
                binding(RelationshipRole.PLATFORM_FEE_PAYER, RelationshipSubjectType.LOCAL_PARTNER, FIRST_PARTNER,
                    FIRST_ENTITY_PARTY),
                binding(RelationshipRole.PLATFORM, RelationshipSubjectType.PLATFORM, null, PLATFORM_PARTY)),
                bindings(body)),
            () -> assertEquals("AE991 Synthetic Seller LLC", seller.getLegalName()),
            () -> assertEquals("REG-AE991", seller.getRegistrationNumber()),
            () -> assertEquals("TRN-AE991", seller.getTaxRegistrationNumber()),
            () -> assertEquals("1 Synthetic Street, Dubai, AE991", seller.getAddressText()),
            () -> assertEquals("support-ae991@example.test", seller.getSupportEmail()),
            () -> assertNull(seller.getSupportPhone()),
            () -> assertEquals(List.of(subject(QualificationRoleType.CLIENT, CLIENT_ID),
                subject(QualificationRoleType.OPERATING_ENTITY, FIRST_ENTITY),
                subject(QualificationRoleType.LOCAL_PARTNER, FIRST_PARTNER),
                subject(QualificationRoleType.OPERATING_ENTITY, SECOND_ENTITY),
                subject(QualificationRoleType.LOCAL_PARTNER, SECOND_PARTNER)), compliance.onlyRequestSubjects()),
            () -> assertEquals(1, clientMaster.count("verify:" + FIRST_ENTITY)));
    }

    /** PSP（C2）：卖方与税务责任为 Client，另有 ACQUIRER_SUB_MERCHANT，资金保管方仍为收单 OE。 */
    @Test
    void test_pspSnapshot() {
        compliance.mockAllEligible();
        data.overwriteAgreementModes(CLIENT_AGREEMENT_ID, BusinessMode.PSP, FundMode.SETTLE_THROUGH_PLATFORM);

        var body = invoke(request(), headers()).success();

        assertAll(() -> assertEquals(FIRST_ENTITY, body.getEntityNo()),
            () -> assertEquals(BusinessMode.PSP.getCode(), body.getRelationshipSnapshot().getBusinessMode()),
            () -> assertEquals(List.of(
                binding(RelationshipRole.CLIENT, RelationshipSubjectType.CLIENT, CLIENT_ID, CLIENT_PARTY),
                binding(RelationshipRole.SELLER, RelationshipSubjectType.CLIENT, CLIENT_ID, CLIENT_PARTY),
                binding(RelationshipRole.ACQUIRING_OE, RelationshipSubjectType.OPERATING_ENTITY, FIRST_ENTITY,
                    FIRST_ENTITY_PARTY),
                binding(RelationshipRole.OE_PROVIDER, RelationshipSubjectType.LOCAL_PARTNER, FIRST_PARTNER, FIRST_ENTITY_PARTY),
                binding(RelationshipRole.FUNDS_CUSTODIAN, RelationshipSubjectType.OPERATING_ENTITY, FIRST_ENTITY,
                    FIRST_ENTITY_PARTY),
                binding(RelationshipRole.TAX_RESPONSIBLE, RelationshipSubjectType.CLIENT, CLIENT_ID, CLIENT_PARTY),
                binding(RelationshipRole.PLATFORM_FEE_PAYER, RelationshipSubjectType.LOCAL_PARTNER, FIRST_PARTNER,
                    FIRST_ENTITY_PARTY),
                binding(RelationshipRole.ACQUIRER_SUB_MERCHANT, RelationshipSubjectType.CLIENT, CLIENT_ID, CLIENT_PARTY),
                binding(RelationshipRole.PLATFORM, RelationshipSubjectType.PLATFORM, null, PLATFORM_PARTY)),
                bindings(body)));
    }

    /** Client 资格不合格整单拒绝：不按候选落空，不进入复核。 */
    @Test
    void test_clientNotEligible() {
        compliance.mockAllEligible();
        compliance.mockStatus(QualificationRoleType.CLIENT, CLIENT_ID, QualificationEvaluationStatus.REJECTED);

        fixture.assertReadOnly(() -> invoke(request(), headers()).assertProblem(422, "CLIENT_NOT_ELIGIBLE")
            .assertNoCommercialFacts());

        assertEquals(1, compliance.count());
        assertEquals(0, clientMaster.count("verify"));
    }

    /** 没有候选时 Client 资格仍先判定：不合格报 CLIENT_NOT_ELIGIBLE，合格才报 NO_ELIGIBLE_ENTITY；请求只含 Client。 */
    @Test
    void test_clientNotEligibleWithoutCandidates() {
        clientMaster.mockNoCandidates();
        compliance.mockStatus(QualificationRoleType.CLIENT, CLIENT_ID, QualificationEvaluationStatus.SETTLEMENT_BLOCKED);

        invoke(request(), headers()).assertProblem(422, "CLIENT_NOT_ELIGIBLE");
        assertEquals(List.of(subject(QualificationRoleType.CLIENT, CLIENT_ID)), compliance.onlyRequestSubjects());

        compliance.mockStatus(QualificationRoleType.CLIENT, CLIENT_ID, QualificationEvaluationStatus.ELIGIBLE);
        invoke(request(), headers()).assertProblem(422, "NO_ELIGIBLE_ENTITY");
        assertEquals(2, compliance.count());
    }

    /** 收单 OE 或其提供方任一不合格：候选落空并由下一候选接替；两个候选都不合格时 NO_ELIGIBLE_ENTITY。 */
    @Test
    void test_candidateQualificationFallsThrough() {
        compliance.mockAllEligible();
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, FIRST_ENTITY, QualificationEvaluationStatus.EXPIRED);
        assertEquals(SECOND_ENTITY, invoke(request(), headers()).success().getEntityNo());

        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, FIRST_ENTITY, QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.LOCAL_PARTNER, FIRST_PARTNER, QualificationEvaluationStatus.REVOKED);
        assertEquals(SECOND_ENTITY, invoke(request(), headers()).success().getEntityNo());

        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, SECOND_ENTITY,
            QualificationEvaluationStatus.NOT_YET_VALID);
        fixture.assertReadOnly(() -> invoke(request(), headers()).assertProblem(422, "NO_ELIGIBLE_ENTITY"));
        assertEquals(0, clientMaster.count("verify:" + FIRST_ENTITY));
    }

    /** 收单资质三档：MERCHANT 不承接；MERCHANT_RESALE_APPROVED 只承接 MOR；PAYFAC 承接 MOR 与 PSP。 */
    @Test
    void test_acquiringQualificationTiers() {
        compliance.mockAllEligible();
        mockFirstCandidate(candidate -> candidate.setAcquiringQualification(AcquiringQualification.MERCHANT.getCode()));
        assertEquals(SECOND_ENTITY, invoke(request(), headers()).success().getEntityNo());

        mockFirstCandidate(
            candidate -> candidate.setAcquiringQualification(AcquiringQualification.MERCHANT_RESALE_APPROVED.getCode()));
        assertEquals(FIRST_ENTITY, invoke(request(), headers()).success().getEntityNo());

        data.overwriteAgreementModes(CLIENT_AGREEMENT_ID, BusinessMode.PSP, FundMode.SETTLE_THROUGH_PLATFORM);
        assertEquals(SECOND_ENTITY, invoke(request(), headers()).success().getEntityNo());

        mockFirstCandidate(candidate -> candidate.setAcquiringQualification(AcquiringQualification.PAYFAC.getCode()));
        assertEquals(FIRST_ENTITY, invoke(request(), headers()).success().getEntityNo());
    }

    /** 提供方法律主体与 OE 不同、提供方非 ACTIVE 或没有 OE_PROVIDER 角色：候选落空。 */
    @Test
    void test_providerConstraintsFallThrough() {
        compliance.mockAllEligible();
        mockFirstCandidate(candidate -> candidate.getOeProvider().setPartyId("910000000000199"));
        assertEquals(SECOND_ENTITY, invoke(request(), headers()).success().getEntityNo());

        mockFirstCandidate(candidate -> candidate.getOeProvider().setStatus("SUSPENDED"));
        assertEquals(SECOND_ENTITY, invoke(request(), headers()).success().getEntityNo());

        mockFirstCandidate(candidate -> candidate.getOeProvider().setRoles(List.of(PartnerRoleCode.REFERRER.getCode())));
        assertEquals(SECOND_ENTITY, invoke(request(), headers()).success().getEntityNo());
    }

    /** OE 来源 CLIENT（C3）本轮不支持快照：候选落空，资格请求不含提供方。 */
    @Test
    void test_clientSourceIsUnsupported() {
        compliance.mockAllEligible();
        mockFirstCandidate(candidate -> {
            candidate.getSourceRelation().setSourceType("CLIENT");
            candidate.getSourceRelation().setSourceClientId(CLIENT_ID);
            candidate.getSourceRelation().setSourcePartnerId(null);
            candidate.setLocalPartner(null);
            candidate.setOeProvider(null);
        });

        assertEquals(SECOND_ENTITY, invoke(request(), headers()).success().getEntityNo());
        assertEquals(List.of(subject(QualificationRoleType.CLIENT, CLIENT_ID),
            subject(QualificationRoleType.OPERATING_ENTITY, FIRST_ENTITY),
            subject(QualificationRoleType.OPERATING_ENTITY, SECOND_ENTITY),
            subject(QualificationRoleType.LOCAL_PARTNER, SECOND_PARTNER)), compliance.onlyRequestSubjects());
    }

    /** 不指定支付方式时返回全部有生效价格的 PAYMENT_METHOD 开通项：AE991 只有 CARD 有价格。 */
    @Test
    void test_withoutPaymentMethodReturnsAllPaymentMethods() {
        compliance.mockAllEligible();

        var capabilities = invoke(request(), headers()).success().getClientTerms().getCapabilities();

        assertEquals(List.of(CommercialCodes.Capability.CARD),
            capabilities.stream().map(ResolveCheckoutContextResponseBody.Capability::getCapabilityCode).toList());
    }

    /** client-master 复核报告变化：409 CCT_CONTEXT_CHANGED，不改选其他 OE。 */
    @Test
    void test_verifyChangedIsConflict() {
        compliance.mockAllEligible();
        clientMaster.mockVerified(false, "OPERATING_ENTITY:" + FIRST_ENTITY);

        invoke(request(), headers()).assertProblem(409, "CCT_CONTEXT_CHANGED").assertNoCommercialFacts();
        assertEquals(1, clientMaster.count("verify"));
    }

    /** 修改默认候选中 AE991 的 DTO 后作为本例 client-master 响应。 */
    private void mockFirstCandidate(Consumer<CheckoutCandidatesResponseBody.Candidate> change) {
        var sample = clientMaster.sample();
        change.accept(sample.getCandidates().getFirst());
        clientMaster.mockCandidates(sample);
    }

    /** 快照绑定的可比较文本。 */
    static List<String> bindings(ResolveCheckoutContextResponseBody body) {
        return body.getRelationshipSnapshot().getBindings().stream()
            .map(b -> b.getRole() + "|" + b.getSubjectType() + "|" + b.getSubjectId() + "|" + b.getPartyId()).toList();
    }

    /** 期望绑定的可比较文本。 */
    static String binding(RelationshipRole role, RelationshipSubjectType type, String subjectId, String partyId) {
        return role.getCode() + "|" + type.getCode() + "|" + subjectId + "|" + partyId;
    }

    /** 资格请求主体键。 */
    private static String subject(QualificationRoleType type, String id) {
        return QualificationEvaluationMocker.key(type.getCode(), id);
    }
}
