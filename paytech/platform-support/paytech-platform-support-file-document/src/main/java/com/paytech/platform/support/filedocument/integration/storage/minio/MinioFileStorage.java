package com.paytech.platform.support.filedocument.integration.storage.minio;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.DownloadPresentation;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.integration.storage.*;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import io.minio.messages.Item;
import io.minio.messages.VersioningConfiguration;
import okhttp3.OkHttpClient;

/**
 * MinIO 存储适配器：一个存储实例在一次操作中的客户端。
 * <li>存储桶必须开启版本控制和服务端加密；首次上传授权只允许对象不存在时写入，替换上传只签 Content-Type</li>
 * <li>首次完成要求对象恰好只有一个版本，替换完成要求比基准新的版本恰好一个，后续读取一律使用核对过的版本</li>
 * <li>匿名访问检查按路径风格地址（MinIO 总是支持）不带凭据读取</li>
 * <li>客户端由实例快照与本次密钥构建：有界超时、禁止重定向；登记了私有证书时只信任该证书</li>
 * <li>SDK 异常统一转为依赖不可用并保留原因，URL 与密钥不进入日志</li>
 */
public final class MinioFileStorage implements FileObjectStorage {
    /** S3 预签名的最长有效期。 */
    static final long MAX_PRESIGN_SECONDS = Duration.ofDays(7).toSeconds();
    private static final String SSE_HEADER = "x-amz-server-side-encryption";
    private static final String SSE_KMS_KEY_HEADER = "x-amz-server-side-encryption-aws-kms-key-id";
    private final Clock clock;
    private final String instanceCode;
    private final String endpoint;
    private final OkHttpClient http;
    private final MinioClient client;
    private final AnonymousHttp anonymous;

    /**
     * 按实例构建本次使用的客户端
     *
     * @param clock 应用时钟
     * @param instance 实例快照，MINIO 必有地址
     * @param credential 本次从 Vault 读取的静态密钥
     */
    public MinioFileStorage(Clock clock, FileStorageInstance instance, StorageCredential credential) {
        this.clock = clock;
        this.instanceCode = instance.getInstanceCode();
        this.endpoint = instance.getEndpoint();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(instance.getConnectTimeoutMs()))
            .readTimeout(Duration.ofMillis(instance.getReadTimeoutMs()))
            .writeTimeout(Duration.ofMillis(instance.getReadTimeoutMs()))
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false);
        if (instance.getTrustedCertificatePem() != null) {
            StorageTrust trust = StorageTrust.fromPemText(instance.getTrustedCertificatePem());
            builder.sslSocketFactory(trust.context().getSocketFactory(), trust.trustManager());
        }
        this.http = builder.build();
        this.client = MinioClient.builder().endpoint(instance.getEndpoint()).region(instance.getRegion())
            .credentials(credential.accessKeyId(), credential.secretAccessKey()).httpClient(http).build();
        this.anonymous = new AnonymousHttp(instance);
    }

    /** 实例代码。 */
    @Override
    public String providerCode() {
        return instanceCode;
    }

    /**
     * 签发浏览器直传的预签名 PUT，有效期截止于上传期限之前。
     *
     * @param upload
     *            已固定的上传目标
     * @return 需原样携带的请求头：首次上传为 Content-Type 与 If-None-Match，替换上传只有 Content-Type
     */
    @Override
    public Grant authorize(Upload upload) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long seconds = presignSeconds(now, upload.expiresAt());
        Map<String, String> headers = upload.replacement() ? Map.of("Content-Type", upload.expectedMime())
            : Map.of("Content-Type", upload.expectedMime(), "If-None-Match", "*");
        String url = call(() -> client
            .getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(upload.containerRef())
                .object(upload.objectRef()).expiry((int)seconds).extraHeaders(headers).build()));
        return new Grant(url, headers, now.plusSeconds(seconds));
    }

    /**
     * 固定唯一版本并打开字节流；同一上传重复调用总是得到同一版本。
     *
     * @param upload
     *            原上传目标
     * @return 版本、服务端识别类型、加密引用与字节流
     */
    @Override
    public Opened open(Upload upload) {
        StatObjectResponse stat = call(() -> client
            .statObject(StatObjectArgs.builder().bucket(upload.containerRef()).object(upload.objectRef()).build()));
        String version = stat.versionId();
        requireSingleVersion(upload.containerRef(), upload.objectRef(), version);
        return opened(upload, version, stat);
    }

    /**
     * 列出比替换基准新的全部版本（含删除标记），最新在前；找不到基准版本时不可用。
     *
     * @param upload
     *            替换上传
     * @return 新版本 versionId
     */
    @Override
    public List<String> newerVersions(Upload upload) {
        List<String> newer = new ArrayList<>();
        for (Result<Item> result : client.listObjects(ListObjectsArgs.builder().bucket(upload.containerRef())
            .prefix(upload.objectRef()).includeVersions(true).build())) {
            Item item = call(result::get);
            if (!upload.objectRef().equals(item.objectName())) {
                continue;
            }
            if (upload.baseObjectVersion().equals(item.versionId())) {
                return newer;
            }
            newer.add(item.versionId());
        }
        throw new ServiceUnavailableException();
    }

    /**
     * 打开已核对唯一性的替换版本。
     *
     * @param upload
     *            替换上传
     * @param version
     *            准确版本
     * @return 版本、服务端识别类型、加密引用与字节流
     */
    @Override
    public Opened open(Upload upload, String version) {
        StatObjectResponse stat = call(() -> client.statObject(StatObjectArgs.builder().bucket(upload.containerRef())
            .object(upload.objectRef()).versionId(version).build()));
        if (!version.equals(stat.versionId())) {
            throw new ServiceUnavailableException();
        }
        return opened(upload, version, stat);
    }

    /** 核对加密后打开指定版本并识别类型。 */
    private Opened opened(Upload upload, String version, StatObjectResponse stat) {
        String encryption = encryption(stat);
        BufferedInputStream bytes =
            StoredContentType.buffered(read(upload.containerRef(), upload.objectRef(), version));
        try {
            return new Opened(version, StoredContentType.detect(bytes), encryption, bytes);
        } catch (IOException exception) {
            closeQuietly(bytes);
            throw new ServiceUnavailableException(exception);
        }
    }

    /**
     * 签发原版本的短时只读下载地址；签入 response-content-disposition，INLINE 时另签入 response-content-type。
     *
     * @param file
     *            原不可变文件坐标
     * @param expiresAt
     *            本次授权期限
     * @param presentation
     *            下载方式与文件名
     * @return 不晚于期限的下载授权
     */
    @Override
    public Download authorize(File file, Instant expiresAt, DownloadPresentation presentation) {
        // 到期时间取微秒精度，与审核下载等调用方的期限一致（运营端只接受最多 6 位小数）。
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long seconds = presignSeconds(now, expiresAt);
        String url = call(() -> client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
            .bucket(file.container()).object(file.objectRef()).versionId(file.verified().objectVersion())
            .expiry((int)seconds).extraQueryParams(responseParams(presentation)).build()));
        return new Download(url, now.plusSeconds(seconds));
    }

    /** 下载响应头覆盖参数：总是带 Content-Disposition，INLINE 时带 Content-Type。 */
    static Map<String, String> responseParams(DownloadPresentation presentation) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("response-content-disposition", presentation.contentDisposition());
        if (presentation.contentType() != null) {
            params.put("response-content-type", presentation.contentType());
        }
        return params;
    }

    /** 按登记版本读取原字节。 */
    @Override
    public InputStream open(File file) {
        return read(file.container(), file.objectRef(), file.verified().objectVersion());
    }

    /** 读取指定版本，不回退到 latest。 */
    private InputStream read(String bucket, String object, String version) {
        return call(
            () -> client.getObject(GetObjectArgs.builder().bucket(bucket).object(object).versionId(version).build()));
    }

    /**
     * 依次检查存在、版本控制、写入、默认加密、读取与删除；探测对象在 finally 中尽力删除。
     *
     * @param bucketName 存储桶名称
     * @return 第一项不满足的检查结果
     */
    @Override
    public FileStorageCheckResult checkBucket(String bucketName, FileAccessLevel accessLevel, String publicBaseUrl) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                return FileStorageCheckResult.NOT_FOUND;
            }
            VersioningConfiguration versioning =
                client.getBucketVersioning(GetBucketVersioningArgs.builder().bucket(bucketName).build());
            if (versioning.status() != VersioningConfiguration.Status.ENABLED) {
                return FileStorageCheckResult.VERSIONING_DISABLED;
            }
            return checkProbe(bucketName, ProbeObject.next(), accessLevel, publicBaseUrl);
        } catch (ErrorResponseException rejected) {
            return ProbeObject.classify(rejected.errorResponse().code());
        } catch (MinioException | IOException | GeneralSecurityException | RuntimeException unavailable) {
            return FileStorageCheckResult.UNAVAILABLE;
        }
    }

    /** 写入探测对象后核对加密、内容与匿名访问，最后删除该版本。 */
    FileStorageCheckResult checkProbe(String bucketName, ProbeObject probe, FileAccessLevel accessLevel,
        String publicBaseUrl) throws MinioException, IOException, GeneralSecurityException {
        String version = client.putObject(PutObjectArgs.builder().bucket(bucketName).object(probe.key())
            .stream(new ByteArrayInputStream(probe.bytes()), probe.bytes().length, -1).build()).versionId();
        try {
            StatObjectResponse stat = client.statObject(
                StatObjectArgs.builder().bucket(bucketName).object(probe.key()).versionId(version).build());
            String encryption = stat.headers().get(SSE_HEADER);
            if (encryption == null || encryption.isBlank()) {
                return FileStorageCheckResult.ENCRYPTION_DISABLED;
            }
            try (InputStream bytes = client.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(probe.key()).versionId(version).build())) {
                if (!probe.matches(bytes.readAllBytes())) {
                    return FileStorageCheckResult.UNAVAILABLE;
                }
            }
            FileStorageCheckResult anonymousResult = probe.anonymousResult(accessLevel,
                anonymousRead(accessLevel == FileAccessLevel.PUBLIC ? URI.create(publicBaseUrl + "/" + probe.key())
                    : URI.create(endpoint + "/" + bucketName + "/" + probe.key())));
            return anonymousResult == null ? FileStorageCheckResult.OK : anonymousResult;
        } finally {
            client.removeObject(
                RemoveObjectArgs.builder().bucket(bucketName).object(probe.key()).versionId(version).build());
        }
    }

    /** 不带凭据读取；连接或读取失败为空。 */
    private AnonymousHttp.Response anonymousRead(URI uri) {
        try {
            return anonymous.get(uri);
        } catch (IOException unreachable) {
            return null;
        }
    }

    /**
     * 读取对象最新版本及 Platform 恢复复制写入的源版本元数据。
     *
     * @param container
     *            存储桶
     * @param objectRef
     *            对象名
     * @return 最新版本
     */
    @Override
    public LatestVersion latest(String container, String objectRef) {
        StatObjectResponse stat =
            call(() -> client.statObject(StatObjectArgs.builder().bucket(container).object(objectRef).build()));
        if (stat.versionId() == null || stat.versionId().isBlank()) {
            throw new ServiceUnavailableException();
        }
        return LatestVersion.of(stat.versionId(), stat.userMetadata());
    }

    /**
     * 服务端复制当前版本为最新版本：替换元数据，保留当前版本的 Content-Type 并记录源版本；默认加密由桶配置施加。
     *
     * @param current
     *            文件当前版本坐标
     */
    @Override
    public void restore(File current) {
        String source = current.verified().objectVersion();
        call(() -> client.copyObject(CopyObjectArgs.builder().bucket(current.container()).object(current.objectRef())
            .source(CopySource.builder().bucket(current.container()).object(current.objectRef()).versionId(source)
                .build())
            .headers(Map.of("Content-Type", current.verified().mimeType()))
            .userMetadata(Map.of(LatestVersion.RESTORED_FROM_METADATA, source))
            .metadataDirective(Directive.REPLACE).build()));
    }

    /** 释放本次构建的 SDK 客户端、连接池与调度线程。 */
    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception ignored) {
            // 客户端未持有需要释放的独占资源时可忽略
        }
        anonymous.close();
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    /**
     * 对象必须恰好存在一个非删除标记版本，且与当前读取的版本一致；被重复写入的对象视为不可用。
     */
    private void requireSingleVersion(String bucket, String object, String version) {
        if (version == null || version.isBlank() || "null".equalsIgnoreCase(version)) {
            throw new ServiceUnavailableException();
        }
        int count = 0;
        for (Result<Item> result : client
            .listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(object).includeVersions(true).build())) {
            Item item = call(result::get);
            if (!object.equals(item.objectName())) {
                continue;
            }
            if (item.isDeleteMarker() || !version.equals(item.versionId())) {
                throw new ServiceUnavailableException();
            }
            count++;
        }
        if (count != 1) {
            throw new ServiceUnavailableException();
        }
    }

    /** 服务端加密是必需证据；KMS 时附带密钥标识。 */
    private static String encryption(StatObjectResponse stat) {
        String algorithm = stat.headers().get(SSE_HEADER);
        if (algorithm == null || algorithm.isBlank()) {
            throw new ServiceUnavailableException();
        }
        String key = stat.headers().get(SSE_KMS_KEY_HEADER);
        return key == null || key.isBlank() ? algorithm : algorithm + ":" + key;
    }

    /** 有效期向下取整到秒，确保不晚于给定期限。 */
    static long presignSeconds(Instant now, Instant deadline) {
        long seconds = Math.min(Duration.between(now, deadline).toSeconds(), MAX_PRESIGN_SECONDS);
        if (seconds < 1) {
            throw new ServiceUnavailableException();
        }
        return seconds;
    }

    /** 关闭失败不掩盖原异常。 */
    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 原异常已决定结果
        }
    }

    /** SDK 受检异常转为依赖不可用并保留原因。 */
    private static <T> T call(MinioCall<T> call) {
        try {
            return call.execute();
        } catch (MinioException | IOException | GeneralSecurityException exception) {
            throw new ServiceUnavailableException(exception);
        }
    }

    /** MinIO SDK 调用的受检异常形态。 */
    @FunctionalInterface
    interface MinioCall<T> {
        /** 执行一次 SDK 调用。 */
        T execute() throws MinioException, IOException, GeneralSecurityException;
    }
}
