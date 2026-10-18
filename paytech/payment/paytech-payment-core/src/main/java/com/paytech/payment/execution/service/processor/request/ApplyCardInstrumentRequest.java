package com.paytech.payment.execution.service.processor.request;

import com.paytech.payment.execution.domain.CardInput;

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

    /** 临时卡输入，只转交 card-service，不进入 Execution Context 或仓储；用已存卡时只含 CVV */
    private CardInput card;

    /** 用已存卡时的对外已存卡 Token；手输卡为空 */
    private String storedCardToken;

    /** Commerce Customer ID；访客支付为空 */
    private Long customerId;

    /** 消费者是否同意支付成功后保存卡 */
    private boolean saveCard;

    @Override
    public String toString() {
        return "ApplyCardInstrumentRequest[REDACTED]";
    }
}
