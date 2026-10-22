package com.paytech.card.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用已上传文件创建 BIN 库版本命令 */
@Getter
@AllArgsConstructor
public class CreateBinLibraryVersionRequest {
    /** 已解析的调用方服务名，只接受 operations-console-bff */
    private final String caller;

    /** 导入人 Operations Account ID */
    @NotNull(message = "操作人必填")
    @Positive(message = "操作人无效")
    private final Long actorAccountId;

    /** Platform 文件地址 /files/v1/{fileId}，即业务防重身份 */
    @NotBlank(message = "文件地址必填")
    private final String fileAddress;

    /** 由文件地址解析的 Platform 文件 ID */
    @NotNull(message = "文件 ID 必填")
    @Positive(message = "文件 ID 无效")
    private final Long fileId;
}
