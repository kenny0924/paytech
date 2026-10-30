package com.paytech.tax.domain.registration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * acquiring OrderPaymentSucceeded 中 tax 登记所需的事实（technique 11.1 字段）
 * <li>由 {@code OrderPaymentSucceededEventReader} 从已校验的信封读取；taxCalculationId 为空表示存量订单。</li>
 */
@Getter
@AllArgsConstructor
public class OrderPaymentTaxFact {
    /** 信封 eventId */
    @NotBlank
    private final String eventId;

    /** 来源 Payload 的 RFC 8785 SHA-256 小写十六进制 */
    @NotBlank
    private final String payloadHash;

    /** acquiring 订单 ID */
    @Positive
    private final long orderId;

    /** 订单币种 */
    @NotBlank
    private final String currency;

    /** 支付成功时间（经济事件发生时间） */
    @NotNull
    private final Instant paymentSucceededTime;

    /** 支付所依据的 tax 计算；存量订单为空 */
    private final Long taxCalculationId;

    /** 计算状态 Code（TaxCalculationStatus）；存量订单为空 */
    private final String taxStatus;

    /** 税务范围键；存量订单为空 */
    private final String taxScopeKey;

    /** 税额合计；可空 */
    private final BigDecimal taxAmount;

    /** 按辖区 + 注册汇总的税额组件；NOT_MANAGED 与存量订单为空列表 */
    @NotNull
    private final List<Component> taxComponents;

    /**
     * 事件组件税额合计
     *
     * @return Σ taxComponents.taxAmount
     */
    public BigDecimal componentTotal() {
        return taxComponents.stream().map(Component::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 事件中的一个汇总税额组件。 */
    @Getter
    @AllArgsConstructor
    public static class Component {
        /** 征税辖区 */
        private final String jurisdictionCode;

        /** 注册 ID；可空 */
        private final Long registrationId;

        /** 纳税主体 Party ID */
        private final long taxSubjectPartyId;

        /** 税额 */
        private final BigDecimal taxAmount;
    }
}
