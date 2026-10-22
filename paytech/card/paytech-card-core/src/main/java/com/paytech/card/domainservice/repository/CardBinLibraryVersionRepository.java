package com.paytech.card.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;

/** BIN 库版本数据访问；状态与进度更新均以 version 条件更新，行数不符抛并发异常，成功后同步传入对象。 */
public interface CardBinLibraryVersionRepository {
    /**
     * 插入新版本
     * <li>版本号、文件地址或生效占位重复时抛出 {@link org.springframework.dao.DuplicateKeyException}。</li>
     */
    void insertVersion(CardBinLibraryVersion version);

    /** 按主键查询；不存在时返回 null。 */
    CardBinLibraryVersion findById(long versionId);

    /** 按版本号查询；不存在时返回 null。 */
    CardBinLibraryVersion findByVersionNo(int versionNo);

    /** 按文件地址查询；不存在时返回 null。 */
    CardBinLibraryVersion findByFileAddress(String fileAddress);

    /** 当前生效版本；没有时返回 null。 */
    CardBinLibraryVersion findActive();

    /** 下一个版本号：当前最大版本号加 1，没有版本时为 1。 */
    int nextVersionNo();

    /** 按可选状态计数。 */
    long countVersions(BinLibraryVersionStatus status);

    /** 按可选状态分页，版本号倒序。 */
    List<CardBinLibraryVersion> listVersions(BinLibraryVersionStatus status, int offset, int limit);

    /** VALIDATING → INVALID，写入失败原因与行数。 */
    void markInvalid(CardBinLibraryVersion version, String failureReason, Integer totalRows, int errorRows, Instant now);

    /** VALIDATING 时更新已写入行数。 */
    void updateProgress(CardBinLibraryVersion version, int processedRows, Instant now);

    /** VALIDATING → VALID，已写入行数等于总行数。 */
    void markValid(CardBinLibraryVersion version, int totalRows, Instant now);

    /** ACTIVE → RETIRED，释放生效占位。 */
    void retire(CardBinLibraryVersion version, Instant now);

    /** 当前状态（VALID 或 RETIRED）→ ACTIVE，占用生效占位。 */
    void activate(CardBinLibraryVersion version, Instant now);
}
