package com.paytech.commercial.api.ratecard.response;

import java.util.List;

import com.paytech.commercial.api.price.response.CommercialPriceComponentResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceConditionResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceSettlementResponseBody;

import lombok.Getter;
import lombok.Setter;

/**
 * Client 费率卡结构化数据（technique.md §2.7）：按 Client 的 ACTIVE 合同返回订阅产品、开通项与当前 / 已排期条款版本的价格
 * <li>价格按开通项 × 币种分块，块内画像按具体度（不同维度数）降序、priorityNo 升序；组件按计费事件分组。</li>
 * <li>不返回渲染文案，不返回分润（PARTNER_SHARE、PARTNER_BUY_RATE）与平台服务费（PLATFORM_SERVICE_CHARGE）。</li>
 */
@Getter
@Setter
public class RateCardResponseBody {
    /** Client ID */
    private String clientId;

    /** 费率卡时点（UTC RFC 3339） */
    private String asOf;

    /** 币种筛选，未筛选为空 */
    private String currency;

    /** ACTIVE 且在时点生效的 CLIENT 合同，按合同 ID 升序 */
    private List<AgreementRateCard> agreements;

    /** 一份合同的费率卡 */
    @Getter
    @Setter
    public static class AgreementRateCard {
        /** 合同 ID */
        private String agreementId;

        /** 合同编号 */
        private String agreementNo;

        /** 合同 OE */
        private String entityNo;

        /** 业务模式 */
        private String businessMode;

        /** 资金模式 */
        private String fundMode;

        /** 定价权 */
        private String pricingAuthority;

        /** 合同生效起点 */
        private String effectiveFrom;

        /** 合同生效终点 */
        private String effectiveTo;

        /** 订阅产品代码，按代码排序 */
        private List<String> products;

        /** 开通项（含约束与保证金），按开通项 ID 升序 */
        private List<CapabilityRateCard> capabilities;

        /** 当前版本与已排期版本的价格，按方案、生效起点排序 */
        private List<TermVersionRateCard> termVersions;
    }

    /** 一个开通项 */
    @Getter
    @Setter
    public static class CapabilityRateCard {
        /** 开通项 ID */
        private String agreementCapabilityId;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 所属产品 */
        private String productCode;

        /** 可开通项种类 */
        private String capabilityKind;

        /** 支付方式 */
        private String paymentMethodCode;

        /** 细分维度 */
        private String dimensionCode;

        /** 细分维度值 */
        private String dimensionValue;

        /** 准确 OE */
        private String entityNo;

        /** 开通状态 */
        private String status;

        /** 开通生效起点 */
        private String effectiveFrom;

        /** 开通生效终点 */
        private String effectiveTo;

        /** 滚动保证金比例 */
        private String rollingReserveRate;

        /** 滚动保证金天数 */
        private Integer rollingReserveDays;

        /** 接受条件行（limitCode 为维度代码），按代码与值排序 */
        private List<CapabilityLimit> limits;

        /** 是否要求 3DS 认证 */
        private Boolean require3ds;
    }

    /** 开通项约束 */
    @Getter
    @Setter
    public static class CapabilityLimit {
        /** 约束代码 */
        private String limitCode;

        /** 约束值 */
        private String limitValue;
    }

    /** 一个条款版本的价格 */
    @Getter
    @Setter
    public static class TermVersionRateCard {
        /** 方案 ID */
        private String planId;

        /** 条款类型 */
        private String termType;

        /** 版本 ID */
        private String versionId;

        /** 版本号 */
        private String versionNo;

        /** 版本状态 */
        private String status;

        /** CURRENT：时点适用版本；SCHEDULED：时点之后生效的已排期版本 */
        private String timing;

        /** 生效起点 */
        private String effectiveFrom;

        /** 生效终点 */
        private String effectiveTo;

        /** 定价模型 */
        private String pricingModel;

        /** 费用含税口径 */
        private String feeTaxTreatment;

        /** 每个开通项下按币种分块的价格 */
        private List<CapabilityPrices> capabilities;
    }

    /** 一个开通项在某版本下的价格 */
    @Getter
    @Setter
    public static class CapabilityPrices {
        /** 开通项 ID */
        private String agreementCapabilityId;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 按币种分块，按币种排序 */
        private List<CurrencyPrices> currencies;
    }

    /** 一个币种的价格块 */
    @Getter
    @Setter
    public static class CurrencyPrices {
        /** 币种 */
        private String currency;

        /** 适用画像，具体度降序、priorityNo 升序 */
        private List<ProfilePrice> profiles;
    }

    /** 一个画像的价格 */
    @Getter
    @Setter
    public static class ProfilePrice {
        /** 价格 ID */
        private String priceId;

        /** 画像代码 */
        private String pricingProfileCode;

        /** 画像名称 */
        private String profileName;

        /** 具体度：不同条件维度数 */
        private int specificity;

        /** 优先级 */
        private Integer priorityNo;

        /** 画像条件 */
        private List<CommercialPriceConditionResponseBody> conditions;

        /** 结算四字段 */
        private CommercialPriceSettlementResponseBody settlement;

        /** 按计费事件分组的组件，按事件代码排序 */
        private List<FeeEventComponents> feeEvents;
    }

    /** 一个计费事件下的组件 */
    @Getter
    @Setter
    public static class FeeEventComponents {
        /** 计费事件代码 */
        private String feeEventCode;

        /** 组件，按 componentNo 排序 */
        private List<CommercialPriceComponentResponseBody> components;
    }
}
