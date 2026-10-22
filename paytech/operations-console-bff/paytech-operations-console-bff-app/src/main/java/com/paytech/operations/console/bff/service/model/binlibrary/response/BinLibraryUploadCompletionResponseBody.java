package com.paytech.operations.console.bff.service.model.binlibrary.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** BIN 库 CSV 上传完成：登记的文件地址与当前扫描状态，页面随后用文件地址创建版本 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BinLibraryUploadCompletionResponseBody {
    /** 上传 ID，十进制字符串 */
    private String uploadId;

    /** 文件 ID，十进制字符串 */
    private String fileId;

    /** 文件地址 /files/v1/{fileId}，创建版本时提交 */
    private String fileAddress;

    /**
     * 当前扫描状态
     * <li>{@code FileScanStatus} Code：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED。</li>
     */
    private String scanStatus;

    /** 文件登记时间，ISO-8601 UTC */
    private String completedAt;
}
