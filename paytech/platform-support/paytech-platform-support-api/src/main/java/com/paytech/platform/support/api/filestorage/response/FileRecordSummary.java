package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件记录列表中的一行。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileRecordSummary {

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

    /** 展示名可能包含个人信息。 */
    @Override
    public String toString() {
        return "FileRecordSummary[" + fileId + "]";
    }
}
