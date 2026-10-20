package com.paytech.channel.connector.service.processor.request;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 写入渠道技术账号参数与凭据；内容只交给 Vault Protect，不得记录。 */
@Getter
@Setter
public class ConfigureChannelAccountRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 技术账号 ID */
    @NotNull
    private Long channelAccountId;

    /** 账号参数 */
    @NotNull
    private Map<String, String> parameters;

    /** 凭据 */
    @NotNull
    private Map<String, String> credentials;

    /** 不输出参数与凭据。 */
    @Override
    public String toString() {
        return "ConfigureChannelAccountRequest[redacted]";
    }
}
