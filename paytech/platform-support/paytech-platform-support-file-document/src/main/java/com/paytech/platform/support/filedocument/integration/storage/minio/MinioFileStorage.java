package com.paytech.platform.support.filedocument.integration.storage.minio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.filedocument.domain.file.FileModels.File;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.integration.storage.*;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import io.minio.messages.Item;

/**
 * MinIO 入驻文件存储适配器。
 * <li>存储桶必须开启版本控制和服务端加密；上传授权只允许对象不存在时写入</li>
 * <li>完成读取要求对象恰好只有一个版本，后续读取一律使用该版本</li>
 * <li>SDK 异常统一转为依赖不可用并保留原因，URL 与密钥不进入日志</li>
 */
public final class MinioFileStorage
    implements UploadStorage, CompletedUploadStorage, FileDownloadStorage, StoredFileReader {
    /** 配置与持久化使用的提供方代码。 */
    public static final String PROVIDER = "MINIO";
    /** S3 预签名的最长有效期。 */
    static final long MAX_PRESIGN_SECONDS = Duration.ofDays(7).toSeconds();
    private static final String SSE_HEADER = "x-amz-server-side-encryption";
    private static final String SSE_KMS_KEY_HEADER = "x-amz-server-side-encryption-aws-kms-key-id";
    private final Clock clock;
    private final MinioClient client;

    /** 由装配配置注入已校验的客户端。 */
    public MinioFileStorage(Clock clock, MinioClient client) {
        this.clock = clock;
        this.client = client;
    }

    /** 固定为 MINIO。 */
    @Override
    public String providerCode() {
        return PROVIDER;
    }

    /**
     * 签发浏览器直传的预签名 PUT，有效期截止于上传期限之前。
     *
     * @param upload
     *            已固定的上传目标
     * @return 需原样携带 Content-Type 与 If-None-Match 的授权
     */
    @Override
    public Grant authorize(Upload upload) {
        Instant now = clock.instant();
        long seconds = presignSeconds(now, upload.expiresAt());
        Map<String, String> headers = Map.of("Content-Type", upload.expectedMime(), "If-None-Match", "*");
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
        String url = call(() -> client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
            .bucket(file.container()).object(file.objectRef()).versionId(file.verified().objectVersion())
            .expiry((int)seconds).extraQueryParams(Map.of("response-content-disposition", "attachment")).build()));
        return new Download(url, now.plusSeconds(seconds));
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
