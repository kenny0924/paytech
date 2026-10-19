package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 费率卡的多表查询结果（technique.md §2.7）：合同 → 订阅产品、开通项、当前与已排期版本 → 开通项 × 币种 → 按具体度排序的价格
 * <li>只承载查询数据；价格归属与排序由 RateCardQueryService 完成，入站 Converter 只做字段投影。</li>
 */
@Getter
@AllArgsConstructor
public class RateCardView {
    /** Client ID */
    private final long clientId;

    /** 费率卡时点 */
    private final Instant asOf;

    /** 币种筛选，可空 */
    private final String currency;

    /** ACTIVE 合同的费率卡，按合同 ID 升序 */
    private final List<AgreementCard> agreements;

    /** 一份合同 */
    @Getter
    @AllArgsConstructor
    public static class AgreementCard {
        /** 合同 */
        private final Agreement agreement;

        /** 订阅产品，按代码排序 */
        private final List<ProductCode> products;

        /** 开通项 */
        private final List<CapabilityCard> capabilities;

        /** 当前与已排期版本 */
        private final List<VersionCard> versions;
    }

    /** 一个开通项与其目录、约束 */
    @Getter
    @AllArgsConstructor
    public static class CapabilityCard {
        /** 开通项 */
        private final AgreementCapability capability;

        /** 可开通项目录 */
        private final ProductCapability catalog;

        /** 约束，按代码与值排序 */
        private final List<AgreementCapabilityLimit> limits;
    }

    /** 一个版本 */
    @Getter
    @AllArgsConstructor
    public static class VersionCard {
        /** 方案 */
        private final CommercialTermPlan plan;

        /** 版本 */
        private final CommercialTermVersion version;

        /** CURRENT 或 SCHEDULED */
        private final String timing;

        /** 每个相关开通项的价格块 */
        private final List<CapabilityPrices> capabilities;
    }

    /** 一个开通项在某版本下按币种分块的价格 */
    @Getter
    @AllArgsConstructor
    public static class CapabilityPrices {
        /** 开通项 */
        private final CapabilityCard capability;

        /** 币种块，按币种排序 */
        private final List<CurrencyPrices> currencies;
    }

    /** 一个币种的价格，具体度降序、priorityNo 升序 */
    @Getter
    @AllArgsConstructor
    public static class CurrencyPrices {
        /** 币种 */
        private final String currency;

        /** 价格 */
        private final List<CommercialPriceView> prices;
    }
}
