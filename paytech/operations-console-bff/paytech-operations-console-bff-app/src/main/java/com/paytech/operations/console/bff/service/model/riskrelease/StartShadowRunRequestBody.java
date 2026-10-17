package com.paytech.operations.console.bff.service.model.riskrelease;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * 发起影子运行：回放当前生效发布最近的成功正式评估。
 */
@Getter
@Setter
public class StartShadowRunRequestBody {
    /**
     * 目标样本数
     * <li>1..2000，页面默认 200。</li>
     */
    @NotNull(message = "样本数必填")
    @Min(value = 1, message = "样本数至少为 1")
    @Max(value = 2000, message = "样本数不超过 2000")
    private Integer sampleTarget;
}
