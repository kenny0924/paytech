package com.paytech.commercial.api.term.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 阶梯组件的一档：区间 [lowerBound, upperBound)，rate 与 fixedAmount 恰好一个；PROGRESSIVE 只允许 rate。 */
@Getter
@Setter
public class CommercialTermBandRequestBody {
    /** 包含下界；首档为 0。 */
    @NotBlank(message = "lowerBound 必填")
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "lowerBound 格式不正确")
    private String lowerBound;

    /** 不包含上界；末档为空，其他档等于下一档下界。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "upperBound 格式不正确")
    private String upperBound;

    /** 档位费率，0 至 1。 */
    @Pattern(regexp = "(0|1)(\\.[0-9]{1,10})?", message = "rate 必须为 0 至 1 的十进制字符串")
    private String rate;

    /** 档位固定金额。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "fixedAmount 格式不正确")
    private String fixedAmount;
}
