package com.paytech.card.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 激活或回切 BIN 库版本命令 */
@Getter
@AllArgsConstructor
public class ActivateBinLibraryVersionRequest {
    /** 已解析的调用方服务名，只接受 operations-console-bff */
    private final String caller;

    /** 操作人 Operations Account ID */
    @NotNull(message = "操作人必填")
    @Positive(message = "操作人无效")
    private final Long actorAccountId;

    /** 目标版本号 */
    @NotNull(message = "版本号必填")
    @Positive(message = "版本号无效")
    private final Integer versionNo;

    /** 操作人看到的当前生效版本号；没有生效版本时为 null */
    private final Integer expectedActiveVersionNo;
}
