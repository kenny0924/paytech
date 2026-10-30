package com.paytech.payout.api.beneficiary.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Owner 的收款账户列表（P2）；按币种、创建时间升序。 */
@Getter
@Setter
public class BeneficiaryAccountListResponseBody {
    /** 收款账户，不含事件时间线 */
    private List<BeneficiaryAccountResponseBody> items;
}
