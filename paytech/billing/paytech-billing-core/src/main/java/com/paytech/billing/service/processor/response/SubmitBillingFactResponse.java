package com.paytech.billing.service.processor.response;

import com.paytech.billing.domain.view.BillingFactView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 提交结果：事实详情与本次是否新登记（同来源引用同内容的重复提交为 false）。 */
@Getter
@AllArgsConstructor
public class SubmitBillingFactResponse {
    /** 事实详情。 */
    private final BillingFactView fact;

    /** 本次是否新登记。 */
    private final boolean created;
}
