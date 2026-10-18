package com.paytech.card.service.processor.request;

import com.paytech.card.domain.CardInstrumentContext;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建卡交易工具命令：调用方与卡工具上下文；含卡原值，不记录日志 */
@Getter
@AllArgsConstructor
public class CreateCardInstrumentRequest {
    /** 已解析的调用方服务名，只接受 payment-service */
    private final String caller;

    /** 卡工具上下文 */
    @NotNull(message = "卡工具上下文必填")
    private final CardInstrumentContext context;

    @Override
    public String toString() {
        return "CreateCardInstrumentRequest[REDACTED]";
    }
}
