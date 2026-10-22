package com.paytech.channel.connector.protocol.service.processor.request;

import java.util.List;

import com.paytech.channel.connector.protocol.domain.EndpointDefinition;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 整组替换渠道出站地址。 */
@Getter
@Setter
public class UpdateChannelEndpointsRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 渠道 ID */
    @NotNull
    private Long channelId;

    /** 调用方读取的渠道版本 */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /** 新出站地址，保存顺序；数量、唯一性与地址规则由出站地址校验器检查 */
    @NotNull
    private List<EndpointDefinition> endpoints;
}
