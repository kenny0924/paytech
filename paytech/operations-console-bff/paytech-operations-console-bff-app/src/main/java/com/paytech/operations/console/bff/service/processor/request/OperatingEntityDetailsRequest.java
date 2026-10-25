package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** B2 经营主体详情页的内部请求。 */
@Getter
@AllArgsConstructor
public class OperatingEntityDetailsRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * OE 编号
     * <li>入口已按两位字母加三位数字校验。</li>
     */
    @NotBlank(message = "OE 编号不能为空")
    private final String entityNo;
}
