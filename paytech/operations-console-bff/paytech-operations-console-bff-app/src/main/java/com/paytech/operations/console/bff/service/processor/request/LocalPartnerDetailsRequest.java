package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** B5 Local Partner 详情页的内部请求。 */
@Getter
@AllArgsConstructor
public class LocalPartnerDetailsRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * Local Partner ID
     * <li>入口已按正整数格式校验。</li>
     */
    @NotBlank(message = "Local Partner ID 不能为空")
    private final String localPartnerId;
}
