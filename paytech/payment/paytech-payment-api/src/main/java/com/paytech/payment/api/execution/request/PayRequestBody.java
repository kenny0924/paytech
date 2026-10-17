package com.paytech.payment.api.execution.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 支付受理请求 */
@Getter
@Setter
public class PayRequestBody {
    /**
     * Client ID
     * <li>正十进制 long 字符串，无前导零；必须与 Intent 归属一致。</li>
     */
    @NotBlank(message = "支付标识必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "支付标识必须为无前导零的正整数字符串")
    @DecimalMax(value = "9223372036854775807", message = "支付标识超出有效范围")
    private String clientId;

    /**
     * 来源提交 ID
     * <li>正十进制 long 字符串，无前导零。</li>
     * <li>重试沿用原值，更换支付工具时使用新的提交 ID。</li>
     */
    @NotBlank(message = "支付标识必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "支付标识必须为无前导零的正整数字符串")
    @DecimalMax(value = "9223372036854775807", message = "支付标识超出有效范围")
    private String sourceSubmissionId;

    /**
     * 捕获模式
     * <li>AUTOMATIC：自动收款；MANUAL：授权后等待显式捕获。</li>
     * <li>必须显式传入，受理后不可修改。</li>
     */
    @NotBlank(message = "捕获模式必填")
    @Pattern(regexp = "MANUAL|AUTOMATIC", message = "captureMode 只能为 MANUAL 或 AUTOMATIC")
    private String captureMode;

    /** 支付方式 */
    @Valid
    @NotNull(message = "支付方式与工具必填")
    private PayMethod payMethod;

    /**
     * 消费者完成渠道交互（如 3DS）后的回跳地址
     * <li>https，最多 2048 字符；只用于本次渠道请求，不保存。</li>
     */
    @NotBlank(message = "回跳地址必填")
    @Size(max = 2048, message = "回跳地址最多 2048 字符")
    @Pattern(regexp = "https://[^\\s]+", message = "回跳地址必须为 https 地址")
    private String returnUrl;

    /**
     * 付款人引用
     * <li>Client 命名空间内不透明、不含个人信息（邮箱、电话、证件号等）的引用；渠道要求付款人标识时使用，不保存。</li>
     */
    @NotBlank(message = "付款人引用必填")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}", message = "付款人引用格式无效")
    private String consumerReference;

    /**
     * 消费者 IP 地址
     * <li>可选；作为收单必需的最小披露传给渠道（3DS/风控），不保存。</li>
     */
    @Size(max = 64, message = "消费者 IP 地址最多 64 字符")
    @Pattern(regexp = "[0-9A-Fa-f.:]+", message = "消费者 IP 地址格式无效")
    private String consumerIpAddress;

    /**
     * 消费者浏览器 User-Agent
     * <li>可选；与 IP 一同作为收单最小披露，不保存。</li>
     */
    @Size(max = 512, message = "User-Agent 最多 512 字符")
    @Pattern(regexp = "[^\\p{Cc}]+", message = "User-Agent 不能包含控制字符")
    private String consumerUserAgent;

    /** 支付方式与工具 */
    @Getter
    @Setter
    public static class PayMethod {

        /**
         * 支付方式代码
         * <li>1–64 位大写字母、数字或下划线，以大写字母开头。</li>
         */
        @NotBlank(message = "支付方式必填")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "支付方式代码无效")
        private String paymentMethodCode;

        /**
         * 支付工具 Token
         * <li>限定本次 Client、Intent 和来源提交；重复提交必须使用原值。</li>
         * <li>1–64 位字母、数字、下划线或连字符。</li>
         */
        @NotBlank(message = "支付工具 Token 必填")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "支付工具 Token 格式无效")
        private String paymentInstrumentToken;

        @Override
        public String toString() {
            return "PayMethod[REDACTED]";
        }
    }

    /** 请求包含工具凭据，禁止默认记录完整内容。 */
    @Override
    public String toString() {
        return "PayRequestBody[REDACTED]";
    }
}
