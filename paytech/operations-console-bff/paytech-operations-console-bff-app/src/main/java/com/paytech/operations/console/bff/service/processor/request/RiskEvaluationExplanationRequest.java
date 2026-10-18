package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 风控评估解释页的内部请求。 */
@Getter
@AllArgsConstructor
public class RiskEvaluationExplanationRequest {

    /**
     * 入站边界已校验的操作人身份
     * <li>只用于读取规则集版本详情；评估解释本身是内部直连查询。</li>
     */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 评估 ID
     * <li>入口已按正整数格式校验；Risk 回执中的评估 ID 必须与之一致。</li>
     */
    @NotBlank(message = "评估 ID 不能为空")
    private final String evaluationId;
}
