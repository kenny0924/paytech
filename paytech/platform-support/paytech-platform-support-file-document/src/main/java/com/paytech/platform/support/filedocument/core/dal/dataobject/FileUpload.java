package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;

/**
 * Upload 的不可变创建事实及当前状态。
 * @param uploadId 上传身份
 * @param tenantId 原租户
 * @param uploaderId 原账号
 * @param ownerRefId 申请与主体的固定组合
 * @param displayName 清理后的展示名
 * @param expectedSize 声明字节数
 * @param expectedMime 声明 MIME
 * @param storageProvider 受控适配器代码
 * @param containerRef 原容器引用
 * @param objectRef 高熵对象引用
 * @param scanPolicyId 冻结扫描策略
 * @param expiresAt 原 UTC 到期时间
 * @param status 当前受控上传状态
 */
public record FileUpload(long uploadId, long tenantId, long uploaderId, String ownerRefId, String displayName,
    long expectedSize, String expectedMime, String storageProvider, String containerRef, String objectRef,
    long scanPolicyId, Instant expiresAt, FileUploadStatus status) {}
