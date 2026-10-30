package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 税务查询页按订单号读取的内部请求。 */
@Getter
@AllArgsConstructor
public class GetOrderTaxRequest {

    /**
     * 入站边界已校验的操作人身份
     * <li>只用于读取 Client 与 Operating Entity 名称（client-master 要求操作人）；tax 查询不携带。</li>
     */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 公开订单号
     * <li>入口已按 tax 合同格式校验。</li>
     */
    @NotBlank(message = "订单号不能为空")
    private final String orderNo;
}
