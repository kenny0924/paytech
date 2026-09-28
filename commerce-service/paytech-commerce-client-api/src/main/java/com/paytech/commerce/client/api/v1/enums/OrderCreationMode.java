package com.paytech.commerce.client.api.v1.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 订单创建时选用的接入方式；创建后固定，不随支付尝试或页面入口变化。 */
@Getter
@AllArgsConstructor
public enum OrderCreationMode implements CodeEnum {
    /** 通过平台托管收银台创建。 */
    HOSTED_CHECKOUT("HOSTED_CHECKOUT"),

    /** 通过 Client 直接支付集成创建；对应创建入口尚未开放。 */
    DIRECT_PAY("DIRECT_PAY");

    /** 持久化及对外传输的稳定代码。 */
    private final String code;
}
