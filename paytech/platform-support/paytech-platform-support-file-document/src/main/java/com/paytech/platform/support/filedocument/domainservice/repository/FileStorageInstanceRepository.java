package com.paytech.platform.support.filedocument.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;

/** 存储实例表仓储；条件更新成功后同步传入的原对象。 */
public interface FileStorageInstanceRepository {

    /** 全部实例，按实例代码排序。 */
    List<FileStorageInstance> findAll();

    /** 按实例代码读取；不存在为空。 */
    FileStorageInstance findByCode(String instanceCode);

    /** 按实例代码排他锁定；不存在为空。 */
    FileStorageInstance lockByCode(String instanceCode);

    /** 插入新实例；代码已存在转换为业务拒绝。 */
    void insert(FileStorageInstance instance);

    /** 按原版本修改连接设置。 */
    void updateSettings(FileStorageInstance instance, FileStorageInstance target, Instant now);

    /** 按原版本替换凭据方式与 Token。 */
    void updateCredential(FileStorageInstance instance, FileStorageCredentialMode mode, String token, Instant now);

    /** 按原版本与原状态切换状态。 */
    void updateStatus(FileStorageInstance instance, FileStorageInstanceStatus status, Instant now);
}
