package com.paytech.card.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.card.core.dal.dataobject.CardBinImportError;
import com.paytech.card.core.dal.mapper.CardBinImportErrorMapper;
import com.paytech.card.domainservice.repository.CardBinImportErrorRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BIN 库导入错误明细数据访问 */
@Repository
@AllArgsConstructor
public class CardBinImportErrorRepositoryImpl implements CardBinImportErrorRepository {
    private final CardBinImportErrorMapper cardBinImportErrorMapper;

    /** 插入整批；影响行数不等于批量大小视为系统错误。 */
    @Override
    public void insertErrors(List<CardBinImportError> errors) {
        if (errors.isEmpty()) {
            return;
        }
        if (cardBinImportErrorMapper.insertErrors(errors) != errors.size()) {
            throw new ErrorException("BIN 导入错误明细保存行数不符: " + errors.size());
        }
    }

    @Override
    public long countErrors(long versionId) {
        return cardBinImportErrorMapper.countErrors(versionId);
    }

    @Override
    public List<CardBinImportError> listErrors(long versionId, int offset, int limit) {
        return cardBinImportErrorMapper.listErrors(versionId, offset, limit);
    }
}
