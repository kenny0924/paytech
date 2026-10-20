package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 商务变更历史的内部请求；入口已校验聚合根类型、ID 格式与分页。 */
@Getter
@AllArgsConstructor
public class CommercialBusinessChangeRequest {

    /** 聚合根类型：AGREEMENT、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE */
    @NotBlank(message = "聚合根类型不能为空")
    private final String aggregateRootType;

    /** 聚合根 ID */
    @NotBlank(message = "聚合根 ID 不能为空")
    private final String aggregateRootId;

    /** 页码，从 1 开始 */
    private final int page;

    /** 每页条数 */
    private final int size;
}
