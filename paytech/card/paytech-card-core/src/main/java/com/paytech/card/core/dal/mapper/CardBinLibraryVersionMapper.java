package com.paytech.card.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;

/** BIN 库版本 SQL 映射 */
@Mapper
public interface CardBinLibraryVersionMapper {
    /** 插入新版本；版本号、文件地址或生效占位重复时由唯一索引拒绝。 */
    int insertVersion(CardBinLibraryVersion version);

    /** 按主键查询。 */
    CardBinLibraryVersion findById(@Param("versionId") long versionId);

    /** 按版本号查询。 */
    CardBinLibraryVersion findByVersionNo(@Param("versionNo") int versionNo);

    /** 按文件地址查询。 */
    CardBinLibraryVersion findByFileAddress(@Param("fileAddress") String fileAddress);

    /** 查询当前生效版本。 */
    CardBinLibraryVersion findActive();

    /** 当前最大版本号；没有版本时为 null。 */
    Integer maxVersionNo();

    /** 按可选状态计数。 */
    long countVersions(@Param("status") BinLibraryVersionStatus status);

    /** 按可选状态分页，版本号倒序。 */
    List<CardBinLibraryVersion> listVersions(@Param("status") BinLibraryVersionStatus status, @Param("offset") int offset,
        @Param("limit") int limit);

    /** VALIDATING 且版本一致时写入失败结果。 */
    int markInvalid(@Param("versionId") long versionId, @Param("version") int version,
        @Param("failureReason") String failureReason, @Param("totalRows") Integer totalRows,
        @Param("errorRows") int errorRows, @Param("now") Instant now);

    /** VALIDATING 且版本一致时更新写入进度。 */
    int updateProgress(@Param("versionId") long versionId, @Param("version") int version,
        @Param("processedRows") int processedRows, @Param("now") Instant now);

    /** VALIDATING 且版本一致时转为 VALID。 */
    int markValid(@Param("versionId") long versionId, @Param("version") int version, @Param("totalRows") int totalRows,
        @Param("now") Instant now);

    /** ACTIVE 且版本一致时转为 RETIRED 并释放生效占位。 */
    int retire(@Param("versionId") long versionId, @Param("version") int version, @Param("now") Instant now);

    /** 期望状态且版本一致时转为 ACTIVE 并占用生效占位。 */
    int activate(@Param("versionId") long versionId, @Param("version") int version,
        @Param("expectedStatus") BinLibraryVersionStatus expectedStatus, @Param("now") Instant now);
}
