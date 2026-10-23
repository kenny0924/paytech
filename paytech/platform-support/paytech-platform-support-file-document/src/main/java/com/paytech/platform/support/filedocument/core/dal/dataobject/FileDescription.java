package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import lombok.Getter;
import lombok.Setter;

/**
 * 文件元数据的组合读取结果：文件、当前版本的上传、首次上传与所在存储桶的对外基础地址
 * <li>只在 file-document 模块内使用，含对象名与基础地址，由 Service 计算公开地址后转换为不含存储坐标的视图。</li>
 */
@Getter
@Setter
public class FileDescription {

    /** 文件 ID。 */
    private Long fileId;

    /** 来源系统。 */
    private String sourceSystem;

    /** 上传原因代码。 */
    private String reasonCode;

    /** 当前版本上传时的展示名；非上传来源为空。 */
    private String displayName;

    /** 当前版本的媒体类型。 */
    private String mimeType;

    /** 当前版本的字节数。 */
    private Long sizeBytes;

    /** 当前版本的 SHA-256。 */
    private String contentHash;

    /** 当前版本的扫描状态。 */
    private FileScanStatus scanStatus;

    /** 对象状态。 */
    private FileObjectStatus objectStatus;

    /** 首次上传的上传人；纯系统上传为空。 */
    private Long uploaderId;

    /** 文件登记时间。 */
    private Instant createTime;

    /** 文件最后更新时间。 */
    private Instant updateTime;

    /** 访问级别。 */
    private FileAccessLevel accessLevel;

    /** 当前版本号。 */
    private Integer currentVersionNo;

    /** 检查中的替换版本号；没有为空。 */
    private Integer pendingVersionNo;

    /** 存储实例代码（storage_provider）。 */
    private String provider;

    /** 存储桶名称（storage_container_ref）。 */
    private String container;

    /** 对象名。 */
    private String objectRef;

    /** 所在存储桶的对外基础地址；私有桶或桶已不在登记中为空。 */
    private String publicBaseUrl;

    /** 不打印展示名与存储坐标。 */
    @Override
    public String toString() {
        return "FileDescription[" + fileId + "]";
    }
}
