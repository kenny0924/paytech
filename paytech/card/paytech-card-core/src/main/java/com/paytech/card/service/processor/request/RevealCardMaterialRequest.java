package com.paytech.card.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 凭票据读卡命令；票据原值不记录日志 */
@Getter
@AllArgsConstructor
public class RevealCardMaterialRequest {
    /** 已解析的调用方服务名，只接受 platform-support-service */
    private final String caller;

    /** 一次性票据原值 */
    @NotBlank(message = "读卡票据必填")
    private final String ticket;

    @Override
    public String toString() {
        return "RevealCardMaterialRequest[REDACTED]";
    }
}
