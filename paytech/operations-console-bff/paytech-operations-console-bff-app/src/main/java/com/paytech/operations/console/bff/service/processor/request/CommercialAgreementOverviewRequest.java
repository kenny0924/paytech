package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 合同详情页聚合的内部请求。 */
@Getter
@AllArgsConstructor
public class CommercialAgreementOverviewRequest {

    /**
     * 合同 ID
     * <li>入口已按正十进制格式校验。</li>
     */
    @NotBlank(message = "合同 ID 不能为空")
    private final String agreementId;
}
