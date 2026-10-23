package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.dal.dataobject.CompletedUploadFile;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileVersion;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;

/** t_file_version 的 SQL 映射；SQL 位于 FileVersionMapper.xml。 */
public interface FileVersionMapper {

    /** 插入新版本。 */
    int insert(@Param("row") FileVersion row);

    /** 按文件与版本号读取。 */
    FileVersion select(@Param("fileId") long fileId, @Param("versionNo") int versionNo);

    /** 按文件与版本号排他锁定当前行。 */
    FileVersion lock(@Param("fileId") long fileId, @Param("versionNo") int versionNo);

    /** 文件的全部版本，按版本号倒序。 */
    List<FileVersion> selectByFile(@Param("fileId") long fileId);

    /** 上传已产生的版本；未产生为空。 */
    CompletedUploadFile selectCompleted(@Param("uploadId") long uploadId);

    /** 文件当前最大版本号；没有版本为 0。 */
    int maxVersionNo(@Param("fileId") long fileId);

    /** 比当前版本新、仍在检查中的版本号；没有为空。 */
    Integer pendingVersionNo(@Param("fileId") long fileId, @Param("currentVersionNo") int currentVersionNo);

    /** 按原版本与原版本状态更新扫描状态与版本状态。 */
    int updateScan(@Param("row") FileVersion row, @Param("scanStatus") FileScanStatus scanStatus,
        @Param("versionStatus") FileVersionStatus versionStatus, @Param("availableTime") Instant availableTime,
        @Param("now") Instant now);

    /** 把文件当前的 CURRENT 版本改为 SUPERSEDED。 */
    int supersede(@Param("fileId") long fileId, @Param("versionNo") int versionNo, @Param("now") Instant now);
}
