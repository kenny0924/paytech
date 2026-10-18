package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.env.Environment;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.filedocument.integration.storage.StorageTrust;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import okhttp3.OkHttpClient;

/**
 * 仓库 local-environment MinIO 测试存储桶中的对象夹具：以浏览器身份执行预签名请求，并以 Platform 账号直接写入/读取版本。
 *
 * <p>每个对象引用都位于本例独有的随机前缀下；AfterEach 删除这些前缀下的全部版本与删除标记，不触碰其他对象或存储桶。
 * 连接配置复用 application-mock.yml 的 files.onboarding.minio，凭据来自环境变量，不输出密钥。
 */
@TestComponent
public class StorageObjectMocker implements Clear {

    private final Environment environment;
    private final List<String> prefixes = new ArrayList<>();
    private MinioClient client;
    private HttpClient browser;

    /** 只注入配置来源；存储客户端在首次使用时创建，未使用文件能力的上下文不连接 MinIO。 */
    public StorageObjectMocker(Environment environment) {
        this.environment = environment;
    }

    /** 测试专用存储桶。 */
    public String bucket() {
        return environment.getRequiredProperty("files.onboarding.storage-container-ref");
    }

    /** 生成本例独有的对象引用，并登记其随机前缀以便回收。 */
    public synchronized String newObjectRef() {
        String prefix = "unit-test/" + UUID.randomUUID() + "/";
        prefixes.add(prefix);
        return prefix + "material";
    }

    /** 登记由生产链路生成的对象引用（例如上传开始），本例结束时同样回收其全部版本。 */
    public synchronized void track(String objectRef) {
        prefixes.add(objectRef);
    }

    /** 以 Platform 账号直接写入一个新版本（不带条件），返回存储生成的版本号。 */
    public String put(String objectRef, byte[] bytes) {
        try {
            return client().putObject(PutObjectArgs.builder().bucket(bucket()).object(objectRef)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1).build()).versionId();
        } catch (Exception failure) {
            throw new IllegalStateException("写入本机 MinIO 测试对象失败", failure);
        }
    }

    /** 读取对象的全部非删除标记版本号。 */
    public List<String> versions(String objectRef) {
        List<String> versions = new ArrayList<>();
        for (Item item : list(objectRef)) {
            if (objectRef.equals(item.objectName()) && !item.isDeleteMarker()) {
                versions.add(item.versionId());
            }
        }
        return versions;
    }

    /** 以浏览器身份按预签名地址 PUT，原样携带授权返回的请求头。 */
    public HttpResponse<byte[]> browserPut(String url, Map<String, String> headers, byte[] bytes) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        headers.forEach(request::header);
        return send(request.build());
    }

    /** 以浏览器身份按预签名地址 GET。 */
    public HttpResponse<byte[]> browserGet(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    /** 每例开始时没有遗留的对象前缀登记。 */
    public synchronized void assertEmpty() {
        assertTrue(prefixes.isEmpty(), "上一例 MinIO 测试对象未回收");
    }

    /** 删除本例前缀下的全部版本与删除标记；单个前缀失败不阻止其他前缀回收。 */
    @Override
    public synchronized void clear() {
        if (prefixes.isEmpty()) {
            return;
        }
        List<Executable> actions = new ArrayList<>();
        for (String prefix : prefixes) {
            actions.add(() -> removeAllVersions(prefix));
        }
        try {
            assertAll("回收 MinIO 测试对象", actions);
        } finally {
            prefixes.clear();
        }
    }

    /** 逐个版本删除，包括删除标记。 */
    private void removeAllVersions(String prefix) throws Exception {
        for (Item item : list(prefix)) {
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket()).object(item.objectName())
                .versionId(item.versionId()).build());
        }
    }

    /** 列出前缀下的全部版本。 */
    private List<Item> list(String prefix) {
        List<Item> items = new ArrayList<>();
        try {
            for (Result<Item> result : client().listObjects(ListObjectsArgs.builder().bucket(bucket()).prefix(prefix)
                .recursive(true).includeVersions(true).build())) {
                items.add(result.get());
            }
        } catch (Exception failure) {
            throw new IllegalStateException("列出本机 MinIO 测试对象版本失败", failure);
        }
        return items;
    }

    /** 发送浏览器请求；网络失败直接使测试失败。 */
    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return browser().send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException failure) {
            throw new IllegalStateException("访问本机 MinIO 预签名地址失败", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("访问本机 MinIO 预签名地址被中断", interrupted);
        }
    }

    /** Platform 账号的 SDK 客户端，只信任本机自签名证书。 */
    private synchronized MinioClient client() {
        if (client == null) {
            StorageTrust trust = trust();
            client = MinioClient.builder().endpoint(property("endpoint")).region(property("region"))
                .credentials(property("access-key"), property("secret-key"))
                .httpClient(new OkHttpClient.Builder().sslSocketFactory(trust.context().getSocketFactory(),
                    trust.trustManager()).connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(10))
                    .build())
                .build();
        }
        return client;
    }

    /** 浏览器侧 HTTP 客户端：不跟随重定向，只信任本机证书。 */
    private synchronized HttpClient browser() {
        if (browser == null) {
            browser = HttpClient.newBuilder().sslContext(trust().context()).connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        }
        return browser;
    }

    /** 复用生产信任材料读取本机证书。 */
    private StorageTrust trust() {
        return StorageTrust.fromPem(Path.of(property("trusted-certificate")));
    }

    /** 读取 files.onboarding.minio 下的必填连接配置。 */
    private String property(String name) {
        return environment.getRequiredProperty("files.onboarding.minio." + name);
    }
}
