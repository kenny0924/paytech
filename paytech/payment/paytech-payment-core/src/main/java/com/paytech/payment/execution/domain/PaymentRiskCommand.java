package com.paytech.payment.execution.domain;

import java.math.BigDecimal;

import com.paytech.payment.execution.core.enums.CaptureMode;

import lombok.Getter;
import lombok.Setter;

/**
 * 支付风控评估输入（Risk 风控点 PAYMENT_ATTEMPT_RISK 输入合同 v1）
 * <li>以支付尝试为评估身份；同一支付尝试的重试必须使用同一对象，风控 Action 与其他稳定输入不得变化。</li>
 * <li>消费者引用、IP、User-Agent、卡指纹为受限值，只在本次请求内存中传递，不持久化、不记录日志。</li>
 */
@Getter
@Setter
public class PaymentRiskCommand {
    /** 支付尝试 ID，作为评估业务身份 */
    private Long paymentAttemptId;

    /** 支付意图 ID */
    private Long paymentIntentId;

    /** 本次 PAY_RISK 动作 ID */
    private Long riskActionId;

    /** Intent 冻结的 Commerce 订单 ID */
    private Long orderId;

    /** Client ID */
    private Long clientId;

    /** 经营主体编号 */
    private String entityNo;

    /** 支付方式代码 */
    private String paymentMethodCode;

    /** 捕获模式 */
    private CaptureMode captureMode;

    /** 交易金额（主单位） */
    private BigDecimal amount;

    /** 交易币种 */
    private String currency;

    /** 冻结的行业代码 */
    private String industryCode;

    /** 冻结的 MCC */
    private String mccCode;

    /**
     * 消费者国家
     * <li>可空。</li>
     */
    private String consumerCountryCode;

    /**
     * 消费者（玩家）引用
     * <li>受限值；可空。</li>
     */
    private String consumerReference;

    /**
     * 消费者 IP
     * <li>受限值；可空。</li>
     */
    private String clientIpAddress;

    /**
     * 消费者 User-Agent
     * <li>受限值；可空。</li>
     */
    private String userAgent;

    /**
     * 卡指纹
     * <li>受限值；非卡支付或 Card 无法提供时为空。</li>
     */
    private String cardFingerprint;

    /**
     * 卡 BIN
     * <li>非卡支付或 Card 无法提供时为空。</li>
     */
    private String cardBin;

    @Override
    public String toString() {
        return "PaymentRiskCommand[paymentAttemptId=" + paymentAttemptId + ", riskActionId=" + riskActionId + "]";
    }
}
