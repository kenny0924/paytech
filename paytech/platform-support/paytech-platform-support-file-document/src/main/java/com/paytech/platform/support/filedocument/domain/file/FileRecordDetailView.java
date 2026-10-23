package com.paytech.platform.support.filedocument.domain.file;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.core.enums.FileVersionStatus;
import com.paytech.platform.support.filedocument.domain.enums.FileUploadStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 文件记录详情：文件、存储位置（不含凭据）、公开地址、版本历史、检查结果与上传记录的组合。 */
@Getter
@AllArgsConstructor
public class FileRecordDetailView {

    /** 文件与当前版本。 */
    private final FileRecordView file;

    /** 当前版本的 SHA-256。 */
    private final String contentHash;

    /** 首次上传的上传人；纯系统上传为空。 */
    private final Long uploaderId;

    /** 存储实例代码，即记录上的 storage_provider。 */
    private final String instanceCode;

    /** 存储桶名称。 */
    private final String bucketName;

    /** 对象名。 */
    private final String objectRef;

    /** 长期公开地址；只有 PUBLIC 且当前版本 AVAILABLE 时非空。 */
    private final String publicUrl;

    /** 带版本参数的公开地址。 */
    private final String versionedUrl;

    /** 版本历史，按版本号倒序。 */
    private final List<Version> versions;

    /** 检查结果，按版本号倒序、同版本按检查时间倒序。 */
    private final List<ScanResult> scanResults;

    /** 上传记录（首次上传与全部替换），按创建时间倒序。 */
    private final List<Upload> uploads;

    /** 一个版本与产生它的上传。 */
    @Getter
    @Setter
    public static class Version {

        /** 版本号。 */
        private Integer versionNo;

        /** 版本状态。 */
        private FileVersionStatus versionStatus;

        /** 扫描状态。 */
        private FileScanStatus scanStatus;

        /** 上传时的展示名；非上传来源为空。 */
        private String displayName;

        /** 媒体类型。 */
        private String mimeType;

        /** 字节数。 */
        private Long sizeBytes;

        /** SHA-256。 */
        private String contentHash;

        /** 产生该版本的上传 ID；非上传来源为空。 */
        private Long uploadId;

        /** 上传人；纯系统上传为空。 */
        private Long uploaderId;

        /** 检查通过时间；未通过为空。 */
        private Instant availableTime;

        /** 版本登记时间。 */
        private Instant createTime;

        /** CURRENT 或 SUPERSEDED 且检查 AVAILABLE 时可下载。 */
        public boolean downloadable() {
            boolean served = versionStatus == FileVersionStatus.CURRENT || versionStatus == FileVersionStatus.SUPERSEDED;
            return served && scanStatus == FileScanStatus.AVAILABLE;
        }

        /** 展示名可能含个人信息。 */
        @Override
        public String toString() {
            return "Version[" + versionNo + "]";
        }
    }

    /** 一条检查结果及其冻结的策略修订与检查要求。 */
    @Getter
    @Setter
    public static class ScanResult {

        /** 被检查的版本号。 */
        private Integer versionNo;

        /** 策略代码。 */
        private String scanPolicyCode;

        /** 策略修订号。 */
        private Integer revisionNo;

        /** 检查器代码。 */
        private String scannerCode;

        /** 检查类型 Code。 */
        private String scanType;

        /** 结果代码。 */
        private String resultCode;

        /** 尝试序号。 */
        private Integer attemptNo;

        /** 检查完成时间。 */
        private Instant scannedTime;
    }

    /** 一条上传记录。 */
    @Getter
    @Setter
    public static class Upload {

        /** 上传 ID。 */
        private Long uploadId;

        /** 被替换的文件；首次上传为空。 */
        private Long replacesFileId;

        /** 替换序号；首次上传为 0。 */
        private Integer replacementNo;

        /** 上传状态。 */
        private FileUploadStatus status;

        /** 声明的展示名。 */
        private String displayName;

        /** 声明的媒体类型。 */
        private String expectedMime;

        /** 声明的字节数。 */
        private Long expectedSize;

        /** 上传人；纯系统上传为空。 */
        private Long uploaderId;

        /** 完成后产生的版本号；未完成为空。 */
        private Integer resultVersionNo;

        /** 上传到期时间。 */
        private Instant expiresTime;

        /** 创建时间。 */
        private Instant createTime;

        /** 最后更新时间。 */
        private Instant updateTime;

        /** 展示名可能含个人信息。 */
        @Override
        public String toString() {
            return "Upload[" + uploadId + "]";
        }
    }
}
