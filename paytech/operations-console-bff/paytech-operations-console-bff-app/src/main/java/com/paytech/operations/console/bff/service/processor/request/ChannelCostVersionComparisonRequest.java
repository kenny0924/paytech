package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 同一渠道成本方案两个版本对比的内部请求。 */
@Getter
@AllArgsConstructor
public class ChannelCostVersionComparisonRequest {

    /**
     * 方案代码
     * <li>入口已按大写代码格式校验。</li>
     */
    @NotBlank(message = "方案代码不能为空")
    private final String planCode;

    /**
     * 基准版本号
     * <li>入口已按正十进制格式校验，且与对比版本不同。</li>
     */
    @NotBlank(message = "基准版本号不能为空")
    private final String baseCostVer;

    /** 对比版本号 */
    @NotBlank(message = "对比版本号不能为空")
    private final String targetCostVer;
}
