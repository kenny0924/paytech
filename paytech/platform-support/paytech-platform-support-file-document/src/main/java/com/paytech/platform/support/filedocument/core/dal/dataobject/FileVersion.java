package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;

import lombok.Getter;
import lombok.Setter;

/** t_file_version 映射对象：文件的一次内容版本；只在 file-document 模块内使用。 */
@Getter
@Setter
public class FileVersion {

    /**
     * 文件版本主键
     * <li>v1 取 file_id 的值，替换版本使用 Snowflake ID。</li>
     */
    private Long versionId;

    /** 所属文件 ID。 */
    private Long fileId;

    /** 版本号，从 1 开始。 */
    private Integer versionNo;

    /** 产生该版本的上传 ID；非上传来源为空。 */
    private Long uploadId;

    /** 存储 versionId；读取固定到该版本。 */
    private String objectVersion;

    /** 实际字节 SHA-256 小写十六进制。 */
    private String contentHash;

    /** 实际字节数。 */
    private Long sizeBytes;

    /** 服务端识别的媒体类型。 */
    private String mimeType;

    /** 存储加密保护引用。 */
    private String storageEncryptionKeyRef;

    /** 冻结的扫描策略修订 ID。 */
    private Long scanPolicyId;

    /** 扫描状态。 */
    private FileScanStatus scanStatus;

    /** 版本状态。 */
    private FileVersionStatus versionStatus;

    /** 检查通过时间；未通过为空。 */
    private Instant availableTime;

    /** 乐观并发版本。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;

    /** 不打印存储版本与摘要。 */
    @Override
    public String toString() {
        return "FileVersion[" + fileId + ":v" + versionNo + "]";
    }
}
