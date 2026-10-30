package com.paytech.payout.service.processor.response;

import com.paytech.payout.domain.BeneficiaryAccountView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 提交结果：账户视图，以及是否新建（同内容重复提交返回原账户时为 false）。 */
@Getter
@AllArgsConstructor
public class SubmitBeneficiaryAccountResponse {
    /** 账户视图 */
    private final BeneficiaryAccountView view;

    /** 本次是否新建 */
    private final boolean created;
}
