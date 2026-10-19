package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 条款版本编辑页聚合的内部请求。 */
@Getter
@AllArgsConstructor
public class CommercialTermWorkspaceRequest {

    /**
     * 条款版本 ID
     * <li>入口已按正十进制格式校验。</li>
     */
    @NotBlank(message = "版本 ID 不能为空")
    private final String versionId;
}
