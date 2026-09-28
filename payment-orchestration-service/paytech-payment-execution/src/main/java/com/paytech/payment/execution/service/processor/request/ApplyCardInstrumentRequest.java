package com.paytech.payment.execution.service.processor.request;

import com.paytech.payment.card.domain.CardInstrumentContext.CardInput;

import lombok.Getter;
import lombok.Setter;

/** 卡工具专用申请请求，卡输入仅交给 Card 处理 */
@Getter
@Setter
public class ApplyCardInstrumentRequest {
    /** Client ID */
    private Long clientId;

    /** 支付意图 ID */
    private Long intentId;

    /** 来源提交 ID */
    private Long sourceSubmissionId;

    /** 精确支付方式代码 */
    private String paymentMethodCode;

    /** Card 拥有的临时卡输入，不进入 Execution Context 或仓储 */
    private CardInput card;

    @Override
    public String toString() {
        return "ApplyCardInstrumentRequest[REDACTED]";
    }
}
