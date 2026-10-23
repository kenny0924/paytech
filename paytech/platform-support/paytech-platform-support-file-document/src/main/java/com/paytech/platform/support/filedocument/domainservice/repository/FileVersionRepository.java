package com.paytech.platform.support.filedocument.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.dal.dataobject.CompletedUploadFile;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileVersion;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;

/** 文件版本表仓储；条件更新成功后同步传入的原对象。 */
public interface FileVersionRepository {

    /** 插入新版本，参与调用方事务。 */
    void insert(FileVersion version);

    /** 按文件与版本号读取；不存在为空。 */
    FileVersion find(long fileId, int versionNo);

    /** 按文件与版本号排他锁定；不存在为空。 */
    FileVersion lock(long fileId, int versionNo);

    /** 文件的全部版本，按版本号倒序。 */
    List<FileVersion> findByFile(long fileId);

    /** 上传已产生的版本；未产生为空。 */
    CompletedUploadFile completedBy(long uploadId);

    /** 文件当前最大版本号；没有版本为 0。 */
    int maxVersionNo(long fileId);

    /** 比当前版本新、仍在检查中的版本号；没有为空。 */
    Integer pendingVersionNo(long fileId, int currentVersionNo);

    /** 按原版本与原版本状态更新扫描与版本状态。 */
    void updateScan(FileVersion version, FileScanStatus scanStatus, FileVersionStatus versionStatus, Instant now);

    /** 把文件的原 CURRENT 版本改为 SUPERSEDED；该版本必须恰为 CURRENT。 */
    void supersede(long fileId, int versionNo, Instant now);
}
