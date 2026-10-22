package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建渠道技术账号。 */
@Getter
@Setter
public class CreateChannelAccountRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 所属渠道 ID */
    @NotNull
    private Long channelId;

    /** 所属 OE 编号；支付渠道必填，服务渠道必须为空 */
    private String entityNo;

    /** 账号名称 */
    @NotBlank
    private String accountName;

    /** 运营备注，可空 */
    private String remark;
}
