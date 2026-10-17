package com.paytech.operations.console.bff.service.model.riskrelease;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * 以冻结的决策流版本创建执行发布；回滚时传入旧发布的决策流版本。
 */
@Getter
@Setter
public class CreateExecutionReleaseRequestBody {
    /**
     * 决策点编码
     * <li>只有支持运行期的决策点（PAYMENT_ATTEMPT_RISK、CHECKOUT_PREPARE）可创建，其他由 Risk 返回 400。</li>
     */
    @NotBlank(message = "决策点必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "决策点编码格式无效")
    private String pointCode;

    /**
     * 决策流版本 ID
     */
    @NotBlank(message = "决策流版本必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "决策流版本 ID 格式无效")
    private String flowVersionId;
}
