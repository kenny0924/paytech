package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建条款方案的内部请求。 */
@Getter
@AllArgsConstructor
public class CreateCommercialTermPlanRequest {
    /** 方案编号 */
    @NotNull
    private final String planCode;

    /** 条款类型 */
    @NotNull
    private final String termType;

    /** 合同 ID */
    @Positive
    private final long agreementId;

    /** Client Owner，可空 */
    private final Long clientId;

    /** Local Partner Owner，可空 */
    private final Long localPartnerId;

    /** 操作人账号 ID */
    @Positive
    private final long operatorAccountId;
}
