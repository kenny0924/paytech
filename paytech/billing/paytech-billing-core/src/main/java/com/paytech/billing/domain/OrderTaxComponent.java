package com.paytech.billing.domain;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

/**
 * OrderPaymentSucceeded taxComponents 的一项：按辖区与注册汇总后的组件税额（Owner 为 tax，经 acquiring 冻结后随事件到达）
 * <li>税额为正的组件生成一行 SELLER_TAX，收方为纳税主体 Party；零税额组件（不征收、零税率）不生成行。</li>
 */
@Getter
@Builder
public class OrderTaxComponent {
    /** 征税辖区代码 */
    private final String jurisdictionCode;

    /**
     * 税种代码（如 VAT）
     * <li>可空：只进入计算证据，billing 不按税种分支。</li>
     */
    private final String taxType;

    /**
     * 法定税务注册 ID
     * <li>可空：不征收的组件没有注册；税额为正时必须给出。</li>
     */
    private final Long registrationId;

    /** 纳税主体 Party，SELLER_TAX 行的 TAX_AUTHORITY 收方标识 */
    private final long taxSubjectPartyId;

    /** 组件税额，币种主单位 */
    private final BigDecimal taxAmount;
}
