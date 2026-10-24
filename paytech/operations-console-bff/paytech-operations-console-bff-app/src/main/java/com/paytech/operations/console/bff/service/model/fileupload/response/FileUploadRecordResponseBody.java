package com.paytech.operations.console.bff.service.model.fileupload.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件上传页的一条公开上传：检查状态、上传人与公开地址；只来自本入口（运营控制台后端、OPERATIONS_PUBLIC_ASSET） */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRecordResponseBody {
    /** 文件 ID，十进制字符串 */
    private String fileId;

    /** 文件地址 /files/v1/{fileId} */
    private String fileAddress;

    /** 展示文件名 */
    private String displayName;

    /** Platform 按文件头识别的媒体类型 */
    private String mimeType;

    /** 实际字节数，十进制字符串 */
    private String sizeBytes;

    /**
     * 扫描状态
     * <li>{@code FileScanStatus} Code：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED。</li>
     */
    private String scanStatus;

    /** 当前对外提供的版本号 */
    private Integer versionNo;

    /** 上传人账号 ID，十进制字符串 */
    private String uploaderAccountId;

    /** 上传人是否为当前会话账号 */
    private boolean uploadedByMe;

    /** 文件登记时间（UTC） */
    private Instant createTime;

    /**
     * 长期公开地址
     * <li>只有检查通过（AVAILABLE）时返回，否则为空。</li>
     */
    private String publicUrl;

    /**
     * 带版本参数的公开地址
     * <li>与 publicUrl 同时为空或非空。</li>
     */
    private String versionedUrl;

    /** 展示名可能包含个人信息。 */
    @Override
    public String toString() {
        return "FileUploadRecordResponseBody[" + fileId + "]";
    }
}
