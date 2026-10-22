package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

import tools.jackson.databind.JsonNode;

/** 经通用执行入口执行服务渠道的一个标准 API。 */
@Getter
@AllArgsConstructor
public class ExecuteServiceChannelRequest {
    /** 已确认的调用方服务名 */
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9-]{1,63}")
    private final String callerService;

    /** 服务渠道编码 */
    @NotBlank
    private final String channelCode;

    /** 标准 API 编码 */
    @NotBlank
    private final String standardApiCode;

    /** 调用方提供的标准 API 输入；其中的 channelAccount 一律被忽略，账号材料只由本服务注入 */
    @NotNull
    private final JsonNode input;
}
