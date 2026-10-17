package com.paytech.risk.api.decision.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 实时累计变量的 Feature 参数；创建后不可修改。 */
public class VelocityFeatureParameters {

    /**
     * 计入的事件
     * <li>支付尝试：PAYMENT_ATTEMPT_ASSESSED（成功完成的正式评估）、PAYMENT_ATTEMPT_REJECTED（结论为审核或拒绝）；结账：CHECKOUT_ASSESSED、CHECKOUT_REJECTED，含义相同。</li>
     * <li>事件须属于变量所属风控点，由服务端校验。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(PAYMENT_ATTEMPT_ASSESSED|PAYMENT_ATTEMPT_REJECTED|CHECKOUT_ASSESSED|CHECKOUT_REJECTED)$")
    private String eventCode;

    /**
     * 锚定主体
     * <li>CLIENT、OPERATING_ENTITY、CONSUMER、IP_ADDRESS 或 CARD_FINGERPRINT；取本次评估中该主体的值。</li>
     */
    @NotBlank
    private String anchorSubjectType;

    /**
     * 累计算法
     * <li>COUNT、SUM_AMOUNT、DISTINCT_COUNT、LAST_AMOUNT 或 SECONDS_SINCE_LAST。</li>
     * <li>SUM_AMOUNT 只累加与本次同币种的事件，不做汇率换算。</li>
     */
    @NotBlank
    private String metric;

    /**
     * 去重主体
     * <li>仅 DISTINCT_COUNT 必填，且不能与锚定主体相同；其他算法为空。</li>
     */
    private String distinctSubjectType;

    /**
     * 窗口秒数
     * <li>600、3600、86400 或 604800；窗口为 [评估时间 − 窗口, 评估时间)。</li>
     */
    @NotNull
    private Integer windowSeconds;

    /**
     * 是否计入本次
     * <li>仅 COUNT 与 SUM_AMOUNT 可为 true，缺省 false。</li>
     */
    private boolean includeCurrent;

    /** 读取计入的事件。 */
    public String getEventCode() { return eventCode; }

    /** 设置计入的事件。 */
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }

    /** 读取锚定主体。 */
    public String getAnchorSubjectType() { return anchorSubjectType; }

    /** 设置锚定主体。 */
    public void setAnchorSubjectType(String anchorSubjectType) { this.anchorSubjectType = anchorSubjectType; }

    /** 读取累计算法。 */
    public String getMetric() { return metric; }

    /** 设置累计算法。 */
    public void setMetric(String metric) { this.metric = metric; }

    /** 读取去重主体。 */
    public String getDistinctSubjectType() { return distinctSubjectType; }

    /** 设置去重主体。 */
    public void setDistinctSubjectType(String distinctSubjectType) { this.distinctSubjectType = distinctSubjectType; }

    /** 读取窗口秒数。 */
    public Integer getWindowSeconds() { return windowSeconds; }

    /** 设置窗口秒数。 */
    public void setWindowSeconds(Integer windowSeconds) { this.windowSeconds = windowSeconds; }

    /** 读取是否计入本次。 */
    public boolean getIncludeCurrent() { return includeCurrent; }

    /** 设置是否计入本次。 */
    public void setIncludeCurrent(boolean includeCurrent) { this.includeCurrent = includeCurrent; }
}
