package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审计事件详情页的内部请求。 */
@Getter
@AllArgsConstructor
public class AuditEventDetailsRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 审计事件 ID
     * <li>Platform 回执中的事件 ID 必须与之一致。</li>
     */
    @NotNull(message = "审计事件 ID 不能为空")
    private final String auditEventId;
}
