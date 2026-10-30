package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账簿入账进度页面聚合的内部请求。 */
@Getter
@AllArgsConstructor
public class PostingProgressRequest {

    /**
     * 账簿主键
     * <li>入口已按正十进制格式校验。</li>
     */
    @NotBlank(message = "账簿不能为空")
    private final String ledgerBookId;
}
