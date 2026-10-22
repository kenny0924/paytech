package com.paytech.platform.support.filedocument.integration.storage;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageBucket;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageRoute;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageCatalog;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.filedocument.integration.storage.minio.MinioFileStorage;
import com.paytech.platform.support.filedocument.integration.storage.s3.S3FileStorage;

/**
 * 存储实例、存储桶与写入路由的进程内快照，以及按记录坐标构建本次存储适配器的唯一入口
 * <li>快照不含密钥；首次使用时加载，此后由定时任务刷新，本进程的管理写入提交后立即刷新。刷新失败保留上一份。</li>
 * <li>写入按来源系统选择精确路由，否则使用默认路由 *；要求路由桶与实例均为 ACTIVE。</li>
 * <li>读取只按记录上的实例代码与桶名定位，不受路由影响；实例 ACTIVE 且桶 ACTIVE / READ_ONLY 才可读。</li>
 * <li>不满足条件时返回不可用（503）且不发起存储或 Vault 调用；STATIC 实例每次构建前从 Vault 读取密钥，不缓存原值。</li>
 */
@Component
public class FileStorageRegistry {

    /** 默认路由的来源系统。 */
    public static final String DEFAULT_ROUTE = "*";

    private static final Logger LOG = LoggerFactory.getLogger(FileStorageRegistry.class);

    private final Clock clock;
    private final StorageCredentialVault credentials;
    private final FileStorageManagementService management;
    private final AtomicReference<FileStorageCatalog> catalog = new AtomicReference<>();

    /** 注入时钟、密钥读取与目录查询。 */
    public FileStorageRegistry(Clock clock, StorageCredentialVault credentials,
        FileStorageManagementService management) {
        this.clock = clock;
        this.credentials = credentials;
        this.management = management;
    }

    /**
     * 新上传的写入目标
     *
     * @param sourceSystem 来源系统
     * @return 实例代码、桶名、扫描策略与上传期限
     */
    public StorageWriteTarget forWrite(String sourceSystem) {
        FileStorageCatalog current = catalog();
        FileStorageRoute route = current.route(sourceSystem);
        if (route == null) {
            route = current.route(DEFAULT_ROUTE);
        }
        if (route == null) {
            throw new ServiceUnavailableException();
        }
        FileStorageBucket bucket = current.bucket(route.getBucketId());
        FileStorageInstance instance = bucket == null ? null : current.instance(bucket.getInstanceId());
        if (!writable(instance, bucket)) {
            throw new ServiceUnavailableException();
        }
        return new StorageWriteTarget(instance.getInstanceCode(), bucket.getBucketName(), route.getScanPolicyCode(),
            route.getMaxUploadSeconds());
    }

    /** 为新上传签发写入授权：记录坐标的实例与桶必须仍可写。 */
    public FileObjectStorage openForWrite(String provider, String container) {
        FileStorageCatalog current = catalog();
        FileStorageInstance instance = current.instance(provider);
        FileStorageBucket bucket = instance == null ? null : current.bucket(instance.getInstanceId(), container);
        if (!writable(instance, bucket)) {
            throw new ServiceUnavailableException();
        }
        return connect(instance);
    }

    /** 完成、检查与下载读取：记录坐标的实例 ACTIVE 且桶 ACTIVE / READ_ONLY。 */
    public FileObjectStorage openForRead(String provider, String container) {
        FileStorageCatalog current = catalog();
        FileStorageInstance instance = current.instance(provider);
        FileStorageBucket bucket = instance == null ? null : current.bucket(instance.getInstanceId(), container);
        if (instance == null || bucket == null || instance.getStatus() != FileStorageInstanceStatus.ACTIVE
            || !(bucket.getStatus() == FileStorageBucketStatus.ACTIVE
                || bucket.getStatus() == FileStorageBucketStatus.READ_ONLY)) {
            throw new ServiceUnavailableException();
        }
        return connect(instance);
    }

    /**
     * 按实例构建本次使用的适配器；存储管理检查也用于 DRAFT 实例
     *
     * @param instance 从快照或数据库读取的实例
     * @return 调用方负责关闭的适配器
     */
    public FileObjectStorage connect(FileStorageInstance instance) {
        StorageCredential credential = credential(instance);
        try {
            return instance.getStorageType() == FileStorageType.MINIO
                ? new MinioFileStorage(clock, instance, credential) : new S3FileStorage(clock, instance, credential);
        } catch (IllegalArgumentException invalid) {
            throw new ServiceUnavailableException(invalid);
        }
    }

    /** 立即重新加载快照；失败保留上一份并记录告警，不向调用方抛出。 */
    public synchronized void refresh() {
        try {
            catalog.set(management.loadCatalog());
        } catch (RuntimeException failure) {
            LOG.warn("File storage snapshot refresh failed, keeping previous snapshot: loaded={}, cause={}",
                catalog.get() != null, failure.getClass().getSimpleName());
        }
    }

    /** 当前快照；尚未加载时同步加载一次，仍失败则为不可用。 */
    FileStorageCatalog catalog() {
        FileStorageCatalog current = catalog.get();
        if (current != null) {
            return current;
        }
        refresh();
        current = catalog.get();
        if (current == null) {
            throw new ServiceUnavailableException();
        }
        return current;
    }

    /** 实例与桶都为 ACTIVE 才接受新上传。 */
    static boolean writable(FileStorageInstance instance, FileStorageBucket bucket) {
        return instance != null && bucket != null && instance.getStatus() == FileStorageInstanceStatus.ACTIVE
            && bucket.getStatus() == FileStorageBucketStatus.ACTIVE;
    }

    /** STATIC 从 Vault 读取本次密钥；IAM_ROLE 只用于 S3，使用默认凭据链。 */
    StorageCredential credential(FileStorageInstance instance) {
        if (instance.getCredentialMode() == FileStorageCredentialMode.IAM_ROLE) {
            if (instance.getStorageType() != FileStorageType.S3) {
                throw new ServiceUnavailableException();
            }
            return null;
        }
        if (instance.getCredentialVaultToken() == null) {
            throw new ServiceUnavailableException();
        }
        return credentials.reveal(instance.getCredentialVaultToken());
    }
}
