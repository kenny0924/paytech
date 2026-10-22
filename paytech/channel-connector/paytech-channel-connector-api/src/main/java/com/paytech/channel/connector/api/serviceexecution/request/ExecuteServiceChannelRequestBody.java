package com.paytech.channel.connector.api.serviceexecution.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 执行服务渠道标准 API 的请求；结构沿用原 Platform 标准 API 执行合同，integrationCode 改为 channelCode。 */
@Getter
@Setter
public class ExecuteServiceChannelRequestBody {
    /** 服务渠道编码，由调用方显式选择 */
    @NotBlank(message = "渠道编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private String channelCode;

    /** 标准 API 编码，例如 GEOCODE */
    @NotBlank(message = "标准 API 编码必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private String standardApiCode;

    /**
     * 标准 API 输入
     * <li>按标准 API 请求字段校验，未声明的字段拒绝；金额与精确小数用十进制字符串。</li>
     * <li>不得包含密钥或凭据明文；凭据由渠道的技术账号在本服务内还原。</li>
     */
    @NotNull(message = "标准 API 输入必填")
    private JsonNode input;
}
