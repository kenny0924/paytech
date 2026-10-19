package com.paytech.commercial.test.mocker.local.checkout;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;

import lombok.AllArgsConstructor;

/**
 * Checkout 夹具第一份合同（AE991）条款方案下的后继版本：经正式 Repository 插入版本及其 AE991 / USD 价格与组件。
 * <li>无可变状态；插入的行由 CommercialDataCleaner 在每例结束后统一清理。价格 ID = 版本 ID + 1，组件 ID = 版本 ID + 2。</li>
 */
@TestComponent
@AllArgsConstructor
public class CheckoutTermVersionMocker {
    /** 夹具第一份 CLIENT 合同。 */
    public static final long AGREEMENT_ID = 910000000000106L;

    /** 夹具第一份合同的 CLIENT_PRICING 方案。 */
    public static final long TERM_PLAN_ID = 910000000000112L;

    /** 夹具第一份合同方案下的原 ACTIVE 版本。 */
    public static final long TERM_VERSION_ID = 910000000000113L;

    /** 夹具价格引用的画像代码。 */
    public static final String PRICING_PROFILE_CODE = "CCTQUERY_CARD";

    private final CommercialTermVersionRepository versionRepository;
    private final CommercialTermPriceRepository priceRepository;
    private final CommercialTermComponentRepository componentRepository;

    /**
     * 插入版本号 2 的后继版本，区间自 effectiveFrom 起无结束。
     *
     * @param termVersionId 后继版本 ID
     * @param status 后继版本状态
     * @param effectiveFrom 生效起点
     * @return 已插入的后继版本
     */
    public CommercialTermVersion mockSuccessorVersion(long termVersionId, CommercialTermVersionStatus status,
        Instant effectiveFrom) {
        CommercialTermVersion version = CommercialTermVersionBuilder.template().termVersionId(termVersionId)
            .termPlanId(TERM_PLAN_ID).agreementId(AGREEMENT_ID).versionNo(2L).status(status)
            .activeSlot(status == CommercialTermVersionStatus.ACTIVE ? 1 : null).effectiveFrom(effectiveFrom)
            .effectiveTo(null).build();
        versionRepository.insert(version);
        CommercialTermPrice price = CommercialTermPriceBuilder.template().termPriceId(termVersionId + 1)
            .termVersionId(termVersionId).entityNo("AE991").pricingProfileCode(PRICING_PROFILE_CODE).build();
        priceRepository.insertAll(List.of(price));
        CommercialTermComponent component = CommercialTermComponentBuilder.template().termComponentId(termVersionId + 2)
            .termPriceId(termVersionId + 1).build();
        componentRepository.insertAll(List.of(component));
        return version;
    }
}
