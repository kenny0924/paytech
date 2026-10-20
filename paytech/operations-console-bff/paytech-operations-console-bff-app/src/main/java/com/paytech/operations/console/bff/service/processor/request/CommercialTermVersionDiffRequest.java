package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 条款版本对比的内部请求。 */
@Getter
@AllArgsConstructor
public class CommercialTermVersionDiffRequest {

    /**
     * 基准版本 ID
     * <li>入口已按正十进制格式校验，且与目标版本不同。</li>
     */
    @NotBlank(message = "基准版本 ID 不能为空")
    private final String baseVersionId;

    /** 目标版本 ID */
    @NotBlank(message = "目标版本 ID 不能为空")
    private final String targetVersionId;
}
