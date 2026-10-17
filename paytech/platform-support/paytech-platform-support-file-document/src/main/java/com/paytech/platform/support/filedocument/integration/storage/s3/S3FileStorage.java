package com.paytech.platform.support.filedocument.integration.storage.s3;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.integration.storage.*;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * AWS S3 入驻文件存储适配器。
 * <li>存储桶必须开启版本控制和默认服务端加密（推荐 SSE-KMS）；上传授权签入 Content-Type 与 If-None-Match</li>
 * <li>完成读取要求对象恰好只有一个版本，后续读取一律使用该版本</li>
 * <li>SDK 异常统一转为依赖不可用并保留原因，URL 与凭据不进入日志</li>
 */
public final class S3FileStorage
    implements UploadStorage, CompletedUploadStorage, FileDownloadStorage, StoredFileReader {
    /** 配置与持久化使用的提供方代码。 */
    public static final String PROVIDER = "S3";
    /** S3 预签名的最长有效期。 */
    static final long MAX_PRESIGN_SECONDS = Duration.ofDays(7).toSeconds();
    private final Clock clock;
    private final S3Client client;
    private final S3Presigner presigner;

    /** 由装配配置注入已校验的客户端与签名器。 */
    public S3FileStorage(Clock clock, S3Client client, S3Presigner presigner) {
        this.clock = clock;
        this.client = client;
        this.presigner = presigner;
    }

    /** 固定为 S3。 */
    @Override
    public String providerCode() {
        return PROVIDER;
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
        Instant now = clock.instant();
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
        Instant now = clock.instant();
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
