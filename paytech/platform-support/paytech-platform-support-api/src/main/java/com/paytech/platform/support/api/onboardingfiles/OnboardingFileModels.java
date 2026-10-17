package com.paytech.platform.support.api.onboardingfiles;

import java.time.Instant;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 入驻受控文件的私网传输合同；临时授权不进入日志。 */
public final class OnboardingFileModels {
    /**
     * @param fileId 原文件
     * @param displayName 上传时固定的展示名
     * @param sizeBytes 实际字节数的十进制字符串
     * @param mimeType 服务端识别的媒体类型
     * @param scanStatus 当前扫描状态
     * @param objectStatus 当前对象状态
     * @param allowedActions 当前允许的动作，执行时仍重新授权
     */
    public record FileResponseBody(String fileId,String displayName,String sizeBytes,String mimeType,
        String scanStatus,
        String objectStatus,
        java.util.List<String> allowedActions) {
        /** 展示名可能含个人信息。 */
        @Override public String toString() { return "FileResponseBody[REDACTED]"; }
    }
    /**
     * @param url 准确版本的短时 GET 授权
     * @param expiresAt 本次授权到期时间
     */
    public record DownloadResponseBody(String url,Instant expiresAt) {
        /** 临时签名 URL 不进入日志。 */
        @Override public String toString() { return "DownloadResponseBody[REDACTED]"; }
    }
    /**
     * @param fileId 文件身份
     * @param uploadId 原上传身份
     * @param contentHash 实际字节摘要
     * @param sizeBytes 实际字节数
     * @param mimeType 服务端识别的媒体类型
     * @param scanStatus 当前扫描状态
     * @param objectStatus 当前对象状态
     * @param checkedAt 本次本库核验时间
     */
    public record VerificationResponseBody(String fileId,String uploadId,String contentHash,String sizeBytes,String mimeType,
        String scanStatus,
        String objectStatus,Instant checkedAt) {
        /** 摘要不作为普通日志内容。 */
        @Override public String toString() { return "VerificationResponseBody[REDACTED]"; }
    }
    /** 仅作为模型容器。 */
    private OnboardingFileModels() {}
    /**
     * 开始一次主体绑定的上传，不接受客户端指定 Tenant、存储坐标或扫描策略。
     * @param applicationId 原申请 ID
     * @param subjectPartyId 已由 CCT 授权的主主体或相关人 ID
     * @param filename 展示名，最多 255 Unicode 字符，服务器清除路径与控制字符
     * @param mimeType 文件声明的规范 MIME，实际字节在完成时独立验证
     * @param sizeBytes 声明字节数的正整数字符串
     */
    public record StartUploadRequestBody(@NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String applicationId,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String subjectPartyId,
        @NotBlank String filename, @NotBlank @Size(max=128) String mimeType,
        @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String sizeBytes) {
        /** 不记录可能包含个人信息的文件名。 */
        @Override public String toString() { return "StartUploadRequestBody[REDACTED]"; }
    }
    /**
     * 对原对象目标签发的临时 PUT 授权；expiresAt 是不可延长的 Upload 到期时间。
     * @param uploadId 稳定上传 ID
     * @param authorization 含临时 URL、必需 Header 与本次授权到期时间
     * @param expiresAt 原上传的 UTC 到期时间
     */
    public record UploadResponseBody(String uploadId, UploadAuthorization authorization, Instant expiresAt) {
        /** 临时授权不可进入普通日志。 */
        @Override public String toString() { return "UploadResponseBody[REDACTED]"; }
    }
    /**
     * @param commandId 完成操作身份，等于 uploadId（一次上传只产生一个文件）；保留字段以兼容既有合同
     * @param uploadId 原上传
     * @param fileId 唯一文件
     * @param completedAt 首次成功时间
     */
    public record FileCompletionReceipt(String commandId,String uploadId,String fileId,Instant completedAt) {}
    /** method 固定 PUT；expiresAt 可早于 Upload 到期，不允许延长原生命周期。 */
    public record UploadAuthorization(String method, String url, Map<String,String> headers, Instant expiresAt) {
        /** 临时 URL 和签名 Header 不可被默认打印。 */
        @Override public String toString() { return "UploadAuthorization[REDACTED]"; }
    }
}
