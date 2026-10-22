package com.paytech.platform.support.filedocument.domain.file;
import java.time.Instant;
import java.util.List;
import com.paytech.platform.support.filedocument.domain.enums.*;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileObjectStatus;
/** 文件模块发布的不可变事实，不发布持久化对象。 */
public final class FileModels {
    /** 仅作为模型容器。 */
    private FileModels() {}
    /**
     * @param objectVersion 已固定且不可改写的版本
     * @param contentHash 实际字节 SHA-256
     * @param sizeBytes 实际字节数
     * @param mimeType 服务端识别的 MIME
     * @param encryptionKeyRef 存储加密保护引用
     */
    public record Verified(String objectVersion,String contentHash,long sizeBytes,String mimeType,String encryptionKeyRef) {
        /** 不打印存储保护坐标。 */
        @Override public String toString() { return "Verified[REDACTED]"; }
    }
    /**
     * @param fileId 文件身份
     * @param provider 原存储适配器
     * @param container 原容器
     * @param objectRef 原对象
     * @param verified 固定字节证明
     * @param policyId 原策略
     * @param scanStatus 扫描状态
     * @param objectStatus 对象状态
     */
    public record File(long fileId,String provider,String container,String objectRef,Verified verified,long policyId,FileScanStatus scanStatus,FileObjectStatus objectStatus) {
        /** 不打印存储坐标。 */
        @Override public String toString() { return "File["+fileId+"]"; }
    }
    /**
     * @param id 要求身份
     * @param scannerCode 适配器
     * @param type 检查类别
     * @param minimumVersion 最低运行版本
     * @param acceptedCodes 明确接受代码
     */
    public record Requirement(long id,String scannerCode,FileScanType type,String minimumVersion,List<String> acceptedCodes) {}
    /**
     * @param file 固定文件
     * @param pending 尚未完成的要求
     */
    public record Scan(File file,List<Requirement> pending) {}
    /**
     * @param fileId 文件身份
     * @param requirementId 原要求
     * @param attemptNo 稳定尝试
     * @param objectVersion 准确版本
     * @param contentHash 实际摘要
     * @param runtimeVersion 扫描器运行版本
     * @param resultCode 明确终局结果
     * @param scannedAt 扫描完成时间
     */
    public record Result(long fileId,long requirementId,int attemptNo,String objectVersion,String contentHash,String runtimeVersion,String resultCode,Instant scannedAt) {
        /** 对象版本与摘要不进入普通日志。 */
        @Override public String toString() { return "Result[REDACTED]"; }
    }
}
