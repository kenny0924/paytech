package com.paytech.platform.support.filedocument.domain.file;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileDescription;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import lombok.Getter;
import lombok.Setter;

/** 按文件 ID 查询的元数据：文件对象、当前版本与上传记录的组合，不含存储坐标。 */
@Getter
@Setter
public class StoredFileView {

    /** 文件 ID。 */
    private Long fileId;

    /** 来源系统。 */
    private String sourceSystem;

    /** 来源系统声明的上传原因。 */
    private String reasonCode;

    /** 当前版本上传时清理后的展示名；非上传来源为空。 */
    private String displayName;

    /** 服务端识别的媒体类型。 */
    private String mimeType;

    /** 实际字节数。 */
    private Long sizeBytes;

    /** 实际字节 SHA-256 小写十六进制。 */
    private String contentHash;

    /** 扫描状态。 */
    private FileScanStatus scanStatus;

    /** 对象状态。 */
    private FileObjectStatus objectStatus;

    /** 首次上传的上传人账号；纯系统上传为空。 */
    private Long uploaderId;

    /** 文件登记时间（UTC）。 */
    private Instant createTime;

    /** 访问级别。 */
    private FileAccessLevel accessLevel;

    /** 当前对外提供的版本号。 */
    private Integer versionNo;

    /** 检查中的替换版本号；没有为空。 */
    private Integer pendingVersionNo;

    /** 长期公开地址；只有 PUBLIC 且当前版本 AVAILABLE 时非空。 */
    private String publicUrl;

    /** 带版本参数的公开地址；与 publicUrl 同时为空或非空。 */
    private String versionedUrl;

    /** 展示名可能含个人信息。 */
    @Override
    public String toString() {
        return "StoredFileView[" + fileId + "]";
    }

    /**
     * 由文件对象、当前版本、上传与存储桶的组合读取构造元数据；公开地址只在 PUBLIC 且当前版本 AVAILABLE 时计算
     *
     * @param row 组合读取的一行
     * @return 不含存储坐标的元数据
     */
    public static StoredFileView of(FileDescription row) {
        StoredFileView view = new StoredFileView();
        view.setFileId(row.getFileId());
        view.setSourceSystem(row.getSourceSystem());
        view.setReasonCode(row.getReasonCode());
        view.setDisplayName(row.getDisplayName());
        view.setMimeType(row.getMimeType());
        view.setSizeBytes(row.getSizeBytes());
        view.setContentHash(row.getContentHash());
        view.setScanStatus(row.getScanStatus());
        view.setObjectStatus(row.getObjectStatus());
        view.setUploaderId(row.getUploaderId());
        view.setCreateTime(row.getCreateTime());
        view.setAccessLevel(row.getAccessLevel());
        view.setVersionNo(row.getCurrentVersionNo());
        view.setPendingVersionNo(row.getPendingVersionNo());
        String publicUrl = PublicFileUrl.of(row.getAccessLevel(), row.getScanStatus(), row.getPublicBaseUrl(),
            row.getObjectRef());
        view.setPublicUrl(publicUrl);
        view.setVersionedUrl(PublicFileUrl.versioned(publicUrl, row.getCurrentVersionNo()));
        return view;
    }
}
