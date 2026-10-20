package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 修改渠道技术账号的名称与备注。 */
@Getter
@Setter
public class UpdateChannelAccountRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 技术账号 ID */
    @NotNull
    private Long channelAccountId;

    /** 账号名称 */
    @NotBlank
    private String accountName;

    /** 运营备注，可空 */
    private String remark;
}
