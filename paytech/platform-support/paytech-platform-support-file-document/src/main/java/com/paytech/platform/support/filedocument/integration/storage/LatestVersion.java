package com.paytech.platform.support.filedocument.integration.storage;

import java.util.Map;

/**
 * 存储中某对象最新版本的身份
 *
 * @param versionId 最新版本的 versionId
 * @param restoredFrom 该版本若由 Platform 把旧版本复制为最新版本得到，记录源版本；否则为空
 */
public record LatestVersion(String versionId, String restoredFrom) {

    /** Platform 恢复复制时写入的用户元数据键（x-amz-meta-platform-restored-from）。 */
    public static final String RESTORED_FROM_METADATA = "platform-restored-from";

    /**
     * 最新版本是否对外提供指定版本的内容：即为该版本，或是 Platform 从该版本复制得到的恢复版本
     *
     * @param objectVersion 文件当前版本的 versionId
     */
    public boolean serves(String objectVersion) {
        return objectVersion.equals(versionId) || objectVersion.equals(restoredFrom);
    }

    /**
     * 从 SDK 返回的用户元数据中取源版本；键名按大小写不敏感匹配（各 SDK 对元数据键的大小写处理不同）
     *
     * @param versionId 最新版本的 versionId
     * @param userMetadata 不含 x-amz-meta- 前缀的用户元数据
     * @return 最新版本身份
     */
    public static LatestVersion of(String versionId, Map<String, String> userMetadata) {
        String restoredFrom = userMetadata.entrySet().stream()
            .filter(entry -> RESTORED_FROM_METADATA.equalsIgnoreCase(entry.getKey())).map(Map.Entry::getValue)
            .findFirst().orElse(null);
        return new LatestVersion(versionId, restoredFrom);
    }
}
