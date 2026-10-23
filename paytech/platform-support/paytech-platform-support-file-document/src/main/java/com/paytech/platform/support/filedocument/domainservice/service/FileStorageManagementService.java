package com.paytech.platform.support.filedocument.domainservice.service;

import java.util.List;

import com.paytech.platform.support.filedocument.domain.storage.ChangeStorageBucketStatusCommand;
import com.paytech.platform.support.filedocument.domain.storage.ChangeStorageInstanceStatusCommand;
import com.paytech.platform.support.filedocument.domain.storage.CreateStorageInstanceCommand;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageCatalog;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageInstanceView;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageRouteView;
import com.paytech.platform.support.filedocument.domain.storage.RecordStorageBucketCheckCommand;
import com.paytech.platform.support.filedocument.domain.storage.RegisterStorageBucketCommand;
import com.paytech.platform.support.filedocument.domain.storage.ReplaceStorageCredentialCommand;
import com.paytech.platform.support.filedocument.domain.storage.SaveStorageRouteCommand;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.storage.UpdateStorageBucketCommand;
import com.paytech.platform.support.filedocument.domain.storage.UpdateStorageInstanceCommand;

/**
 * 存储实例、存储桶与写入路由的本库规则与保存；Owner 为 file-document
 * <li>写方法在调用方事务内锁定当前行、核对版本与状态规则后条件更新，返回审计差异；不访问 Vault 或存储。</li>
 * <li>锁定顺序固定为实例 → 存储桶 → 检查策略 ACTIVE 修订 → 路由，跨表不变量（路由只指向同访问级别的 ACTIVE 桶与实例、有生效修订的
 * 检查策略，有路由的桶与实例不能停用）由行锁串行。</li>
 */
public interface FileStorageManagementService {

    /** 实例列表：每个实例附其存储桶与引用路由数，不含 Vault Token。 */
    List<FileStorageInstanceView> listInstances();

    /** 写入路由列表，含目标实例代码与存储桶名称。 */
    List<FileStorageRouteView> listRoutes();

    /** 读取三张表全集，供进程内快照使用。 */
    FileStorageCatalog loadCatalog();

    /** 实例代码已存在时拒绝；用于 Vault Protect 之前的预检。 */
    void requireInstanceAbsent(String instanceCode);

    /**
     * 实例存在且版本一致时返回主键；用于更换密钥前的预检
     *
     * @return 实例主键，用作 Vault Protect 幂等键的一部分
     */
    long requireInstanceVersion(String instanceCode, int expectedVersion);

    /** 新建 DRAFT 实例。 */
    FileStorageChange createInstance(CreateStorageInstanceCommand command);

    /** 修改连接设置。 */
    FileStorageChange updateInstance(UpdateStorageInstanceCommand command);

    /** 替换凭据方式与密钥 Token。 */
    FileStorageChange replaceCredential(ReplaceStorageCredentialCommand command);

    /** 切换实例状态。 */
    FileStorageChange changeInstanceStatus(ChangeStorageInstanceStatusCommand command);

    /** 登记 DRAFT 存储桶。 */
    FileStorageChange registerBucket(RegisterStorageBucketCommand command);

    /** 修改 DRAFT 公开桶的对外基础地址并清除最近检查结果。 */
    FileStorageChange updateBucket(UpdateStorageBucketCommand command);

    /** 回写存储桶检查结果。 */
    FileStorageChange recordBucketCheck(RecordStorageBucketCheckCommand command);

    /** 切换存储桶状态。 */
    FileStorageChange changeBucketStatus(ChangeStorageBucketStatusCommand command);

    /** 新建写入路由。 */
    FileStorageChange createRoute(SaveStorageRouteCommand command);

    /** 修改写入路由。 */
    FileStorageChange updateRoute(SaveStorageRouteCommand command);

    /** 删除写入路由；默认私有路由 (*, PRIVATE) 不能删除。 */
    FileStorageChange deleteRoute(String sourceSystem, FileAccessLevel accessLevel, int expectedVersion);
}
