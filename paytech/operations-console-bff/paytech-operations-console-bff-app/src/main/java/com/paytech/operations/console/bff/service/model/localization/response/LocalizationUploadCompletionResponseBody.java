package com.paytech.operations.console.bff.service.model.localization.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 多语言导入文件上传完成：页面随后用文件 ID 调用导入预览或翻译导入 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocalizationUploadCompletionResponseBody {
    /** 上传 ID，十进制字符串 */
    private String uploadId;

    /** 文件 ID，十进制字符串；导入预览、确认与翻译导入提交此值 */
    private String fileId;

    /** 文件地址 /files/v1/{fileId} */
    private String fileAddress;

    /**
     * 当前扫描状态
     * <li>{@code FileScanStatus} Code：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED；未到 AVAILABLE 时导入由 Platform 拒绝。</li>
     */
    private String scanStatus;

    /** 文件登记时间，ISO-8601 UTC */
    private String completedAt;
}
