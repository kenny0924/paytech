package com.paytech.platform.support.api.notificationmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 修改发送账号的区域、发件地址与 Configuration Set；编码与类型不可改。 */
@Getter
@Setter
public class UpdateNotificationProviderRequestBody {

    /**
     * 服务商区域
     * <li>如 me-central-1。</li>
     */
    @NotBlank
    @Pattern(regexp = "[a-z]{2}(-[a-z]+)+-[0-9]{1,2}", message = "区域格式如 me-central-1")
    @Size(max = 32)
    private String region;

    /**
     * 发件地址
     * <li>纯邮箱地址，按平台邮箱规则规范化；须为该区域已验证身份，本接口不在线校验。</li>
     */
    @NotBlank
    @Size(max = 254)
    private String fromAddress;

    /**
     * Configuration Set
     * <li>可空，空白视为未配置。</li>
     */
    @Pattern(regexp = "|[A-Za-z0-9_-]{1,64}", message = "Configuration Set 只能包含字母、数字、下划线或连字符，最多 64 字符")
    private String configurationSet;

    /**
     * 接收投递事件的 SQS 队列 URL
     * <li>可空，null 或空串视为未配置（不接收事件）；形如 https://sqs.{区域}.amazonaws.com/{12 位账号}/{队列名}，最多 512 字符。</li>
     */
    @Size(max = 512)
    @Pattern(regexp = "|https://sqs\\.[a-z]{2}(-[a-z]+)+-[0-9]\\.amazonaws\\.com/[0-9]{12}/[A-Za-z0-9_-]{1,80}",
        message = "事件队列 URL 须为 https://sqs.{区域}.amazonaws.com/{账号}/{队列名}")
    private String eventQueueUrl;

    /**
     * 调用方看到的当前版本
     * <li>不一致返回 409。</li>
     */
    @Positive
    private int expectedVersion;

    /**
     * 操作原因
     * <li>去除首尾空白后 1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
