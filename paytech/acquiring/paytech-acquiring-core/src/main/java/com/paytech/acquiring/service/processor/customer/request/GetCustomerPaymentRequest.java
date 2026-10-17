package com.paytech.acquiring.service.processor.customer.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成协议标识转换的查询输入，授权仍由 Processor 完成。 */
@Getter
@AllArgsConstructor
public class GetCustomerPaymentRequest {
    /** 原公开 Checkout Token，不生成包含属性的 toString。 */
    private final String checkoutToken;

    /** 协议层已解析的支付提交业务编号。 */
    private final long submissionId;
}
