package com.paytech.risk.api.evaluation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 支付尝试风控评估请求；不含卡号、CVV 或个人信息原文。 */
public class PaymentRiskAssessmentRequestBody {

    /**
     * 支付尝试 ID
     * <li>正整数十进制字符串；评估的业务唯一身份，同一支付尝试只评估一次。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String paymentAttemptId;

    /** 支付 Intent ID，正整数十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String paymentIntentId;

    /**
     * Payment 风控 Action ID
     * <li>正整数十进制字符串；重放必须使用原值，不同值视为冲突。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String riskActionId;

    /** 订单 ID，正整数十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String orderId;

    /** Client ID，正整数十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String clientId;

    /** Operating Entity 编号，5 位大写字母或数字。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{5}$")
    private String entityNo;

    /** 精确支付方式代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String paymentMethodCode;

    /** 捕获模式，AUTOMATIC 或 MANUAL。 */
    @NotBlank
    @Pattern(regexp = "^(AUTOMATIC|MANUAL)$")
    private String captureMode;

    /**
     * 支付金额
     * <li>币种主单位十进制字符串，正数，最多 8 位小数；不接受科学计数法。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(?!0+(\\.0+)?$)(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?$")
    private String amount;

    /** ISO 4217 币种。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    /** 行业代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String industryCode;

    /** 四位 MCC，保留前导零。 */
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$")
    private String mccCode;

    /** 消费者国家，ISO 3166-1 alpha-2；可空。 */
    @Pattern(regexp = "^[A-Z]{2}$")
    private String consumerCountryCode;

    /**
     * 消费者引用
     * <li>Client 命名空间内的不透明非 PII 引用；可空。</li>
     */
    @Pattern(regexp = "^[\\x21-\\x7E]{1,128}$")
    private String consumerReference;

    /**
     * 消费者 IP
     * <li>IPv4 或 IPv6 字面量；可空。受限字段，只进入 Risk 评估快照。</li>
     */
    @Size(max = 45)
    private String clientIpAddress;

    /** 浏览器 User-Agent；可空，只记录。 */
    @Size(max = 512)
    private String userAgent;

    /**
     * 卡指纹
     * <li>Payment Card 提供的跨交易稳定卡指纹；非卡支付为空。</li>
     */
    @Pattern(regexp = "^[A-Za-z0-9_\\-+/=]{8,128}$")
    private String cardFingerprint;

    /** 卡号前 6 至 8 位；非卡支付为空。 */
    @Pattern(regexp = "^[0-9]{6,8}$")
    private String cardBin;

    /** 读取支付尝试 ID。 */
    public String getPaymentAttemptId() { return paymentAttemptId; }

    /** 设置支付尝试 ID。 */
    public void setPaymentAttemptId(String paymentAttemptId) { this.paymentAttemptId = paymentAttemptId; }

    /** 读取支付 Intent ID。 */
    public String getPaymentIntentId() { return paymentIntentId; }

    /** 设置支付 Intent ID。 */
    public void setPaymentIntentId(String paymentIntentId) { this.paymentIntentId = paymentIntentId; }

    /** 读取Payment 风控 Action ID。 */
    public String getRiskActionId() { return riskActionId; }

    /** 设置Payment 风控 Action ID。 */
    public void setRiskActionId(String riskActionId) { this.riskActionId = riskActionId; }

    /** 读取订单 ID。 */
    public String getOrderId() { return orderId; }

    /** 设置订单 ID。 */
    public void setOrderId(String orderId) { this.orderId = orderId; }

    /** 读取Client ID。 */
    public String getClientId() { return clientId; }

    /** 设置Client ID。 */
    public void setClientId(String clientId) { this.clientId = clientId; }

    /** 读取Operating Entity 编号。 */
    public String getEntityNo() { return entityNo; }

    /** 设置Operating Entity 编号。 */
    public void setEntityNo(String entityNo) { this.entityNo = entityNo; }

    /** 读取支付方式代码。 */
    public String getPaymentMethodCode() { return paymentMethodCode; }

    /** 设置支付方式代码。 */
    public void setPaymentMethodCode(String paymentMethodCode) { this.paymentMethodCode = paymentMethodCode; }

    /** 读取捕获模式。 */
    public String getCaptureMode() { return captureMode; }

    /** 设置捕获模式。 */
    public void setCaptureMode(String captureMode) { this.captureMode = captureMode; }

    /** 读取支付金额。 */
    public String getAmount() { return amount; }

    /** 设置支付金额。 */
    public void setAmount(String amount) { this.amount = amount; }

    /** 读取币种。 */
    public String getCurrency() { return currency; }

    /** 设置币种。 */
    public void setCurrency(String currency) { this.currency = currency; }

    /** 读取行业代码。 */
    public String getIndustryCode() { return industryCode; }

    /** 设置行业代码。 */
    public void setIndustryCode(String industryCode) { this.industryCode = industryCode; }

    /** 读取MCC。 */
    public String getMccCode() { return mccCode; }

    /** 设置MCC。 */
    public void setMccCode(String mccCode) { this.mccCode = mccCode; }

    /** 读取消费者国家。 */
    public String getConsumerCountryCode() { return consumerCountryCode; }

    /** 设置消费者国家。 */
    public void setConsumerCountryCode(String consumerCountryCode) { this.consumerCountryCode = consumerCountryCode; }

    /** 读取消费者引用。 */
    public String getConsumerReference() { return consumerReference; }

    /** 设置消费者引用。 */
    public void setConsumerReference(String consumerReference) { this.consumerReference = consumerReference; }

    /** 读取消费者 IP。 */
    public String getClientIpAddress() { return clientIpAddress; }

    /** 设置消费者 IP。 */
    public void setClientIpAddress(String clientIpAddress) { this.clientIpAddress = clientIpAddress; }

    /** 读取User-Agent。 */
    public String getUserAgent() { return userAgent; }

    /** 设置User-Agent。 */
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    /** 读取卡指纹。 */
    public String getCardFingerprint() { return cardFingerprint; }

    /** 设置卡指纹。 */
    public void setCardFingerprint(String cardFingerprint) { this.cardFingerprint = cardFingerprint; }

    /** 读取卡 BIN。 */
    public String getCardBin() { return cardBin; }

    /** 设置卡 BIN。 */
    public void setCardBin(String cardBin) { this.cardBin = cardBin; }
}
