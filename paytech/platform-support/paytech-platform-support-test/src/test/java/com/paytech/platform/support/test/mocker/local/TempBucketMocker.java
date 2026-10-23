package com.paytech.platform.support.test.mocker.local;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.filedocument.integration.storage.StorageTrust;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketEncryptionArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.Item;
import io.minio.messages.SseConfiguration;
import io.minio.messages.VersioningConfiguration;
import okhttp3.OkHttpClient;

/**
 * 以本机 MinIO 管理员账号临时创建用于存储桶检查负例的存储桶，并在每例结束删除
 * <li>只创建 ut-check- 前缀的随机桶：不开版本控制，或开版本控制但不配置默认加密，或版本控制、SSE-S3 加密齐备且允许匿名只读
 * （公开桶场景）；Platform 测试账号对这些桶没有权限，需要读写时使用管理员密钥的实例。</li>
 * <li>不修改仓库初始化的两个 Platform 存储桶；清理删除桶内全部版本后删除桶本身。</li>
 */
@TestComponent
public class TempBucketMocker implements Clear {

    private final Environment environment;
    private final StorageObjectMocker objects;
    private final List<String> buckets = new ArrayList<>();
    private MinioClient root;

    /** 只注入配置来源；管理员客户端在首次使用时创建。 */
    public TempBucketMocker(Environment environment, StorageObjectMocker objects) {
        this.environment = environment;
        this.objects = objects;
    }

    /** 管理员账号名，供需要在 Vault DTO 中返回管理员密钥的负例使用。 */
    public String rootUser() {
        return environment.getRequiredProperty("test.storage.root-user");
    }

    /** 管理员密钥。 */
    public String rootPassword() {
        return environment.getRequiredProperty("test.storage.root-password");
    }

    /** 新建不开版本控制的存储桶。 */
    public synchronized String unversioned() {
        return create(false);
    }

    /** 新建开启版本控制、未配置默认加密的存储桶。 */
    public synchronized String unencrypted() {
        return create(true);
    }

    /**
     * 新建版本控制与 SSE-S3 默认加密齐备、允许匿名读取对象（s3:GetObject）的桶，作为公开桶；登记为私有桶时用于匿名访问负例
     *
     * @return 桶名
     */
    public synchronized String publicReadable() {
        String bucket = create(true);
        try {
            client().setBucketEncryption(SetBucketEncryptionArgs.builder().bucket(bucket)
                .config(SseConfiguration.newConfigWithSseS3Rule()).build());
            client().setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket)
                .config("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                    + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}")
                .build());
            return bucket;
        } catch (Exception failure) {
            throw new IllegalStateException("配置本机 MinIO 公开测试桶失败", failure);
        }
    }

    /** 公开桶的路径风格对外基础地址（本机 MinIO）。 */
    public String publicBaseUrl(String bucket) {
        return objects.endpoint() + "/" + bucket;
    }

    /** 以管理员身份读取对象全部非删除标记版本号，最新在前。 */
    public List<String> versions(String bucket, String objectRef) {
        List<String> versions = new ArrayList<>();
        try {
            for (Result<Item> result : client().listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(objectRef)
                .includeVersions(true).build())) {
                Item item = result.get();
                if (objectRef.equals(item.objectName()) && !item.isDeleteMarker()) {
                    versions.add(item.versionId());
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException("列出本机 MinIO 临时桶对象版本失败", failure);
        }
        return versions;
    }

    /** 以管理员身份直接写入一个新版本，返回版本号；模拟授权外的写入或再次 PUT。 */
    public String put(String bucket, String objectRef, byte[] bytes) {
        try {
            return client().putObject(PutObjectArgs.builder().bucket(bucket).object(objectRef)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1).build()).versionId();
        } catch (Exception failure) {
            throw new IllegalStateException("写入本机 MinIO 临时桶对象失败", failure);
        }
    }

    /** 不存在的随机桶名。 */
    public String missing() {
        return "ut-missing-" + UUID.randomUUID().toString().substring(0, 18);
    }

    /** 每例开始时没有遗留登记。 */
    public synchronized void assertEmpty() {
        assertTrue(buckets.isEmpty(), "上一例临时存储桶未回收");
    }

    /** 删除本例创建的桶及其全部版本。 */
    @Override
    public synchronized void clear() {
        if (buckets.isEmpty()) {
            return;
        }
        List<Executable> actions = new ArrayList<>();
        for (String bucket : buckets) {
            actions.add(() -> remove(bucket));
        }
        try {
            assertAll("回收临时存储桶", actions);
        } finally {
            buckets.clear();
        }
    }

    /** 创建随机桶，按需开启版本控制。 */
    private String create(boolean versioned) {
        String bucket = "ut-check-" + UUID.randomUUID().toString().substring(0, 18);
        try {
            client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            buckets.add(bucket);
            if (versioned) {
                client().setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
                    .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null)).build());
            }
            return bucket;
        } catch (Exception failure) {
            throw new IllegalStateException("创建本机 MinIO 临时存储桶失败", failure);
        }
    }

    /** 删除桶内全部版本与删除标记后删除桶。 */
    private void remove(String bucket) throws Exception {
        if (!client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            return;
        }
        for (Result<Item> result : client().listObjects(
            ListObjectsArgs.builder().bucket(bucket).recursive(true).includeVersions(true).build())) {
            Item item = result.get();
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(item.objectName())
                .versionId(item.versionId()).build());
        }
        client().removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
    }

    /** 管理员 SDK 客户端，只信任本机自签名证书。 */
    private synchronized MinioClient client() {
        if (root == null) {
            StorageTrust trust = StorageTrust.fromPemText(objects.certificatePem());
            root = MinioClient.builder().endpoint(objects.endpoint()).region(objects.region())
                .credentials(rootUser(), rootPassword())
                .httpClient(new OkHttpClient.Builder().sslSocketFactory(trust.context().getSocketFactory(),
                    trust.trustManager()).connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(10))
                    .build())
                .build();
        }
        return root;
    }
}
