package com.paytech.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * acquiring 订单冻结商业上下文在 billing 内的形态（Owner 为 acquiring，随 OrderPaymentSucceeded 载荷到达，本服务不持久化原样）
 * <li>Partner 五项（partnerAgreementId、partnerAgreementOperatingEntityId、partnerShareVersionId、platformChargeVersionId、
 * platformServicePartyId）只在 Local Partner 来源时非空。</li>
 */
@Getter
@Builder
public class OrderCommercialContext {
    /** 订单冻结上下文主键 */
    private final long orderContextId;

    /** 订单主键 */
    private final long orderId;

    /** 订单所属 Client */
    private final long clientId;

    /** 冻结的 Operating Entity 编号 */
    private final String entityNo;

    /**
     * Local Partner
     * <li>非 PARTNER 来源为空。</li>
     */
    private final Long localPartnerId;

    /**
     * 行业分类代码
     * <li>可空。</li>
     */
    private final String industryCode;

    /**
     * 四位 MCC
     * <li>可空。</li>
     */
    private final String mccCode;

    /** 订单总金额，币种主单位 */
    private final BigDecimal orderAmount;

    /** 订单币种 */
    private final String currency;

    /** 冻结的商业时点（UTC），价格命中的 contextAsOfTime */
    private final Instant commercialContextTime;

    /** 冻结的 Client / OE 合同 */
    private final long clientAgreementId;

    /** 冻结的 Client 定价条款版本 */
    private final long clientPricingVersionId;

    /** Partner 合同 */
    private final Long partnerAgreementId;

    /** Partner 合同与订单 OE 的关联 */
    private final Long partnerAgreementOperatingEntityId;

    /** Partner 分润条款版本 */
    private final Long partnerShareVersionId;

    /** 平台服务费条款版本 */
    private final Long platformChargeVersionId;

    /** Partner 合同的平台签约法律 Party，映射 Finance Entity */
    private final Long platformServicePartyId;

    /**
     * Finance 税务快照（旧字段，只读兼容）
     * <li>存量订单有值、新订单为空；只在没有 tax 计算时记 TAX_SNAPSHOT 引用，不生成税行。</li>
     */
    private final Long financeTaxSnapshotId;

    /**
     * 订单支付时生效的 tax 计算
     * <li>载荷缺 taxCalculationId 时为空（存量订单），与旧行为相同。</li>
     */
    private final OrderTaxCalculation taxCalculation;

    /**
     * 消费者国家 ISO 3166-1 alpha-2
     * <li>载荷可空；登记 PAYMENT 事实时必填（渠道成本命中维度、定价输入 NOT NULL 列）。</li>
     */
    private final String consumerCountryCode;

    /** 订单来自 Local Partner 提供的 Operating Entity。 */
    public boolean hasLocalPartner() {
        return localPartnerId != null;
    }

    /** Local Partner 来源的分润与服务费命中所需五项齐全。 */
    public boolean partnerTermsComplete() {
        return partnerAgreementId != null && partnerAgreementOperatingEntityId != null && partnerShareVersionId != null
            && platformChargeVersionId != null && platformServicePartyId != null;
    }
}
