package com.paytech.platform.support.filedocument.domain.file;

import java.time.Instant;
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
     */
    public record Start(Long tenantId, Long uploaderId, String sourceSystem, String reasonCode,
        String displayName, String mimeType, long sizeBytes) {
        /** 文件名可能含个人信息。 */
        @Override public String toString() { return "Start[REDACTED]"; }
    }
    /** 已固定的上传目标；expiry 和扫描策略不得在重放时替换。 */
    public record Upload(long uploadId, Long tenantId, Long uploaderId, String sourceSystem, String reasonCode,
        String displayName,
        long expectedSize, String expectedMime, String storageProvider, String containerRef, String objectRef,
        long scanPolicyId, Instant expiresAt, FileUploadStatus status) {
        /** 不在普通日志中暴露展示名或存储坐标。 */
        @Override public String toString() { return "Upload[" + uploadId + "]"; }
    }
    /** 已完成上传产生的唯一文件及其登记时间。 */
    public record Completed(long fileId, Instant completedAt) {}
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
}
