package com.paytech.payout.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryReviewStage;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分页查询核验队列。 */
@Getter
@AllArgsConstructor
public class ListBeneficiaryReviewsRequest {
    /** 阶段 */
    @NotNull
    private final BeneficiaryReviewStage stage;

    /** 可选 Owner 类型 */
    private final BeneficiaryOwnerType ownerType;

    /** 可选 Owner 标识 */
    private final Long ownerId;

    /** 已校验的分页参数 */
    @NotNull
    private final PagingRequestBody paging;
}
