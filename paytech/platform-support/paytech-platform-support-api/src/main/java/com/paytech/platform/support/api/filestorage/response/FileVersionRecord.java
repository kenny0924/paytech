package com.paytech.platform.support.api.filestorage.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件的一个版本。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileVersionRecord {

    /** 版本号，从 1 开始。 */
    private int versionNo;

    /**
     * 版本状态
     * <li>CANDIDATE：检查中；CURRENT：当前对外提供；SUPERSEDED：已被新版本取代；REJECTED：检查不通过。</li>
     */
    private String versionStatus;

    /** 该版本的扫描状态。 */
    private String scanStatus;

    /** 该版本上传时的展示文件名；非上传来源为空。 */
    private String displayName;

    /** 该版本的媒体类型。 */
    private String mimeType;

    /** 该版本的字节数，十进制字符串。 */
    private String sizeBytes;

    /** 该版本的 SHA-256 小写十六进制。 */
    private String contentHash;

    /** 产生该版本的上传 ID；非上传来源为空。 */
    private String uploadId;

    /** 该版本的上传人账号 ID；纯系统上传为空。 */
    private String uploaderAccountId;

    /** 是否可下载：CURRENT 或 SUPERSEDED 且检查 AVAILABLE。 */
    private boolean downloadable;

    /** 检查通过时间（UTC）；未通过为空。 */
    private Instant availableTime;

    /** 版本登记时间（UTC）。 */
    private Instant createTime;

    /** 展示名可能包含个人信息。 */
    @Override
    public String toString() {
        return "FileVersionRecord[" + versionNo + "]";
    }
}
