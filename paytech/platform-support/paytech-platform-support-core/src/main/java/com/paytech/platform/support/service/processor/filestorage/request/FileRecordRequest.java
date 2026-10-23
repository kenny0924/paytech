package com.paytech.platform.support.service.processor.filestorage.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件记录详情查询或版本下载。 */
@Getter
@AllArgsConstructor
public class FileRecordRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 文件 ID。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final long fileId;

    /** 下载的版本号；详情查询与下载当前版本为空。 */
    @Positive(message = "版本号必须为正整数")
    private final Integer versionNo;

    /** 请求 ID，写入下载审计；无请求上下文时为 UNKNOWN。 */
    @NotBlank(message = "请求 ID 必填")
    private final String requestId;
}
