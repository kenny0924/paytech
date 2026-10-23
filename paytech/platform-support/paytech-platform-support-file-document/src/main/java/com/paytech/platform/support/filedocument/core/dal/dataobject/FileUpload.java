package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;

/**
 * Upload 的不可变创建事实及当前状态。
 * @param uploadId 上传身份
 * @param tenantId 原租户；内部文件接口的上传为空
 * @param uploaderId 原账号；纯系统上传为空
 * @param sourceSystem 来源系统
 * @param reasonCode 来源系统声明的上传原因
 * @param displayName 清理后的展示名
 * @param expectedSize 声明字节数
 * @param expectedMime 声明 MIME
 * @param storageProvider 受控适配器代码
 * @param containerRef 原容器引用
 * @param objectRef 高熵对象引用
 * @param scanPolicyId 冻结扫描策略
 * @param expiresAt 原 UTC 到期时间
 * @param status 当前受控上传状态
 * @param accessLevel 访问级别；替换沿用原文件
 * @param replacesFileId 被替换内容的文件；首次上传为空
 * @param replacementNo 替换序号；首次上传为 0
 * @param baseObjectVersion 开始替换时存储中该对象的最新版本；首次上传为空
 */
public record FileUpload(long uploadId, Long tenantId, Long uploaderId, String sourceSystem, String reasonCode,
    String displayName,
    long expectedSize, String expectedMime, String storageProvider, String containerRef, String objectRef,
    long scanPolicyId, Instant expiresAt, FileUploadStatus status, FileAccessLevel accessLevel, Long replacesFileId,
    int replacementNo, String baseObjectVersion) {}
