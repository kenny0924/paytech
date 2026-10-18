package com.paytech.card.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.card.core.dal.dataobject.CardMaterialTicket;

/** 读卡票据 SQL 映射 */
@Mapper
public interface CardMaterialTicketMapper {
    /** 插入新票据。 */
    int insertTicket(CardMaterialTicket ticket);

    /** 按票据摘要查询。 */
    CardMaterialTicket findByHash(@Param("ticketHash") String ticketHash);

    /** 未使用且未过期时标记已用；已用、已过期或并发使用时影响行数为 0。 */
    int markUsed(@Param("cardMaterialTicketId") long cardMaterialTicketId, @Param("usedAt") Instant usedAt);
}
