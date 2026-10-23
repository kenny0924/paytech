package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件记录详情：元数据、存储位置（不含凭据）、公开地址、版本历史、检查结果与上传记录。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileRecordDetailResponseBody {

    /** 文件 ID，十进制字符串。 */
    private String fileId;

    /** 文件地址 {@code /files/v1/{fileId}}。 */
    private String fileAddress;

    /** 当前版本的展示文件名；非上传来源为空。 */
    private String displayName;

    /** 来源系统。 */
    private String sourceSystem;

    /** 上传原因代码。 */
    private String reasonCode;

    /** 访问级别：PRIVATE 或 PUBLIC。 */
    private String accessLevel;

    /** 当前对外提供的版本号。 */
    private int currentVersionNo;

    /** 检查中的替换版本号；没有为空。 */
    private Integer pendingVersionNo;

    /**
     * 当前版本的扫描状态
     * <li>{@code FileScanStatus} Code：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED。</li>
     */
    private String scanStatus;

    /**
     * 对象状态
     * <li>{@code FileObjectStatus} Code：ACTIVE、UNAVAILABLE、RETIRED。</li>
     */
    private String objectStatus;

    /** 当前版本的媒体类型。 */
    private String mimeType;

    /** 当前版本的字节数，十进制字符串。 */
    private String sizeBytes;

    /** 文件登记时间（UTC）。 */
    private Instant createTime;

    /** 文件最后更新时间（UTC）。 */
    private Instant updateTime;

    /** 当前版本实际字节的 SHA-256 小写十六进制。 */
    private String contentHash;

    /** 首次上传的上传人账号 ID，十进制字符串；纯系统上传为空。 */
    private String uploaderAccountId;

    /** 存储实例代码，即文件记录上的 storage_provider。 */
    private String instanceCode;

    /** 存储桶名称。 */
    private String bucketName;

    /** 对象名（各版本共用）。 */
    private String objectRef;

    /** 长期公开地址；只有 PUBLIC 且当前版本 AVAILABLE 时返回。 */
    private String publicUrl;

    /** 带版本参数的公开地址；与 publicUrl 同时为空或非空。 */
    private String versionedUrl;

    /** 版本历史，按版本号倒序。 */
    private List<FileVersionRecord> versions;

    /** 检查结果，按版本号倒序、同版本按检查时间倒序。 */
    private List<FileScanRecord> scanResults;

    /** 上传记录（含过期、作废的替换），按替换序号倒序（首次上传序号 0 在最后）。 */
    private List<FileUploadRecord> uploads;

    /** 展示名可能包含个人信息。 */
    @Override
    public String toString() {
        return "FileRecordDetailResponseBody[" + fileId + "]";
    }
}
