package com.paytech.card.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_card_material_ticket 的完整持久化映射；只含票据摘要，不通过 HTTP 或模块边界暴露。 */
@Getter
@Setter
public class CardMaterialTicket {
    /** 票据标识。 */
    private Long cardMaterialTicketId;

    /** 票据原值的 SHA-256 小写十六进制。 */
    private String ticketHash;

    /** 可读取的卡交易工具引用。 */
    private String instrumentRef;

    /** 签发方声明的支付尝试 ID。 */
    private Long attemptId;

    /** 签发方声明的资金动作 ID。 */
    private Long actionId;

    /** 用途稳定代码；以字符串保存，读卡时与入口用途比较，未知值视为不符。 */
    private String purpose;

    /** 截止时间。 */
    private Instant expiresAt;

    /** 使用时间；未使用为空。 */
    private Instant usedAt;

    /** 签发请求的调用方服务名。 */
    private String caller;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    @Override
    public String toString() {
        return "CardMaterialTicket[" + cardMaterialTicketId + "]";
    }
}
