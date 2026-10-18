package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 客户入驻审核详情页的内部请求。 */
@Getter
@AllArgsConstructor
public class ClientReviewDetailsRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 审核任务 ID
     * <li>入口已按正整数格式校验。</li>
     */
    @NotBlank(message = "审核任务 ID 不能为空")
    private final String caseId;
}
