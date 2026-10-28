package com.paytech.commercial.test.mocker.local.agreement;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeOperation;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.service.impl.BusinessChangeHistoryWriter;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.AgreementProductBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.mocker.local.catalog.ProductCatalogMocker;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.commercial.test.mocker.local.term.TermPricingMocker;

import lombok.AllArgsConstructor;

/**
 * 开通项前置门禁的本地前置数据：合同（带 BCR Root）、订阅、带受审计 Child 身份与 CREATE 快照的开通项，以及 CLIENT_PRICING 方案的
 * ACTIVE 版本价格
 * <li>经正式 Repository 插入，不调用被测写入命令；行由 CommercialDataCleaner 在每例结束后统一清理。无可变状态。</li>
 * <li>默认合同为 AgreementBuilder 模板（CLIENT、AE993、Client 930000000000901），开通项为 CARD、CURRENCY=USD。</li>
 */
@TestComponent
@AllArgsConstructor
public class CapabilityGateMocker {
    /** 第二份 CLIENT 合同（同一 Client、另一 OE AE994）。 */
    public static final long SECOND_AGREEMENT_ID = AgreementBuilder.AGREEMENT_ID + 1000;

    /** 第二份合同的 OE。 */
    public static final String SECOND_ENTITY_NO = "AE994";

    /** 合同 Root 初始版本。 */
    public static final long ROOT_VERSION = 5;

    private final TermPricingMocker pricing;
    private final ProductCatalogMocker catalog;
    private final AgreementWriteMocker agreements;
    private final AgreementSubscriptionMocker subscriptions;
    private final BusinessChangeHistoryWriter historyWriter;

    /** 种子产品目录与 ACTIVE 通用卡画像（门禁判定价格覆盖需要）。 */
    public void mockCatalogAndProfile() {
        catalog.mockSeedCatalog();
        pricing.mockProfile(PricingProfileBuilder.template().status(PricingProfileStatus.ACTIVE));
    }

    /**
     * 插入合同（Root 版本 {@value #ROOT_VERSION}）与 ACQUIRING 订阅
     *
     * @param builder 合同 Builder
     * @return 合同 Mocker，可继续登记受审计 Child
     */
    public AgreementWriteMocker.Mocker mockAgreement(AgreementBuilder builder) {
        AgreementWriteMocker.Mocker mocker = agreements.mock(builder, ROOT_VERSION);
        long agreementId = mocker.agreement().getAgreementId();
        subscriptions.mockProduct(AgreementProductBuilder.template().agreementProductId(agreementId + 60)
            .agreementId(agreementId));
        return mocker;
    }

    /** 默认 ACTIVE CLIENT 合同。 */
    public AgreementWriteMocker.Mocker mockActiveAgreement() {
        return mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE));
    }

    /** 第二份 ACTIVE CLIENT 合同：同一 Client，OE AE994。 */
    public AgreementWriteMocker.Mocker mockSecondAgreement() {
        return mockAgreement(AgreementBuilder.template().agreementId(SECOND_AGREEMENT_ID).agreementNo("GATE-AGREEMENT-2")
            .entityNo(SECOND_ENTITY_NO).status(AgreementStatus.ACTIVE));
    }

    /**
     * 在合同上插入一个 CURRENCY=USD 的开通项并登记其受审计 Child 身份
     *
     * @param agreement 合同 Mocker
     * @param agreementCapabilityId 开通项 ID
     * @param capabilityCode 可开通项代码
     * @param executionStatus 执行状态
     * @return 已插入的开通项
     */
    public AgreementCapability mockCapability(AgreementWriteMocker.Mocker agreement, long agreementCapabilityId,
        String capabilityCode, AgreementCapabilityExecutionStatus executionStatus) {
        return mockCapability(agreement, agreementCapabilityId, agreement.agreement().getEntityNo(), capabilityCode,
            executionStatus);
    }

    /**
     * 在合同的指定 OE 上插入一个 CURRENCY=USD 的开通项并登记其受审计 Child 身份与 CREATE 快照（PARTNER 合同用）
     *
     * @param agreement 合同 Mocker
     * @param agreementCapabilityId 开通项 ID
     * @param entityNo 准确 OE
     * @param capabilityCode 可开通项代码
     * @param executionStatus 执行状态
     * @return 已插入的开通项
     */
    public AgreementCapability mockCapability(AgreementWriteMocker.Mocker agreement, long agreementCapabilityId,
        String entityNo, String capabilityCode, AgreementCapabilityExecutionStatus executionStatus) {
        return mockCapability(agreement, agreementCapabilityId, entityNo, capabilityCode, executionStatus, "USD");
    }

    /**
     * 在合同的指定 OE 上插入一个指定 CURRENCY 取值的开通项并登记其受审计 Child 身份与 CREATE 快照
     *
     * @param agreement 合同 Mocker
     * @param agreementCapabilityId 开通项 ID
     * @param entityNo 准确 OE
     * @param capabilityCode 可开通项代码
     * @param executionStatus 执行状态
     * @param currencies CURRENCY 条件取值
     * @return 已插入的开通项
     */
    public AgreementCapability mockCapability(AgreementWriteMocker.Mocker agreement, long agreementCapabilityId,
        String entityNo, String capabilityCode, AgreementCapabilityExecutionStatus executionStatus, String... currencies) {
        AgreementSubscriptionMocker.Mocker mocker = subscriptions.mockCapability(AgreementCapabilityBuilder.template()
            .agreementCapabilityId(agreementCapabilityId).agreementId(agreement.agreement().getAgreementId())
            .entityNo(entityNo).capabilityCode(capabilityCode).executionStatus(executionStatus))
            .currencies(currencies);
        // 以正式 BCR 写入协作者登记 CREATE 快照（版本为 Root 当前版本），使事件重建与完整性校验有真实的上一快照。
        historyWriter.createChild(agreement.root(), BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY, agreementCapabilityId,
            agreementCapabilityId, BusinessChangeReason.AGREEMENT_CAPABILITY_ADDED,
            BusinessChangeSnapshot.agreementCapabilityData(mocker.capability(), mocker.limits()), AgreementBuilder.ACTOR_ID,
            CheckoutFixture.NOW);
        return mocker.capability();
    }

    /**
     * 默认合同的 CLIENT_PRICING 方案（BCR 根修订 2）与给定状态的第 1 版，不含价格
     *
     * @param status 版本状态（ACTIVE 时占用 active_slot）
     * @return 第 1 版
     */
    public CommercialTermVersion mockClientPricingVersion(CommercialTermVersionStatus status) {
        boolean active = status == CommercialTermVersionStatus.ACTIVE;
        return pricing.mockPlan(CommercialTermPlanBuilder.template()
                .status(active ? CommercialTermPlanStatus.ACTIVE : CommercialTermPlanStatus.DRAFT), 2,
            CommercialTermVersionBuilder.template().status(status).activeSlot(active ? 1 : null)
                .effectiveFrom(AgreementCapabilityBuilder.EFFECTIVE_FROM)).getFirst();
    }

    /**
     * 版本下一条默认合同 OE 的 USD 通用卡价格（SALE 百分比 3%），覆盖 CURRENCY=USD 的 CARD 开通项
     *
     * @param version 版本
     * @param index 序号（同一用例内唯一）
     */
    public void mockCardPrice(CommercialTermVersion version, int index) {
        pricing.mockPrice(CommercialTermPriceBuilder.template().termPriceId(CommercialTermPriceBuilder.TERM_PRICE_ID + index)
                .termVersionId(version.getTermVersionId()),
            CommercialTermComponentBuilder.template().termComponentId(CommercialTermComponentBuilder.TERM_COMPONENT_ID + index)
                .rate(new BigDecimal("0.03")));
    }

    /** 默认合同完整前置：目录与画像、ACTIVE 合同、ACTIVE 版本及覆盖 CARD/USD 的价格。 */
    public AgreementWriteMocker.Mocker mockCoveredAgreement() {
        mockCatalogAndProfile();
        AgreementWriteMocker.Mocker agreement = mockActiveAgreement();
        mockCardPrice(mockClientPricingVersion(CommercialTermVersionStatus.ACTIVE), 0);
        return agreement;
    }

    /**
     * 以正式 BCR 写入协作者把合同 Root 推进一版，并为开通项追加一条执行状态快照（不改开通项行）；用于直接驱动发布 Handler 的事件重建
     *
     * @param agreement 合同 Mocker
     * @param capability 已插入的开通项
     * @param executionStatus 快照中的执行状态
     * @param reason 快照原因
     * @return 推进后的 Root 版本
     */
    public long mockExecutionSnapshot(AgreementWriteMocker.Mocker agreement, AgreementCapability capability,
        AgreementCapabilityExecutionStatus executionStatus, BusinessChangeReason reason) {
        BusinessChangeRecord root = agreement.root();
        long triggerId = historyWriter.advanceRoot(root, root.getCurrentVersion(), BusinessChangeSnapshot.TRIGGER_SYSTEM,
            reason, BusinessChangeSnapshot.agreementData(agreement.agreement()), 0L, CheckoutFixture.NOW);
        AgreementCapability snapshot = new AgreementCapability();
        snapshot.setAgreementCapabilityId(capability.getAgreementCapabilityId());
        snapshot.setAgreementId(capability.getAgreementId());
        snapshot.setEntityNo(capability.getEntityNo());
        snapshot.setCapabilityCode(capability.getCapabilityCode());
        snapshot.setStatus(capability.getStatus());
        snapshot.setExecutionStatus(executionStatus);
        snapshot.setEffectiveFrom(capability.getEffectiveFrom());
        snapshot.setRequire3ds(capability.getRequire3ds());
        historyWriter.changeChild(root, BusinessChangeRecordRecordType.AGREEMENT_CAPABILITY, capability.getAgreementCapabilityId(),
            BusinessChangeOperation.UPDATE, BusinessChangeSnapshot.TRIGGER_SYSTEM, triggerId, reason,
            BusinessChangeSnapshot.agreementCapabilityData(snapshot, List.of()), 0L, CheckoutFixture.NOW);
        return root.getCurrentVersion();
    }

    /** 默认 CARD 可开通项代码。 */
    public static String card() {
        return CommercialCodes.Capability.CARD;
    }
}
