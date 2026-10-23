package com.paytech.platform.support.filedocument.core.dal.dataobject;
import com.paytech.platform.support.filedocument.domain.enums.*;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
/**
 * 文件表最小投影，仅在 Owner 模块内部使用。
 * @param fileId 文件身份
 * @param provider 原存储提供方
 * @param container 原存储容器
 * @param objectRef 原对象引用
 * @param objectVersion 不可变版本
 * @param contentHash 实际字节摘要
 * @param sizeBytes 实际大小
 * @param mimeType 服务端识别 MIME
 * @param encryptionKeyRef 存储保护引用
 * @param policyId 冻结策略
 * @param scanStatus 扫描状态
 * @param objectStatus 对象状态
 * @param accessLevel 访问级别
 * @param currentVersionNo 当前对外提供的版本号；镜像列均为该版本的值
 * @param sourceSystem 来源系统
 * @param reasonCode 上传原因
 */
public record FileObject(long fileId,String provider,String container,String objectRef,String objectVersion,String contentHash,long sizeBytes,String mimeType,String encryptionKeyRef,long policyId,FileScanStatus scanStatus,FileObjectStatus objectStatus,FileAccessLevel accessLevel,int currentVersionNo,String sourceSystem,String reasonCode) {
    /** 持久化对象不打印存储坐标。 */
    @Override public String toString() { return "FileObject["+fileId+"]"; }
}
