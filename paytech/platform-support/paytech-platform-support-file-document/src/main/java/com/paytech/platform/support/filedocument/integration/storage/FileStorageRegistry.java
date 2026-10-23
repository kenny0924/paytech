package com.paytech.platform.support.filedocument.integration.storage;

import java.io.IOException;
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
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageCatalog;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;
import com.paytech.platform.support.filedocument.domainservice.service.FileStorageManagementService;
import com.paytech.platform.support.filedocument.integration.storage.minio.MinioFileStorage;
import com.paytech.platform.support.filedocument.integration.storage.s3.S3FileStorage;

/**
 * 存储实例、存储桶与写入路由的进程内快照，以及按记录坐标构建本次存储适配器的唯一入口
 * <li>快照不含密钥；首次使用时加载，此后由定时任务刷新，本进程的管理写入提交后立即刷新。刷新失败保留上一份。</li>
 * <li>写入按 (来源系统, 访问级别) 选择精确路由，否则使用该级别的默认路由 (*, 访问级别)，不跨级别回落；要求路由桶与实例均为
 * ACTIVE，且桶与路由同访问级别。</li>
 * <li>读取只按记录上的实例代码与桶名定位，不受路由影响；实例 ACTIVE 且桶 ACTIVE / READ_ONLY 才可读。</li>
 * <li>不满足条件时返回不可用（503）且不发起存储或 Vault 调用；STATIC 实例每次构建前从 Vault 读取密钥，不缓存原值。</li>
 */
@Component
public class FileStorageRegistry {

    /** 默认路由的来源系统。 */
    public static final String DEFAULT_ROUTE = "*";

    /** 上传授权有效秒数的上限，与写入路由的取值范围一致。 */
    static final int MAX_UPLOAD_SECONDS = 900;

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
     * @param accessLevel 调用方声明的访问级别
     * @return 实例代码、桶名、扫描策略与上传期限；没有该级别的可用路由返回不可用（503）
     */
    public StorageWriteTarget forWrite(String sourceSystem, FileAccessLevel accessLevel) {
        FileStorageCatalog current = catalog();
        FileStorageRoute route = route(current, sourceSystem, accessLevel);
        if (route == null) {
            throw new ServiceUnavailableException();
        }
        FileStorageBucket bucket = current.bucket(route.getBucketId());
        FileStorageInstance instance = bucket == null ? null : current.instance(bucket.getInstanceId());
        if (!writable(instance, bucket) || bucket.getAccessLevel() != accessLevel) {
            throw new ServiceUnavailableException();
        }
        return new StorageWriteTarget(instance.getInstanceCode(), bucket.getBucketName(), route.getScanPolicyCode(),
            route.getMaxUploadSeconds());
    }

    /**
     * 替换上传的授权有效秒数：取来源系统在该级别的路由（精确或默认）的上传期限；路由已删除时取上限 900 秒
     *
     * @param sourceSystem 原文件的来源系统
     * @param accessLevel 原文件的访问级别
     * @return 上传授权有效秒数
     */
    public int replacementUploadSeconds(String sourceSystem, FileAccessLevel accessLevel) {
        FileStorageRoute route = route(catalog(), sourceSystem, accessLevel);
        return route == null ? MAX_UPLOAD_SECONDS : route.getMaxUploadSeconds();
    }

    /** 精确路由优先，其次同级别默认路由；都没有为空。 */
    static FileStorageRoute route(FileStorageCatalog current, String sourceSystem, FileAccessLevel accessLevel) {
        FileStorageRoute route = current.route(sourceSystem, accessLevel);
        return route != null ? route : current.route(DEFAULT_ROUTE, accessLevel);
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

    /**
     * Platform 进程内上传：按上传记录坐标的实例（须仍可写）把字节 PUT 到签发地址
     * <li>不读取存储密钥：授权已在签发时生成；只使用实例的超时与信任设置。</li>
     * <li>存储返回非 2xx（含 If-None-Match 拒绝覆盖）或连接失败时返回不可用（503），由调用方决定是否重来。</li>
     *
     * @param provider 上传记录的实例代码
     * @param container 上传记录的存储桶
     * @param grant 本次上传的 PUT 授权
     * @param content 对象字节
     */
    public void putThroughGrant(String provider, String container, Grant grant, byte[] content) {
        FileStorageCatalog current = catalog();
        FileStorageInstance instance = current.instance(provider);
        FileStorageBucket bucket = instance == null ? null : current.bucket(instance.getInstanceId(), container);
        if (!writable(instance, bucket)) {
            throw new ServiceUnavailableException();
        }
        int status;
        try (GrantedUploadHttp http = new GrantedUploadHttp(instance)) {
            status = http.put(grant, content);
        } catch (IOException | RuntimeException unavailable) {
            throw new ServiceUnavailableException(unavailable);
        }
        if (status < 200 || status > 299) {
            LOG.warn("Granted upload rejected by storage: provider={}, status={}", provider, status);
            throw new ServiceUnavailableException();
        }
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
