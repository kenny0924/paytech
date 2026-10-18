package com.paytech.card.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.card.core.dal.dataobject.CardMaterialTicket;
import com.paytech.card.core.dal.mapper.CardMaterialTicketMapper;
import com.paytech.card.domainservice.repository.CardMaterialTicketRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 读卡票据数据访问 */
@Repository
@AllArgsConstructor
public class CardMaterialTicketRepositoryImpl implements CardMaterialTicketRepository {
    private final CardMaterialTicketMapper cardMaterialTicketMapper;

    @Override
    public void insertTicket(CardMaterialTicket ticket) {
        if (cardMaterialTicketMapper.insertTicket(ticket) != 1) {
            throw new ErrorException("读卡票据保存失败: " + ticket.getCardMaterialTicketId());
        }
    }

    @Override
    public CardMaterialTicket findByHash(String ticketHash) {
        return cardMaterialTicketMapper.findByHash(ticketHash);
    }

    /** 条件更新成功后同步对象。 */
    @Override
    public boolean markUsed(CardMaterialTicket ticket, Instant usedAt) {
        if (cardMaterialTicketMapper.markUsed(ticket.getCardMaterialTicketId(), usedAt) != 1) {
            return false;
        }
        ticket.setUsedAt(usedAt);
        ticket.setUpdateTime(usedAt);
        return true;
    }
}
