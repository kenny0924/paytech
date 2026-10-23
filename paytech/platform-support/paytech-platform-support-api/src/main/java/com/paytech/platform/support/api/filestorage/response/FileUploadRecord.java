package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件的一条上传记录（首次上传或替换）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRecord {

    /** 上传 ID，十进制字符串。 */
    private String uploadId;

    /** 上传类型：ORIGINAL（首次上传）或 REPLACEMENT（替换）。 */
    private String kind;

    /** 替换序号；首次上传为 0。 */
    private int replacementNo;

    /**
     * 上传状态
     * <li>CREATED、UPLOADING、COMPLETED、EXPIRED、CANCELLED；CANCELLED 表示替换核对发现多余写入而作废。</li>
     */
    private String status;

    /** 声明的展示文件名。 */
    private String displayName;

    /** 声明的媒体类型。 */
    private String expectedMime;

    /** 声明的字节数，十进制字符串。 */
    private String expectedSize;

    /** 上传人账号 ID；纯系统上传为空。 */
    private String uploaderAccountId;

    /** 完成后产生的版本号；未完成为空。 */
    private Integer resultVersionNo;

    /** 上传到期时间（UTC）。 */
    private Instant expiresTime;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;

    /** 展示名可能包含个人信息。 */
    @Override
    public String toString() {
        return "FileUploadRecord[" + uploadId + "]";
    }
}
