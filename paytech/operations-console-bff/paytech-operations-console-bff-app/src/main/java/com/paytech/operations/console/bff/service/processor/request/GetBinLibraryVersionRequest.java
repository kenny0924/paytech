package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** BIN 库版本详情的内部请求。 */
@Getter
@AllArgsConstructor
public class GetBinLibraryVersionRequest {

    /** 版本号 */
    @NotNull(message = "版本号不能为空")
    @Positive(message = "版本号必须为正数")
    private final Integer versionNo;
}
