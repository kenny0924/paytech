package com.paytech.platform.support.filedocument.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.dal.dataobject.CompletedUploadFile;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileVersion;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileVersionMapper;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.domainservice.repository.FileVersionRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的文件版本仓储。 */
@Repository
@AllArgsConstructor
public class FileVersionRepositoryImpl implements FileVersionRepository {

    private final FileVersionMapper mapper;

    /** 插入新版本；版本号或上传重复由唯一键拒绝并回滚。 */
    @Override
    public void insert(FileVersion version) {
        requireSaved(mapper.insert(version), version);
    }

    /** 按文件与版本号读取。 */
    @Override
    public FileVersion find(long fileId, int versionNo) {
        return mapper.select(fileId, versionNo);
    }

    /** 按文件与版本号排他锁定。 */
    @Override
    public FileVersion lock(long fileId, int versionNo) {
        return mapper.lock(fileId, versionNo);
    }

    /** 文件的全部版本。 */
    @Override
    public List<FileVersion> findByFile(long fileId) {
        return mapper.selectByFile(fileId);
    }

    /** 上传已产生的版本。 */
    @Override
    public CompletedUploadFile completedBy(long uploadId) {
        return mapper.selectCompleted(uploadId);
    }

    /** 文件当前最大版本号。 */
    @Override
    public int maxVersionNo(long fileId) {
        return mapper.maxVersionNo(fileId);
    }

    /** 检查中的替换版本号。 */
    @Override
    public Integer pendingVersionNo(long fileId, int currentVersionNo) {
        return mapper.pendingVersionNo(fileId, currentVersionNo);
    }

    /** 条件更新成功后同步扫描状态、版本状态、可用时间、版本与时间。 */
    @Override
    public void updateScan(FileVersion version, FileScanStatus scanStatus, FileVersionStatus versionStatus,
        Instant now) {
        Instant availableTime = scanStatus == FileScanStatus.AVAILABLE ? now : null;
        requireSaved(mapper.updateScan(version, scanStatus, versionStatus, availableTime, now), version);
        version.setScanStatus(scanStatus);
        version.setVersionStatus(versionStatus);
        version.setAvailableTime(availableTime);
        version.setVersion(version.getVersion() + 1);
        version.setUpdateTime(now);
    }

    /** 原 CURRENT 版本必须恰好一行。 */
    @Override
    public void supersede(long fileId, int versionNo, Instant now) {
        if (mapper.supersede(fileId, versionNo, now) != 1) {
            throw new ConcurrentRequestException("文件当前版本已变更: " + fileId + ":v" + versionNo);
        }
    }

    /** 未精确保存一行时回滚，由调用方丢弃本次对象。 */
    void requireSaved(int changed, FileVersion version) {
        if (changed != 1) {
            throw new ConcurrentRequestException("文件版本已被其他操作修改: " + version);
        }
    }
}
