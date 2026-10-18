package com.paytech.payment.execution.domain;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 交给 card-service 创建卡交易工具的命令；含卡原值，不记录日志 */
@Getter
@Setter
public class CardInstrumentCommand {
    /** Client ID */
    private Long clientId;

    /** Commerce Customer ID；访客支付为空 */
    private Long customerId;

    /** 来源提交 ID：card 的幂等键 */
    private Long sourceSubmissionId;

    /** 手输卡或已存卡的本次输入 */
    private CardInput card;

    /** 用已存卡时的对外已存卡 Token */
    private String storedCardToken;

    /** 消费者是否同意支付成功后保存卡 */
    private boolean consentToSave;

    /** 工具期限上限：card 只能缩短 */
    private Instant expiresAt;

    @Override
    public String toString() {
        return "CardInstrumentCommand[REDACTED]";
    }
}
