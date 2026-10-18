package com.paytech.card.domainservice.repository;

import java.time.Instant;

import com.paytech.card.core.dal.dataobject.CardMaterialTicket;

/** 读卡票据数据访问 */
public interface CardMaterialTicketRepository {
    /** 插入票据；影响行数不为 1 视为系统错误。 */
    void insertTicket(CardMaterialTicket ticket);

    /** 按票据摘要查询；不存在时返回 null。 */
    CardMaterialTicket findByHash(String ticketHash);

    /**
     * 原子标记票据已用
     *
     * @return 本次是否成功标记；已用、已过期或被并发使用时为 false
     */
    boolean markUsed(CardMaterialTicket ticket, Instant usedAt);
}
