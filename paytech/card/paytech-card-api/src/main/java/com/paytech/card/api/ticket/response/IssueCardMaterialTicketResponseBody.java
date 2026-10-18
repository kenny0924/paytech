package com.paytech.card.api.ticket.response;

import lombok.Getter;
import lombok.Setter;

/** 签发读卡票据响应；票据只在调用方内存中传递给 Platform */
@Getter
@Setter
public class IssueCardMaterialTicketResponseBody {
    /** 一次性票据：随机 256 位的 Base64URL（43 位），不含业务身份 */
    private String ticket;

    /** 票据截止时间，ISO-8601 UTC */
    private String expiresAt;

    @Override
    public String toString() {
        return "IssueCardMaterialTicketResponseBody[REDACTED]";
    }
}
