package com.paytech.platform.support.filedocument.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileStorageInstanceMapper;
import com.paytech.platform.support.filedocument.domainservice.repository.FileStorageInstanceRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的存储实例仓储。 */
@Repository
@AllArgsConstructor
public class FileStorageInstanceRepositoryImpl implements FileStorageInstanceRepository {

    private final FileStorageInstanceMapper mapper;

    /** 全部实例。 */
    @Override
    public List<FileStorageInstance> findAll() {
        return mapper.selectAll();
    }

    /** 按代码读取。 */
    @Override
    public FileStorageInstance findByCode(String instanceCode) {
        return mapper.selectByCode(instanceCode);
    }

    /** 按代码排他锁定。 */
    @Override
    public FileStorageInstance lockByCode(String instanceCode) {
        return mapper.lockByCode(instanceCode);
    }

    /** 只把实例代码唯一键冲突转换为“已存在”业务拒绝，其他故障保留原异常。 */
    @Override
    public void insert(FileStorageInstance instance) {
        try {
            requireSaved(mapper.insert(instance), instance.getInstanceCode());
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_file_storage_instance_code")) {
                throw failure;
            }
            throw new BizCheckFailException("存储实例已存在: " + instance.getInstanceCode(), failure);
        }
    }

    /** 条件更新成功后同步连接设置、版本与时间。 */
    @Override
    public void updateSettings(FileStorageInstance instance, FileStorageInstance target, Instant now) {
        requireSaved(mapper.updateSettings(instance, target, now), instance.getInstanceCode());
        instance.setEndpoint(target.getEndpoint());
        instance.setRegion(target.getRegion());
        instance.setPathStyleAccess(target.getPathStyleAccess());
        instance.setTrustedCertificatePem(target.getTrustedCertificatePem());
        instance.setConnectTimeoutMs(target.getConnectTimeoutMs());
        instance.setReadTimeoutMs(target.getReadTimeoutMs());
        advance(instance, now);
    }

    /** 条件更新成功后同步凭据方式、Token、密钥时间、版本与时间。 */
    @Override
    public void updateCredential(FileStorageInstance instance, FileStorageCredentialMode mode, String token,
        Instant now) {
        requireSaved(mapper.updateCredential(instance, mode, token, now), instance.getInstanceCode());
        instance.setCredentialMode(mode);
        instance.setCredentialVaultToken(token);
        instance.setCredentialUpdateTime(now);
        advance(instance, now);
    }

    /** 条件更新成功后同步状态、版本与时间。 */
    @Override
    public void updateStatus(FileStorageInstance instance, FileStorageInstanceStatus status, Instant now) {
        requireSaved(mapper.updateStatus(instance, status, now), instance.getInstanceCode());
        instance.setStatus(status);
        advance(instance, now);
    }

    /** 版本递增与更新时间同步。 */
    void advance(FileStorageInstance instance, Instant now) {
        instance.setVersion(instance.getVersion() + 1);
        instance.setUpdateTime(now);
    }

    /** 未精确保存一行时回滚，由调用方丢弃本次对象。 */
    void requireSaved(int changed, String instanceCode) {
        if (changed != 1) {
            throw new ConcurrentRequestException("存储实例已被其他操作修改，请刷新后重试: " + instanceCode);
        }
    }
}
