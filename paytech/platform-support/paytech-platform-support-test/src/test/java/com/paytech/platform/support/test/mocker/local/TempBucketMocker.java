package com.paytech.platform.support.test.mocker.local;

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
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.Item;
import io.minio.messages.VersioningConfiguration;
import okhttp3.OkHttpClient;

/**
 * 以本机 MinIO 管理员账号临时创建用于存储桶检查负例的存储桶，并在每例结束删除
 * <li>只创建 ut-check- 前缀的随机桶：不开版本控制，或开版本控制但不配置默认加密；Platform 测试账号对这些桶没有权限。</li>
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
