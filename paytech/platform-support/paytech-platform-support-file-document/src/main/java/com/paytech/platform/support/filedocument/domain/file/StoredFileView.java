package com.paytech.platform.support.filedocument.domain.file;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;

import lombok.Getter;
import lombok.Setter;

/** 按文件 ID 查询的元数据：文件对象与其上传记录的组合，不含存储坐标。 */
@Getter
@Setter
public class StoredFileView {

    /** 文件 ID。 */
    private Long fileId;

    /** 来源系统。 */
    private String sourceSystem;

    /** 来源系统声明的上传原因。 */
    private String reasonCode;

    /** 清理后的展示名；非上传来源为空。 */
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

    /** 上传人账号；纯系统上传为空。 */
    private Long uploaderId;

    /** 文件登记时间（UTC）。 */
    private Instant createTime;

    /** 展示名可能含个人信息。 */
    @Override
    public String toString() {
        return "StoredFileView[" + fileId + "]";
    }
}
