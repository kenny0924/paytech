package com.paytech.account.api.response;

import lombok.Getter;
import lombok.Setter;

/** 一个 Owner 类型的非零余额账户数。 */
@Getter
@Setter
public class OwnerBalanceCountResponseBody {
    /** 核算对象类型代码。 */
    private String ownerType;

    /** 净余额非零的余额账户数。 */
    private String count;
}
