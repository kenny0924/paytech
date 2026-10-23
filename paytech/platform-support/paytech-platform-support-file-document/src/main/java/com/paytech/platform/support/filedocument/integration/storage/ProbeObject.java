package com.paytech.platform.support.filedocument.integration.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import com.paytech.platform.support.api.enums.FileStorageCheckResult;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

/**
 * 存储桶启用检查使用的探测对象：高熵 Key 与可比对的内容
 * <li>Key 位于 {@code .platform-readiness/} 前缀下，不与业务对象冲突。</li>
 *
 * @param key 对象 Key
 * @param bytes 写入内容
 */
public record ProbeObject(String key, byte[] bytes) {

    /** 探测对象前缀。 */
    public static final String PREFIX = ".platform-readiness/";

    /** 表示凭据缺少权限或无效的存储错误码。 */
    private static final Set<String> ACCESS_DENIED = Set.of("AccessDenied", "AllAccessDisabled", "InvalidAccessKeyId",
        "SignatureDoesNotMatch", "Forbidden");

    /** 生成一个新的探测对象。 */
    public static ProbeObject next() {
        String id = UUID.randomUUID().toString();
        return new ProbeObject(PREFIX + id, ("platform readiness probe " + id).getBytes(StandardCharsets.US_ASCII));
    }

    /** 读回的内容与写入一致。 */
    public boolean matches(byte[] actual) {
        return Arrays.equals(bytes, actual);
    }

    /**
     * 匿名读取探测对象的检查结论：私有桶必须拒绝（401 / 403），公开桶必须 200 且字节一致
     *
     * @param level 存储桶访问级别
     * @param response 匿名读取结果；连接或读取失败为空
     * @return 通过为空，否则为失败结果
     */
    public FileStorageCheckResult anonymousResult(FileAccessLevel level, AnonymousHttp.Response response) {
        if (level == FileAccessLevel.PUBLIC) {
            boolean readable = response != null && response.status() == 200 && matches(response.body());
            return readable ? null : FileStorageCheckResult.PUBLIC_READ_FAILED;
        }
        if (response == null) {
            return FileStorageCheckResult.UNAVAILABLE;
        }
        if (response.status() == 401 || response.status() == 403) {
            return null;
        }
        return response.status() >= 200 && response.status() < 300 ? FileStorageCheckResult.PUBLIC_ACCESS_ALLOWED
            : FileStorageCheckResult.UNAVAILABLE;
    }

    /** 把存储返回的错误码归类为检查结果；未知错误码为不可用。 */
    public static FileStorageCheckResult classify(String errorCode) {
        if ("NoSuchBucket".equals(errorCode)) {
            return FileStorageCheckResult.NOT_FOUND;
        }
        return ACCESS_DENIED.contains(errorCode) ? FileStorageCheckResult.ACCESS_DENIED
            : FileStorageCheckResult.UNAVAILABLE;
    }

    /** 不输出内容。 */
    @Override
    public String toString() {
        return "ProbeObject[" + key + "]";
    }
}
