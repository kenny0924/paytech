package com.paytech.card.domainservice.repository.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.card.core.dal.dataobject.CardBinRange;
import com.paytech.card.core.dal.mapper.CardBinRangeMapper;
import com.paytech.card.domain.binlibrary.BinRangeFilter;
import com.paytech.card.domain.binlibrary.CardBinMatchView;
import com.paytech.card.domainservice.repository.CardBinRangeRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BIN 号段数据访问 */
@Repository
@AllArgsConstructor
public class CardBinRangeRepositoryImpl implements CardBinRangeRepository {
    /** 最短号段前缀位数。 */
    static final int MIN_PREFIX_LENGTH = 6;

    private final CardBinRangeMapper cardBinRangeMapper;

    /** 插入整批；影响行数不等于批量大小视为系统错误。 */
    @Override
    public void insertRanges(List<CardBinRange> ranges) {
        if (ranges.isEmpty()) {
            return;
        }
        if (cardBinRangeMapper.insertRanges(ranges) != ranges.size()) {
            throw new ErrorException("BIN 号段批量保存行数不符: " + ranges.size());
        }
    }

    @Override
    public int deleteRanges(long versionId, int limit) {
        return cardBinRangeMapper.deleteRanges(versionId, limit);
    }

    @Override
    public CardBinMatchView findMatch(String cardBin, Integer versionNo) {
        return cardBinRangeMapper.selectMatch(prefixes(cardBin), versionNo);
    }

    @Override
    public long countRanges(long versionId, BinRangeFilter filter) {
        return cardBinRangeMapper.countRanges(versionId, filter);
    }

    @Override
    public List<CardBinRange> listRanges(long versionId, BinRangeFilter filter, int offset, int limit) {
        return cardBinRangeMapper.selectRanges(versionId, filter, offset, limit);
    }

    /** 由长到短的候选前缀：BIN 自身到 6 位。 */
    static List<String> prefixes(String cardBin) {
        List<String> prefixes = new ArrayList<>();
        for (int length = cardBin.length(); length >= MIN_PREFIX_LENGTH; length--) {
            prefixes.add(cardBin.substring(0, length));
        }
        return prefixes;
    }
}
