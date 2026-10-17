package com.paytech.acquiring.service.processor.customer.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Checkout 查询输入；仅由入站边界组合 Path 与 Query，不保存或打印原 Token。 */
@Getter
@AllArgsConstructor
public class GetCheckoutRequest {
    /** 本次请求携带的公开 Checkout Token。 */
    private final String checkoutToken;

    /** 是否经原 Token 授权回填消费者资料。 */
    private final boolean includeConsumer;
}
