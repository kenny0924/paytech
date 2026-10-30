package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 收款账户核验队列的内部请求。 */
@Getter
@AllArgsConstructor
public class PayoutReviewQueueRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** 队列阶段：MAKER、CHECKER、DONE */
    @NotBlank(message = "队列阶段不能为空")
    private final String stage;

    /**
     * Owner 类型
     * <li>可空；与 ownerId 同时给出。</li>
     */
    private final String ownerType;

    /**
     * Owner 标识
     * <li>可空；与 ownerType 同时给出。</li>
     */
    private final String ownerId;

    /** 已校验页码 */
    private final int page;

    /** 已校验每页条数 */
    private final int size;
}
