package com.paytech.platform.support.filedocument.integration.storage.s3;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.net.ssl.TrustManager;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileStorageInstance;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.integration.storage.*;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * AWS S3（及兼容 S3 的存储）适配器：一个存储实例在一次操作中的客户端。
 * <li>存储桶必须开启版本控制和默认服务端加密（推荐 SSE-KMS）；上传授权签入 Content-Type 与 If-None-Match</li>
 * <li>完成读取要求对象恰好只有一个版本，后续读取一律使用该版本</li>
 * <li>客户端由实例快照与本次凭据构建：JDK 连接、有界超时；STATIC 使用本次密钥，IAM_ROLE 使用默认凭据链</li>
 * <li>SDK 异常统一转为依赖不可用并保留原因，URL 与凭据不进入日志</li>
 */
public final class S3FileStorage implements FileObjectStorage {
    /** S3 预签名的最长有效期。 */
    static final long MAX_PRESIGN_SECONDS = Duration.ofDays(7).toSeconds();
    private final Clock clock;
    private final String instanceCode;
    private final S3Client client;
    private final S3Presigner presigner;

    /**
     * 按实例构建本次使用的客户端与签名器
     *
     * @param clock 应用时钟
     * @param instance 实例快照
     * @param credential 本次从 Vault 读取的静态密钥；IAM_ROLE 为空，使用默认凭据链
     */
    public S3FileStorage(Clock clock, FileStorageInstance instance, StorageCredential credential) {
        this.clock = clock;
        this.instanceCode = instance.getInstanceCode();
        AwsCredentialsProvider credentials = credentials(credential);
        Region region = Region.of(instance.getRegion());
        boolean pathStyle = Boolean.TRUE.equals(instance.getPathStyleAccess());
        var clientBuilder = S3Client.builder().region(region).credentialsProvider(credentials)
            .httpClient(http(instance)).forcePathStyle(pathStyle);
        var presignerBuilder = S3Presigner.builder().region(region).credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        if (instance.getEndpoint() != null) {
            clientBuilder.endpointOverride(URI.create(instance.getEndpoint()));
            presignerBuilder.endpointOverride(URI.create(instance.getEndpoint()));
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    /** 静态密钥时使用本次密钥，否则走默认凭据链（推荐生产使用 IAM 角色）。 */
    static AwsCredentialsProvider credentials(StorageCredential credential) {
        if (credential == null) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(credential.accessKeyId(), credential.secretAccessKey()));
    }

    /** JDK HttpURLConnection 传输，有界超时；登记了私有证书时只信任该证书。 */
    static SdkHttpClient http(FileStorageInstance instance) {
        var builder = UrlConnectionHttpClient.builder()
            .connectionTimeout(Duration.ofMillis(instance.getConnectTimeoutMs()))
            .socketTimeout(Duration.ofMillis(instance.getReadTimeoutMs()));
        if (instance.getTrustedCertificatePem() != null) {
            StorageTrust trust = StorageTrust.fromPemText(instance.getTrustedCertificatePem());
            builder.tlsTrustManagersProvider(() -> new TrustManager[] {trust.trustManager()});
        }
        return builder.build();
    }

    /** 实例代码。 */
    @Override
    public String providerCode() {
        return instanceCode;
    }

    /**
     * 签发浏览器直传的预签名 PUT；返回的 Header 即签名覆盖的请求头，浏览器必须原样携带。
     *
     * @param upload
     *            已固定的上传目标
     * @return 不晚于上传期限的授权
     */
    @Override
    public Grant authorize(Upload upload) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long seconds = presignSeconds(now, upload.expiresAt());
        PresignedPutObjectRequest presigned =
            call(() -> presigner.presignPutObject(request -> request.signatureDuration(Duration.ofSeconds(seconds))
                .putObjectRequest(PutObjectRequest.builder().bucket(upload.containerRef()).key(upload.objectRef())
                    .contentType(upload.expectedMime()).ifNoneMatch("*").build())));
        Map<String, String> headers = new LinkedHashMap<>();
        presigned.signedHeaders().forEach((name, values) -> {
            if (!"host".equalsIgnoreCase(name)) {
                headers.put(name, String.join(",", values));
            }
        });
        return new Grant(presigned.url().toString(), headers, expiration(presigned.expiration(), now, seconds));
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
        HeadObjectResponse head =
            call(() -> client.headObject(request -> request.bucket(upload.containerRef()).key(upload.objectRef())));
        String version = head.versionId();
        requireSingleVersion(upload.containerRef(), upload.objectRef(), version);
        String encryption = encryption(head);
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
     * 签发原版本的短时只读下载地址，以附件方式返回。
     *
     * @param file
     *            原不可变文件坐标
     * @param expiresAt
     *            本次授权期限
     * @return 不晚于期限的下载授权
     */
    @Override
    public Download authorize(File file, Instant expiresAt) {
        // 到期时间取微秒精度，与审核下载等调用方的期限一致（运营端只接受最多 6 位小数）。
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long seconds = presignSeconds(now, expiresAt);
        PresignedGetObjectRequest presigned =
            call(() -> presigner.presignGetObject(request -> request.signatureDuration(Duration.ofSeconds(seconds))
                .getObjectRequest(GetObjectRequest.builder().bucket(file.container()).key(file.objectRef())
                    .versionId(file.verified().objectVersion()).responseContentDisposition("attachment").build())));
        return new Download(presigned.url().toString(), expiration(presigned.expiration(), now, seconds));
    }

    /** 按登记版本读取原字节。 */
    @Override
    public InputStream open(File file) {
        return read(file.container(), file.objectRef(), file.verified().objectVersion());
    }

    /** 读取指定版本，不回退到 latest。 */
    private InputStream read(String bucket, String key, String version) {
        return call(
            () -> client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).versionId(version).build()));
    }

    /**
     * 依次检查存在、版本控制、写入、默认加密、读取与删除；探测对象在 finally 中尽力删除。
     *
     * @param bucketName 存储桶名称
     * @return 第一项不满足的检查结果
     */
    @Override
    public FileStorageCheckResult checkBucket(String bucketName) {
        try {
            client.headBucket(request -> request.bucket(bucketName));
            BucketVersioningStatus versioning =
                client.getBucketVersioning(request -> request.bucket(bucketName)).status();
            if (versioning != BucketVersioningStatus.ENABLED) {
                return FileStorageCheckResult.VERSIONING_DISABLED;
            }
            return checkProbe(bucketName, ProbeObject.next());
        } catch (S3Exception rejected) {
            return classify(rejected);
        } catch (SdkException | IOException unavailable) {
            return FileStorageCheckResult.UNAVAILABLE;
        }
    }

    /** 写入探测对象后核对加密与内容，最后删除该版本。 */
    FileStorageCheckResult checkProbe(String bucketName, ProbeObject probe) throws IOException {
        String version = client.putObject(request -> request.bucket(bucketName).key(probe.key()),
            RequestBody.fromBytes(probe.bytes())).versionId();
        try {
            HeadObjectResponse head =
                client.headObject(request -> request.bucket(bucketName).key(probe.key()).versionId(version));
            if (head.serverSideEncryptionAsString() == null || head.serverSideEncryptionAsString().isBlank()) {
                return FileStorageCheckResult.ENCRYPTION_DISABLED;
            }
            try (InputStream bytes = client.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(probe.key()).versionId(version).build())) {
                return probe.matches(bytes.readAllBytes()) ? FileStorageCheckResult.OK : FileStorageCheckResult.UNAVAILABLE;
            }
        } finally {
            client.deleteObject(request -> request.bucket(bucketName).key(probe.key()).versionId(version));
        }
    }

    /** HEAD 请求没有错误体，按 HTTP 状态补足错误码后归类。 */
    static FileStorageCheckResult classify(S3Exception rejected) {
        String code = rejected.awsErrorDetails() == null ? null : rejected.awsErrorDetails().errorCode();
        if (rejected.statusCode() == 404 && (code == null || "NoSuchBucket".equals(code) || "NotFound".equals(code))) {
            return FileStorageCheckResult.NOT_FOUND;
        }
        if (rejected.statusCode() == 403) {
            return FileStorageCheckResult.ACCESS_DENIED;
        }
        return ProbeObject.classify(code);
    }

    /** 释放本次构建的客户端与签名器。 */
    @Override
    public void close() {
        client.close();
        presigner.close();
    }

    /** 对象必须恰好存在一个版本且没有删除标记；被重复写入的对象视为不可用。 */
    private void requireSingleVersion(String bucket, String key, String version) {
        if (version == null || version.isBlank() || "null".equalsIgnoreCase(version)) {
            throw new ServiceUnavailableException();
        }
        ListObjectVersionsResponse listing =
            call(() -> client.listObjectVersions(request -> request.bucket(bucket).prefix(key)));
        boolean deleted = listing.deleteMarkers().stream().anyMatch(marker -> key.equals(marker.key()));
        var versions = listing.versions().stream().filter(item -> key.equals(item.key())).toList();
        if (deleted || listing.isTruncated() || versions.size() != 1
            || !version.equals(versions.getFirst().versionId())) {
            throw new ServiceUnavailableException();
        }
    }

    /** 服务端加密是必需证据；KMS 时附带密钥标识。 */
    private static String encryption(HeadObjectResponse head) {
        String algorithm = head.serverSideEncryptionAsString();
        if (algorithm == null || algorithm.isBlank()) {
            throw new ServiceUnavailableException();
        }
        String key = head.ssekmsKeyId();
        return key == null || key.isBlank() ? algorithm : algorithm + ":" + key;
    }

    /**
     * 签名器按自身时钟计算到期；以应用时钟为准取较早者，保证不晚于调用方按同一时钟给出的期限。
     */
    private static Instant expiration(Instant signed, Instant now, long seconds) {
        Instant bounded = now.plusSeconds(seconds);
        return signed.isBefore(bounded) ? signed : bounded;
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

    /** SDK 运行时异常转为依赖不可用并保留原因。 */
    private static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (SdkException exception) {
            throw new ServiceUnavailableException(exception);
        }
    }
}
