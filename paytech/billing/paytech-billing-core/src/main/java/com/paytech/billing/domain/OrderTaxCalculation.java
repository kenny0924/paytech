package com.paytech.billing.domain;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.billing.core.enums.OrderTaxStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * 订单支付时生效的 tax 计算（OrderPaymentSucceeded schema 1 的可空新增字段，技术方案 11.1）
 * <li>载荷缺 taxCalculationId 时整组为空，表示存量订单，按旧行为不生成税行。</li>
 * <li>taxAmount 为订单税额合计；READY 时必须等于 Σ 组件税额，由登记前的事件内校验保证。</li>
 */
@Getter
@Builder
public class OrderTaxCalculation {
    /** tax 计算 ID；写入 TAX_CALCULATION 引用与 SELLER_TAX 行的 source_rule_version_id */
    private final long taxCalculationId;

    /** 计算状态 */
    private final OrderTaxStatus taxStatus;

    /** 计税范围键（本期 SALE），只进入计算证据 */
    private final String taxScopeKey;

    /**
     * 订单税额合计
     * <li>READY 时必填；NOT_MANAGED 为 Client 自报值，billing 不使用。</li>
     */
    private final BigDecimal taxAmount;

    /** 按辖区与注册汇总的组件；缺失时为空列表 */
    private final List<OrderTaxComponent> components;

    /** READY 计算才产生 SELLER_TAX 行。 */
    public boolean collectsSellerTax() {
        return taxStatus == OrderTaxStatus.READY;
    }

    /** Σ 组件税额。 */
    public BigDecimal componentTotal() {
        return components.stream().map(OrderTaxComponent::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
