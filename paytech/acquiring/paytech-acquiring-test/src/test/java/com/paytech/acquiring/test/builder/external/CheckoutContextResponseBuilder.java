package com.paytech.acquiring.test.builder.external;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.Capability;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.CapabilityCondition;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.Classification;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.ClientTerms;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.Derived;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.Eligibility;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.FileEvidence;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.Legal;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.PartnerTerms;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.RelationshipSnapshot;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.Revisions;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.RoleBinding;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.Seller;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.FundFlow;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;
import com.paytech.commercial.api.enums.ResponsiblePartyType;

/**
 * commercial 结账上下文解析响应 DTO；测试独立给出合法默认值，不借用生产固定 Mock 生成期望
 * <li>默认：AE001，PARTNER 来源（Local Partner 300001），MOR 合同，生效版本 1 的 8 行商业关系快照与一个 CARD 开通项。</li>
 * <li>每次 build 生成新对象；负例用 {@link #with(Consumer)} 改写本次对象，不修复非法值。</li>
 */
public final class CheckoutContextResponseBuilder {
    public static final String ENTITY_NO = "AE001";
    public static final String LOCAL_PARTNER_ID = "300001";
    public static final String OE_PARTY_ID = "601";
    public static final String CLIENT_PARTY_ID = "701";
    public static final String PLATFORM_SIGNATORY_PARTY_ID = "403";
    public static final int SNAPSHOT_MODEL_VERSION = 1;

    private final long clientId;
    private final List<Consumer<ResolveCheckoutContextResponseBody>> changes = new ArrayList<>();

    private CheckoutContextResponseBuilder(long clientId) {
        this.clientId = clientId;
    }

    /** 给定 Client 的合法默认响应。 */
    public static CheckoutContextResponseBuilder template(long clientId) {
        return new CheckoutContextResponseBuilder(clientId);
    }

    /** CLIENT 来源：无 Partner 条款、无商业关系快照（commercial 本轮对 CLIENT 来源不推导快照）。 */
    public static CheckoutContextResponseBuilder clientSource(long clientId) {
        return template(clientId).with(body -> {
            body.setSourceType(EntitySourceType.CLIENT.getCode());
            body.setPartnerTerms(null);
            body.setRelationshipSnapshot(null);
        });
    }

    /** 追加一次对本次响应对象的改写，按添加顺序执行。 */
    public CheckoutContextResponseBuilder with(Consumer<ResolveCheckoutContextResponseBody> change) {
        changes.add(change);
        return this;
    }

    /** 改写快照的生效模型版本。 */
    public CheckoutContextResponseBuilder effectiveModelVersion(int version) {
        return with(body -> body.getRelationshipSnapshot().setEffectiveModelVersion(version));
    }

    /** 改写快照角色绑定。 */
    public CheckoutContextResponseBuilder bindings(List<RoleBinding> bindings) {
        return with(body -> body.getRelationshipSnapshot().setBindings(new ArrayList<>(bindings)));
    }

    /** 构造新的响应对象。 */
    public ResolveCheckoutContextResponseBody build() {
        var body = new ResolveCheckoutContextResponseBody();
        body.setContextAsOfTime("2026-09-08T00:00:00.000000Z");
        body.setEvaluatedAt("2026-09-08T00:00:00.000000Z");
        body.setClientId(Long.toString(clientId));
        body.setEntityNo(ENTITY_NO);
        body.setSourceType(EntitySourceType.PARTNER.getCode());
        body.setClassification(new Classification("501", "502", "RETAIL", "5999"));
        body.setEligibility(new Eligibility("101", 1, "102", "103"));
        body.setClientTerms(new ClientTerms("105", "120", "104", BusinessMode.MOR.getCode(),
            FundMode.SETTLE_THROUGH_PLATFORM.getCode(), PricingAuthority.PLATFORM.getCode(), null,
            List.of(ProductCode.ACQUIRING.getCode()), new ArrayList<>(List.of(cardCapability()))));
        body.setPartnerTerms(new PartnerTerms(LOCAL_PARTNER_ID, "404", "405", "410", "401", "411", "402",
            PLATFORM_SIGNATORY_PARTY_ID));
        body.setLegal(new Legal("105", "201", "202", "203", "106",
            new ArrayList<>(List.of(new FileEvidence("201", "301", "a".repeat(64)),
                new FileEvidence("202", "302", "b".repeat(64)), new FileEvidence("203", "303", "c".repeat(64)))),
            new ArrayList<>(), new Seller("Test Seller", "TEST-REG", "TAX-001", "Test address",
                "support@example.invalid", "+971000000")));
        body.setDerived(new Derived(OE_PARTY_ID, ENTITY_NO, ResponsiblePartyType.OPERATING_ENTITY.getCode(),
            ResponsiblePartyType.OPERATING_ENTITY.getCode()));
        body.setRevisions(new Revisions("1", "1", "1", "1"));
        body.setRelationshipSnapshot(new RelationshipSnapshot(1, SNAPSHOT_MODEL_VERSION,
            FundFlow.THROUGH_CUSTODIAN.getCode(), BusinessMode.MOR.getCode(), new ArrayList<>(morBindings(clientId))));
        changes.forEach(change -> change.accept(body));
        return body;
    }

    /** MOR 合同的 8 行默认绑定，按 RelationshipRole 声明顺序。 */
    public static List<RoleBinding> morBindings(long clientId) {
        return List.of(binding(RelationshipRole.CLIENT, RelationshipSubjectType.CLIENT, Long.toString(clientId),
                CLIENT_PARTY_ID),
            binding(RelationshipRole.SELLER, RelationshipSubjectType.OPERATING_ENTITY, ENTITY_NO, OE_PARTY_ID),
            binding(RelationshipRole.ACQUIRING_OE, RelationshipSubjectType.OPERATING_ENTITY, ENTITY_NO, OE_PARTY_ID),
            binding(RelationshipRole.OE_PROVIDER, RelationshipSubjectType.LOCAL_PARTNER, LOCAL_PARTNER_ID, OE_PARTY_ID),
            binding(RelationshipRole.FUNDS_CUSTODIAN, RelationshipSubjectType.OPERATING_ENTITY, ENTITY_NO, OE_PARTY_ID),
            binding(RelationshipRole.TAX_RESPONSIBLE, RelationshipSubjectType.OPERATING_ENTITY, ENTITY_NO, OE_PARTY_ID),
            binding(RelationshipRole.PLATFORM_FEE_PAYER, RelationshipSubjectType.LOCAL_PARTNER, LOCAL_PARTNER_ID,
                OE_PARTY_ID),
            binding(RelationshipRole.PLATFORM, RelationshipSubjectType.PLATFORM, null, PLATFORM_SIGNATORY_PARTY_ID));
    }

    /** 一行角色绑定。 */
    public static RoleBinding binding(RelationshipRole role, RelationshipSubjectType subjectType, String subjectId,
        String partyId) {
        return new RoleBinding(role.getCode(), subjectType.getCode(), subjectId, partyId);
    }

    /** 默认 CARD 开通项：限 USD。 */
    static Capability cardCapability() {
        return new Capability("130", "CARD", "ENABLED", null, null, null, null, "CARD",
            CapabilityKind.PAYMENT_METHOD.getCode(), List.of(new CapabilityCondition("CURRENCY", List.of("USD"))), false);
    }
}
