package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;
import com.paytech.platform.support.api.enums.FileStorageType;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.FileStorageTestMapper;
import com.paytech.platform.support.test.mocker.external.StorageVaultMocker;

import lombok.RequiredArgsConstructor;

/**
 * 文件存储实例、存储桶与写入路由的本地前置数据：直接插入三张表，并为 STATIC 实例配置 Vault 凭据 DTO
 * <li>标准场景登记两个实例，都指向本机 MinIO 的测试存储桶：MINIO 类型（MinIO SDK）与 S3 类型（AWS SDK，路径风格）。</li>
 * <li>插入后刷新进程内存储快照；三张表由 {@link PlatformDataCleaner} 整表回收。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class StorageInstanceMocker implements Clear {

    /** MinIO SDK 实例代码。 */
    public static final String MINIO_INSTANCE = "UT_MINIO";

    /** AWS SDK（路径风格）实例代码。 */
    public static final String S3_INSTANCE = "UT_S3";

    /** MINIO 实例的密钥 Token。 */
    public static final String MINIO_TOKEN = StorageVaultMocker.token("storage-ut-minio");

    /** S3 实例的密钥 Token。 */
    public static final String S3_TOKEN = StorageVaultMocker.token("storage-ut-s3");

    /** 默认路由。 */
    public static final String DEFAULT_ROUTE = "*";

    /** 内部文件接口测试调用方，与 application-mock.yml 的签发白名单一致。 */
    public static final String SOURCE = "file-test";

    /** 另一内部文件接口测试调用方。 */
    public static final String PEER_SOURCE = "file-test-peer";

    /** 标准上传期限秒数。 */
    public static final int UPLOAD_SECONDS = 900;

    /** 使用本机 MinIO 管理员密钥的实例代码（公开桶场景：Platform 测试账号对临时桶没有权限）。 */
    public static final String ROOT_INSTANCE = "UT_ROOT";

    /** 管理员实例的密钥 Token。 */
    public static final String ROOT_TOKEN = StorageVaultMocker.token("storage-ut-root");

    private final Clock clock;
    private final FileStorageTestMapper mapper;
    private final StorageVaultMocker vault;
    private final StorageObjectMocker objects;
    private final TempBucketMocker tempBuckets;
    private final FileStorageRegistry registry;
    private final Map<String, Long> instances = new HashMap<>();
    private final Map<String, Long> buckets = new HashMap<>();

    /**
     * 标准场景：两个 ACTIVE 实例各有一个 ACTIVE 测试存储桶，默认路由指向 MINIO 实例
     *
     * @param policyCode 默认路由冻结的扫描策略代码
     */
    public void standard(String policyCode) {
        staticInstance(MINIO_INSTANCE, FileStorageType.MINIO, MINIO_TOKEN, false);
        staticInstance(S3_INSTANCE, FileStorageType.S3, S3_TOKEN, true);
        bucket(MINIO_INSTANCE, objects.bucket(), FileStorageBucketStatus.ACTIVE);
        bucket(S3_INSTANCE, objects.bucket(), FileStorageBucketStatus.ACTIVE);
        route(DEFAULT_ROUTE, MINIO_INSTANCE, policyCode);
        refresh();
    }

    /** 指向本机 MinIO 的 ACTIVE STATIC 实例，并配置其 Token 的 Reveal DTO。 */
    public long staticInstance(String code, FileStorageType type, String token, boolean pathStyle) {
        vault.credential(token, objects.accessKey(), objects.secretKey());
        return instance(code, type, FileStorageCredentialMode.STATIC, token, pathStyle, FileStorageInstanceStatus.ACTIVE);
    }

    /** 使用本机 MinIO 管理员密钥的 ACTIVE MINIO 实例，可访问本例创建的临时桶。 */
    public long rootInstance() {
        return rootInstance(ROOT_INSTANCE, FileStorageType.MINIO);
    }

    /** 使用本机 MinIO 管理员密钥、指定适配器类型的 ACTIVE 实例（路径风格），可访问本例创建的临时桶。 */
    public long rootInstance(String code, FileStorageType type) {
        vault.credential(ROOT_TOKEN, tempBuckets.rootUser(), tempBuckets.rootPassword());
        return instance(code, type, FileStorageCredentialMode.STATIC, ROOT_TOKEN, true,
            FileStorageInstanceStatus.ACTIVE);
    }

    /** 插入一个指向本机 MinIO 的实例。 */
    public long instance(String code, FileStorageType type, FileStorageCredentialMode mode, String token,
        boolean pathStyle, FileStorageInstanceStatus status) {
        long id = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertInstance(id, code, type.getCode(), objects.endpoint(), objects.region(), pathStyle,
            objects.certificatePem(), mode.getCode(), token, status.getCode(), now()));
        instances.put(code, id);
        return id;
    }

    /** 插入实例下的私有存储桶；ACTIVE 时同时登记一次 OK 检查。 */
    public long bucket(String instanceCode, String bucketName, FileStorageBucketStatus status) {
        return bucket(instanceCode, bucketName, FileAccessLevel.PRIVATE, null, status);
    }

    /** 插入实例下的公开存储桶；ACTIVE 时同时登记一次 OK 检查。 */
    public long publicBucket(String instanceCode, String bucketName, String publicBaseUrl,
        FileStorageBucketStatus status) {
        return bucket(instanceCode, bucketName, FileAccessLevel.PUBLIC, publicBaseUrl, status);
    }

    /** 插入实例下的存储桶。 */
    private long bucket(String instanceCode, String bucketName, FileAccessLevel level, String publicBaseUrl,
        FileStorageBucketStatus status) {
        long id = OperatorBuilder.nextId();
        boolean checked = status != FileStorageBucketStatus.DRAFT;
        assertEquals(1, mapper.insertBucket(id, instances.get(instanceCode), bucketName, level.getCode(),
            publicBaseUrl, status.getCode(), checked ? now() : null,
            checked ? FileStorageCheckResult.OK.getCode() : null, now()));
        buckets.put(instanceCode + "/" + bucketName, id);
        return id;
    }

    /** 插入指向实例测试存储桶的私有路由，上传期限为标准值。 */
    public void route(String source, String instanceCode, String policyCode) {
        route(source, FileAccessLevel.PRIVATE, instanceCode, objects.bucket(), policyCode);
    }

    /** 插入指定访问级别、指向实例某存储桶的路由，上传期限为标准值。 */
    public void route(String source, FileAccessLevel level, String instanceCode, String bucketName,
        String policyCode) {
        assertEquals(1, mapper.insertRoute(OperatorBuilder.nextId(), source, level.getCode(),
            bucketId(instanceCode, bucketName), policyCode, UPLOAD_SECONDS, now()));
    }

    /** 直接改写实例测试存储桶的状态并刷新快照。 */
    public void bucketStatus(String instanceCode, FileStorageBucketStatus status) {
        assertEquals(1, mapper.bucketStatus(bucketId(instanceCode, objects.bucket()), status.getCode()));
        refresh();
    }

    /** 直接改写实例状态并刷新快照。 */
    public void instanceStatus(String instanceCode, FileStorageInstanceStatus status) {
        assertEquals(1, mapper.instanceStatus(instances.get(instanceCode), status.getCode()));
        refresh();
    }

    /** 已插入存储桶的主键。 */
    public long bucketId(String instanceCode, String bucketName) {
        return buckets.get(instanceCode + "/" + bucketName);
    }

    /** 立即刷新进程内存储快照。 */
    public void refresh() {
        registry.refresh();
    }

    /** 每例清理本地登记表；数据库行由公共清理回收。 */
    @Override
    public void clear() {
        instances.clear();
        buckets.clear();
    }

    /** 与被测链路相同的时钟，截断到数据库微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
