package com.paytech.card.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.card.core.dal.dataobject.CardBinLibraryActivation;
import com.paytech.card.core.dal.mapper.CardBinLibraryActivationMapper;
import com.paytech.card.domain.binlibrary.BinLibraryActivationView;
import com.paytech.card.domainservice.repository.CardBinLibraryActivationRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BIN 库激活记录数据访问 */
@Repository
@AllArgsConstructor
public class CardBinLibraryActivationRepositoryImpl implements CardBinLibraryActivationRepository {
    private final CardBinLibraryActivationMapper cardBinLibraryActivationMapper;

    /** 插入一行；影响行数不为 1 视为系统错误。 */
    @Override
    public void insertActivation(CardBinLibraryActivation activation) {
        if (cardBinLibraryActivationMapper.insertActivation(activation) != 1) {
            throw new ErrorException("BIN 库激活记录保存失败: " + activation.getActivationId());
        }
    }

    @Override
    public long countActivations() {
        return cardBinLibraryActivationMapper.countActivations();
    }

    @Override
    public List<BinLibraryActivationView> listActivations(int offset, int limit) {
        return cardBinLibraryActivationMapper.listActivations(offset, limit);
    }
}
