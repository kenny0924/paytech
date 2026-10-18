package com.paytech.card.service.processor.request;

import java.time.Duration;

import com.paytech.card.api.enums.CardMaterialPurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 签发读卡票据命令 */
@Getter
@AllArgsConstructor
public class IssueCardMaterialTicketRequest {
    /** 已解析的调用方服务名，只接受 payment-service */
    private final String caller;

    /** Card 工具引用 */
    @NotBlank(message = "工具引用必填")
    private final String instrumentRef;

    /** 支付尝试 ID */
    @NotNull(message = "支付尝试 ID 必填")
    private final Long attemptId;

    /** 资金动作 ID */
    @NotNull(message = "支付动作 ID 必填")
    private final Long actionId;

    /** 用途 */
    @NotNull(message = "用途必填")
    private final CardMaterialPurpose purpose;

    /** 票据有效时长，不超过 90 秒 */
    @NotNull(message = "票据有效时长必填")
    private final Duration ttl;
}
