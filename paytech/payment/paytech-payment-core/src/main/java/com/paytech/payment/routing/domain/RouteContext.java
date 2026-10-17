package com.paytech.payment.routing.domain;

import com.paytech.foundation.money.Money;
import com.paytech.payment.execution.core.enums.CaptureMode;
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
    private CaptureMode captureMode;

    /**
     * 消费者国家
     * <li>ISO 3166-1 alpha-2，来自 Intent；参与 MID 能力匹配。</li>
     */
    private String countryCode;

    /**
     * 商户类别码
     * <li>4 位数字，来自 Intent；参与 MID 能力匹配。</li>
     */
    private String mccCode;
}
