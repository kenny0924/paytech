package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 新建账户结果。 */
@Getter
@Setter
public class CreateAccountResponseBody {
    /**
     * 新建账户主键
     */
    private String accountId;
}
