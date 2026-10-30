package com.paytech.acquiring.service.processor.customer.response;

import java.math.BigDecimal;

import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成保存的结果：保存状态与重算后的当前报价，不返回数据库对象或消费者原值。 */
@Getter
@AllArgsConstructor
public class SaveConsumerResponse {
    /** 原订单公开编号。 */
    private final String orderNo;

    /** 保存结果状态。 */
    private final String consumerStatus;

    /** 当前报价的金额状态。 */
    private final OrderAmountStatus amountStatus;

    /** BLOCKED 的原因代码，其他状态为空。 */
    private final String taxBlockReason;

    /** 订单币种。 */
    private final String currency;

    /** 优惠前商品总额。 */
    private final BigDecimal merchandiseAmount;

    /** 优惠总额。 */
    private final BigDecimal discountAmount;

    /** 税前净额。 */
    private final BigDecimal netAmount;

    /** 当前报价税额；BLOCKED 时为空。 */
    private final BigDecimal taxAmount;

    /** 当前报价舍入差额；BLOCKED 时为空。 */
    private final BigDecimal roundingAmount;

    /** 当前报价应付总额；BLOCKED 时为空。 */
    private final BigDecimal totalAmount;
}
