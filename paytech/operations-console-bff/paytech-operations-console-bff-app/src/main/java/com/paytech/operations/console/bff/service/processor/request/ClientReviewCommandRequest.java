package com.paytech.operations.console.bff.service.processor.request;

import java.util.function.Supplier;

import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 案件工作台写命令的内部请求：代操作 PayFac 与已绑定请求体的 compliance 调用。 */
@Getter
@AllArgsConstructor
public class ClientReviewCommandRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 代操作的 PayFac（Local Partner ID）
     * <li>为 null 表示平台自营，不核对 Partner。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$", message = "代操作 PayFac 格式无效")
    private final String onBehalfOfPartnerId;

    /** 核对通过后执行的 compliance 写命令（单次调用，不重试） */
    @NotNull(message = "审核命令不能为空")
    private final Supplier<ClientReviewCommandResponseBody> command;
}
