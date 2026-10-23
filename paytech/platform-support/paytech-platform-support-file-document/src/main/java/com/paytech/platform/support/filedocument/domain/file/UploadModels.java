package com.paytech.platform.support.filedocument.domain.file;

import java.time.Instant;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;
import java.util.Map;

/** 上传用例使用的不可变业务事实；不携带临时授权到持久化层。 */
public final class UploadModels {
    /** 仅作为模型命名空间。 */
    private UploadModels() {}
    /**
     * 原请求规范值，名称仅用于展示
     *
     * @param tenantId 入驻上传的租户；内部文件接口为空
     * @param uploaderId 上传人账号；纯系统上传为空
     * @param sourceSystem 来源系统
     * @param reasonCode 来源系统声明的上传原因
     * @param accessLevel 调用方声明的访问级别；路由按该级别选择，不跨级别回落
     */
    public record Start(Long tenantId, Long uploaderId, String sourceSystem, String reasonCode,
        String displayName, String mimeType, long sizeBytes, FileAccessLevel accessLevel) {
        /** 文件名可能含个人信息。 */
        @Override public String toString() { return "Start[REDACTED]"; }
    }
    /**
     * 已固定的上传目标；expiry 和扫描策略不得在重放时替换
     *
     * @param accessLevel 访问级别；替换沿用原文件
     * @param replacesFileId 被替换内容的文件；首次上传为空
     * @param replacementNo 替换序号；首次上传为 0
     * @param baseObjectVersion 开始替换时存储中该对象的最新版本；首次上传为空
     */
    public record Upload(long uploadId, Long tenantId, Long uploaderId, String sourceSystem, String reasonCode,
        String displayName,
        long expectedSize, String expectedMime, String storageProvider, String containerRef, String objectRef,
        long scanPolicyId, Instant expiresAt, FileUploadStatus status, FileAccessLevel accessLevel,
        Long replacesFileId, int replacementNo, String baseObjectVersion) {
        /** 是否为替换已有文件内容的上传。 */
        public boolean replacement() {
            return replacesFileId != null;
        }
        /** 不在普通日志中暴露展示名或存储坐标。 */
        @Override public String toString() { return "Upload[" + uploadId + "]"; }
    }
    /**
     * 已完成上传产生的唯一文件版本及其登记时间
     *
     * @param fileId 文件；替换上传为被替换的文件
     * @param versionNo 本次上传产生的版本号
     * @param completedAt 版本登记时间
     * @param accessLevel 访问级别
     */
    public record Completed(long fileId, int versionNo, Instant completedAt, FileAccessLevel accessLevel) {}
    /** 仅回传给已授权浏览器的短期 PUT 权限；不落库或通用审计。 */
    public record Grant(String url, Map<String, String> headers, Instant expiresAt) {
        /** 冻结适配器输出，避免调用后被更改。 */
        public Grant { headers = Map.copyOf(headers); }
        /** 授权仍在有效期内、不晚于上传到期且地址为 HTTPS。 */
        public boolean usableFor(Upload upload, Instant now) {
            return url != null && expiresAt != null && now.isBefore(expiresAt) && !expiresAt.isAfter(upload.expiresAt())
                && url.startsWith("https://");
        }
        /** 避免 URL 签名进入异常及普通日志。 */
        @Override public String toString() { return "Grant[REDACTED]"; }
    }
    /**
     * 替换上传的请求值；访问级别、原因、位置与策略代码取自原文件
     *
     * @param file 可替换的原文件
     * @param baseObjectVersion 开始替换时存储中该对象的最新版本
     * @param uploaderId 上传人账号；纯系统上传为空
     * @param displayName 新内容的展示名
     * @param mimeType 新内容声明的 MIME
     * @param sizeBytes 新内容声明的字节数
     * @param maxUploadSeconds 上传授权有效秒数
     */
    public record ReplacementStart(FileModels.Replaceable file, String baseObjectVersion, Long uploaderId,
        String displayName, String mimeType, long sizeBytes, int maxUploadSeconds) {
        /** 文件名可能含个人信息。 */
        @Override public String toString() { return "ReplacementStart[" + file.fileId() + "]"; }
    }
}
