package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 人工添加名单成员的请求。 */
public class AddRiskListEntryRequestBody {

    /**
     * 主体原值
     * <li>CONSUMER 为 clientId:consumerReference；IP_ADDRESS 为 IPv4 或 IPv6 字面量，IPv6 按 /64 网段登记；CARD_BIN 为 6 至 8 位数字。</li>
     * <li>受限字段，响应与日志只出现脱敏值。</li>
     */
    @NotBlank
    @Size(max = 256)
    private String subjectValue;

    /**
     * 生效起点
     * <li>UTC ISO-8601 时间，含；缺省为当前时间。</li>
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z$")
    private String effectiveFrom;

    /**
     * 生效终点
     * <li>UTC ISO-8601 时间，不含，必须晚于起点；缺省为长期有效。</li>
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z$")
    private String effectiveTo;

    /** 加入原因，最长 500 字符。 */
    @NotBlank
    @Size(max = 500)
    private String reason;

    /** 读取主体原值。 */
    public String getSubjectValue() { return subjectValue; }

    /** 设置主体原值。 */
    public void setSubjectValue(String subjectValue) { this.subjectValue = subjectValue; }

    /** 读取生效起点。 */
    public String getEffectiveFrom() { return effectiveFrom; }

    /** 设置生效起点。 */
    public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    /** 读取生效终点。 */
    public String getEffectiveTo() { return effectiveTo; }

    /** 设置生效终点。 */
    public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }

    /** 读取加入原因。 */
    public String getReason() { return reason; }

    /** 设置加入原因。 */
    public void setReason(String reason) { this.reason = reason; }
}
