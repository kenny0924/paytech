package com.paytech.channel.connector.api.grant.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 新建调用授权请求。 */
@Getter
@Setter
public class CreateChannelCallGrantRequestBody {
    /** 服务渠道 ID，正十进制字符串 */
    @NotBlank(message = "渠道 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "渠道 ID 必须是正十进制整数")
    private String channelId;

    /**
     * 调用方服务名
     * <li>即调用方的 spring.application.name，如 commerce-service；小写字母开头，小写字母、数字或连字符，2–64 位。</li>
     * <li>Owner 只校验格式，不维护服务名册。</li>
     */
    @NotBlank(message = "调用方服务名必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{1,63}", message = "调用方服务名必须是小写字母开头的小写字母、数字或连字符，2–64 位")
    private String callerService;

    /** 被授权调用的标准 API 编码，须已被该渠道实现 */
    @NotBlank(message = "标准 API 编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "标准 API 编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private String standardApiCode;
}
