package com.paytech.payment.routing.domain;

import java.math.BigDecimal;

import com.paytech.foundation.money.Money;
import lombok.Getter;
import lombok.Setter;

/** 支付路由上下文 */
@Getter
@Setter
public class RouteContext {
    /** Client ID */
    private Long clientId;

    /**
     * 经营主体编号
     * <li>来自 Intent，仅在该主体下选路。</li>
     */
    private String entityNo;

    /** 支付金额 */
    private Money amount;

    /** 支付方式代码 */
    private String paymentMethodCode;

    /**
     * 捕获模式
     * <li>AUTOMATIC：自动收款；MANUAL：授权后等待显式捕获。</li>
     */
    private String captureMode;
}
