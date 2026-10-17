package com.paytech.risk.decision.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;

import lombok.Data;

/** 支付尝试风控评估的不可变输入快照（t_transaction_risk_assessment）。 */
@Data
public class TransactionRiskAssessment {
    /** 交易评估 ID，返回 Payment 保存。 */
    private long assessmentId;

    /** 正式评估 ID。 */
    private long evaluationId;

    /** 支付 Intent ID。 */
    private long paymentIntentId;

    /** 支付 Attempt ID，业务唯一身份。 */
    private long paymentAttemptId;

    /** Payment 风控 Action ID，重放比较身份。 */
    private long riskActionId;

    /** 订单 ID。 */
    private long orderId;

    /** Client ID。 */
    private long clientId;

    /** Operating Entity 编号。 */
    private String entityNo;

    /** 支付方式代码。 */
    private String paymentMethodCode;

    /** 捕获模式，AUTOMATIC 或 MANUAL。 */
    private String captureMode;

    /** 支付金额，币种主单位。 */
    private BigDecimal amount;

    /** 支付币种。 */
    private String currency;

    /** 行业代码。 */
    private String industryCode;

    /** 四位 MCC。 */
    private String mccCode;

    /** 消费者国家；可空。 */
    private String consumerCountryCode;

    /** 消费者引用；可空。 */
    private String consumerReference;

    /** 消费者邮箱 Token；一期不写。 */
    private String consumerEmailVaultToken;

    /** 消费者手机 Token；一期不写。 */
    private String consumerPhoneVaultToken;

    /**
     * 消费者 IP 原值
     * <li>受限字段，不进入日志、MQ 或稳定输入摘要。</li>
     */
    private String clientIpAddress;

    /** 浏览器 User-Agent；仅记录。 */
    private String userAgent;

    /** 跨交易稳定卡指纹；非卡支付为空。 */
    private String cardFingerprint;

    /** 卡 BIN；非卡支付为空。 */
    private String cardBin;

    /** 认证要求；仅结论为 ALLOW 时非空。 */
    private AuthenticationRequirement authenticationRequirement;

    /** Payment 可重放字段的 SHA-256，用于重放比较。 */
    private String stableInputHash;

    /** 全部冻结输入的 SHA-256。 */
    private String restrictedInputHash;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
