package com.paytech.platform.support.workflow.domain.attachment;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;

/**
 * 工作流附件校验与展示所需的文件事实，取自 file-document 按文件 ID 的元数据查询（技术方案第 5 节）
 * <li>只读快照：提交与发起时据此判断归属与内容，展示时给出名称、大小与扫描状态；不含存储坐标。</li>
 *
 * @param fileId 文件 ID
 * @param sourceSystem 来源系统
 * @param reasonCode 上传原因
 * @param uploaderId 上传人账号；纯系统上传为空
 * @param displayName 清理后的展示名
 * @param mimeType 服务端识别的媒体类型
 * @param sizeBytes 实际字节数
 * @param scanStatus 扫描状态
 * @param objectStatus 对象状态
 */
public record AttachmentFact(long fileId, String sourceSystem, String reasonCode, Long uploaderId, String displayName,
    String mimeType, Long sizeBytes, FileScanStatus scanStatus, FileObjectStatus objectStatus) {

    /**
     * 扫描尚未结束（待扫描或扫描中）。
     *
     * @return 未结束返回 true
     */
    public boolean scanning() {
        return scanStatus == FileScanStatus.PENDING_SCAN || scanStatus == FileScanStatus.SCANNING;
    }

    /**
     * 扫描通过且对象可用，可作为验证通过的附件与下载对象。
     *
     * @return 可用返回 true
     */
    public boolean available() {
        return scanStatus == FileScanStatus.AVAILABLE && objectStatus == FileObjectStatus.ACTIVE;
    }

    /** 展示名可能含个人信息。 */
    @Override
    public String toString() {
        return "AttachmentFact[" + fileId + "]";
    }
}
