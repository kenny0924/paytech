package com.paytech.card.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;
import com.paytech.card.core.dal.mapper.CardBinLibraryVersionMapper;
import com.paytech.card.domainservice.repository.CardBinLibraryVersionRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BIN 库版本数据访问 */
@Repository
@AllArgsConstructor
public class CardBinLibraryVersionRepositoryImpl implements CardBinLibraryVersionRepository {
    private final CardBinLibraryVersionMapper cardBinLibraryVersionMapper;

    /** 插入一行；影响行数不为 1 视为系统错误。 */
    @Override
    public void insertVersion(CardBinLibraryVersion version) {
        if (cardBinLibraryVersionMapper.insertVersion(version) != 1) {
            throw new ErrorException("BIN 库版本保存失败: " + version.getVersionId());
        }
    }

    @Override
    public CardBinLibraryVersion findById(long versionId) {
        return cardBinLibraryVersionMapper.findById(versionId);
    }

    @Override
    public CardBinLibraryVersion findByVersionNo(int versionNo) {
        return cardBinLibraryVersionMapper.findByVersionNo(versionNo);
    }

    @Override
    public CardBinLibraryVersion findByFileAddress(String fileAddress) {
        return cardBinLibraryVersionMapper.findByFileAddress(fileAddress);
    }

    @Override
    public CardBinLibraryVersion findActive() {
        return cardBinLibraryVersionMapper.findActive();
    }

    /** 最大版本号加 1；并发由资源锁与唯一索引兜底。 */
    @Override
    public int nextVersionNo() {
        Integer max = cardBinLibraryVersionMapper.maxVersionNo();
        return max == null ? 1 : max + 1;
    }

    @Override
    public long countVersions(BinLibraryVersionStatus status) {
        return cardBinLibraryVersionMapper.countVersions(status);
    }

    @Override
    public List<CardBinLibraryVersion> listVersions(BinLibraryVersionStatus status, int offset, int limit) {
        return cardBinLibraryVersionMapper.listVersions(status, offset, limit);
    }

    /** 条件更新成功后同步状态、原因、行数与版本。 */
    @Override
    public void markInvalid(CardBinLibraryVersion version, String failureReason, Integer totalRows, int errorRows,
        Instant now) {
        requireOne(cardBinLibraryVersionMapper.markInvalid(version.getVersionId(), version.getVersion(), failureReason,
            totalRows, errorRows, now), version);
        version.setStatus(BinLibraryVersionStatus.INVALID);
        version.setFailureReason(failureReason);
        version.setTotalRows(totalRows);
        version.setErrorRows(errorRows);
        synced(version, now);
    }

    /** 条件更新成功后同步进度与版本。 */
    @Override
    public void updateProgress(CardBinLibraryVersion version, int processedRows, Instant now) {
        requireOne(cardBinLibraryVersionMapper.updateProgress(version.getVersionId(), version.getVersion(), processedRows,
            now), version);
        version.setProcessedRows(processedRows);
        synced(version, now);
    }

    /** 条件更新成功后同步状态、行数与版本。 */
    @Override
    public void markValid(CardBinLibraryVersion version, int totalRows, Instant now) {
        requireOne(cardBinLibraryVersionMapper.markValid(version.getVersionId(), version.getVersion(), totalRows, now),
            version);
        version.setStatus(BinLibraryVersionStatus.VALID);
        version.setTotalRows(totalRows);
        version.setProcessedRows(totalRows);
        synced(version, now);
    }

    /** 条件更新成功后同步状态、占位与版本。 */
    @Override
    public void retire(CardBinLibraryVersion version, Instant now) {
        requireOne(cardBinLibraryVersionMapper.retire(version.getVersionId(), version.getVersion(), now), version);
        version.setStatus(BinLibraryVersionStatus.RETIRED);
        version.setActiveSlot(null);
        synced(version, now);
    }

    /** 条件更新成功后同步状态、占位与版本。 */
    @Override
    public void activate(CardBinLibraryVersion version, Instant now) {
        requireOne(cardBinLibraryVersionMapper.activate(version.getVersionId(), version.getVersion(), version.getStatus(),
            now), version);
        version.setStatus(BinLibraryVersionStatus.ACTIVE);
        version.setActiveSlot(1);
        synced(version, now);
    }

    /** 条件更新必须恰好一行，否则版本已被并发修改。 */
    static void requireOne(int rows, CardBinLibraryVersion version) {
        if (rows != 1) {
            throw new ConcurrentRequestException("BIN 库版本已被并发修改: v" + version.getVersionNo());
        }
    }

    /** 同步乐观版本与更新时间。 */
    static void synced(CardBinLibraryVersion version, Instant now) {
        version.setVersion(version.getVersion() + 1);
        version.setUpdateTime(now);
    }
}
