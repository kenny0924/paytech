package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Owner 收款账户列表的内部请求。 */
@Getter
@AllArgsConstructor
public class PayoutOwnerAccountsRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** Owner 类型：CLIENT、LOCAL_PARTNER */
    @NotBlank(message = "Owner 类型不能为空")
    private final String ownerType;

    /** Owner 标识 */
    @NotBlank(message = "Owner 标识不能为空")
    private final String ownerId;

    /** 可选状态过滤 */
    private final String status;

    /** 可选币种过滤 */
    private final String currency;
}
