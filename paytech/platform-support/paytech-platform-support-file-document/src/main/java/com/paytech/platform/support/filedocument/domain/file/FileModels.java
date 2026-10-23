package com.paytech.platform.support.filedocument.domain.file;
import java.time.Instant;
import java.util.List;
import com.paytech.platform.support.filedocument.domain.enums.*;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
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
     * @param scanStatus 该版本的扫描状态
     * @param objectStatus 对象状态
     * @param versionNo verified 所属的版本号
     */
    public record File(long fileId,String provider,String container,String objectRef,Verified verified,long policyId,FileScanStatus scanStatus,FileObjectStatus objectStatus,int versionNo) {
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
    /**
     * 可替换内容的文件：替换上传沿用的身份、位置、当前版本与策略代码
     *
     * @param fileId 文件身份
     * @param sourceSystem 来源系统
     * @param reasonCode 上传原因
     * @param accessLevel 访问级别
     * @param provider 存储实例
     * @param container 存储桶
     * @param objectRef 对象名（各版本共用）
     * @param currentObjectVersion 当前版本的存储 versionId
     * @param policyCode 当前版本冻结策略的代码；替换冻结该代码当前的 ACTIVE 修订
     * @param nextReplacementNo 本次替换的序号
     */
    public record Replaceable(long fileId,String sourceSystem,String reasonCode,FileAccessLevel accessLevel,String provider,String container,String objectRef,String currentObjectVersion,String policyCode,int nextReplacementNo) {
        /** 不打印存储坐标。 */
        @Override public String toString() { return "Replaceable["+fileId+"]"; }
    }
    /**
     * 可下载的文件版本与签入地址的展示名
     *
     * @param file 版本坐标
     * @param displayName 该版本上传时的展示名；非上传来源为空
     */
    public record Downloadable(File file,String displayName) {
        /** 展示名可能含个人信息。 */
        @Override public String toString() { return "Downloadable["+file.fileId()+"]"; }
    }
    /**
     * 公开文件替换结束后的同步判断
     *
     * @param ended 替换已结束：上传授权已到期且本次替换的候选版本已有检查结论
     * @param superseded 之后又开始了新的替换，由新替换的同步任务负责收敛
     * @param current 文件当前版本坐标
     */
    public record PublicSync(boolean ended,boolean superseded,File current) {}
}
