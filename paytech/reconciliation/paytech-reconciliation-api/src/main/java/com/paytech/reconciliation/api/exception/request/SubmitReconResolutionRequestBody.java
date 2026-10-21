package com.paytech.reconciliation.api.exception.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交异常处置请求
 * <li>RE_MATCH：rematchTargetType + rematchTargetId 必填；银行层另须 rematchAmount；classificationCode、amount 必须为空。</li>
 * <li>ACCEPT_TIMING_DIFFERENCE：只填 note，其余为空。</li>
 * <li>CLASSIFY：classificationCode + amount 必填，amount 必须等于异常差异金额；重新匹配字段必须为空。</li>
 * <li>字段组合与可用范围在服务内按异常来源对象校验（见 {@code ReconClassification}）。</li>
 */
@Getter
@Setter
public class SubmitReconResolutionRequestBody {
    /**
     * 处置方式
     * <li>RE_MATCH、ACCEPT_TIMING_DIFFERENCE 或 CLASSIFY。</li>
     */
    @NotBlank
    @Pattern(regexp = "RE_MATCH|ACCEPT_TIMING_DIFFERENCE|CLASSIFY", message = "只能为 RE_MATCH、ACCEPT_TIMING_DIFFERENCE 或 CLASSIFY")
    private String resolutionType;

    /**
     * 归类码
     * <li>仅 CLASSIFY；必须在异常来源对象允许的范围内。</li>
     */
    @Pattern(regexp = "[A-Z][A-Z_]{0,31}", message = "必须是大写字母或下划线，最长 32 位")
    private String classificationCode;

    /**
     * 归类金额
     * <li>仅 CLASSIFY；带符号主货币单位十进制字符串，必须等于异常差异金额。</li>
     */
    @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的带符号普通十进制字符串")
    private String amount;

    /**
     * 重新匹配目标类型
     * <li>仅 RE_MATCH：交易层为 PLATFORM_TXN，银行层为 EXPECTED_BANK_MOVEMENT。</li>
     */
    @Pattern(regexp = "PLATFORM_TXN|EXPECTED_BANK_MOVEMENT", message = "只能为 PLATFORM_TXN 或 EXPECTED_BANK_MOVEMENT")
    private String rematchTargetType;

    /**
     * 重新匹配目标主键
     * <li>仅 RE_MATCH：交易层目标须同清算账户、方向一致、金额不超剩余；银行层须同银行账户。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String rematchTargetId;

    /**
     * 银行层重新匹配的分配金额
     * <li>仅银行层 RE_MATCH；正数主货币单位十进制字符串，校验同人工多选匹配。</li>
     */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
    private String rematchAmount;

    /**
     * 处置说明
     * <li>必填，最长 1000。</li>
     */
    @NotBlank
    @Size(max = 1000)
    private String note;
}
