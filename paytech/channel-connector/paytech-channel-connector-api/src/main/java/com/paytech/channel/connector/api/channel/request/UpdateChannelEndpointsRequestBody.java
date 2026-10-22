package com.paytech.channel.connector.api.channel.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 整组替换渠道出站地址请求。 */
@Getter
@Setter
public class UpdateChannelEndpointsRequestBody {
    /**
     * 期望的渠道并发版本
     * <li>取自渠道详情的 version；与当前版本不一致时 409，不写库。</li>
     */
    @NotNull(message = "期望版本必填")
    @Min(value = 1, message = "期望版本必须为正整数")
    private Integer expectedVersion;

    /**
     * 本环境出站地址
     * <li>1–16 项，endpointCode 在渠道内唯一；保存后整组替换原地址。</li>
     */
    @NotNull(message = "出站地址必填")
    @Size(min = 1, max = 16, message = "出站地址为 1–16 项")
    private List<@NotNull(message = "出站地址不能为空") @Valid ChannelEndpointBody> endpoints;
}
