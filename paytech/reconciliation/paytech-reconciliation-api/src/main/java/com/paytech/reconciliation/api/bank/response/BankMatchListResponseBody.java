package com.paytech.reconciliation.api.bank.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 人工多选匹配建立的已确认匹配，顺序同请求分配。 */
@Getter
@Setter
public class BankMatchListResponseBody {
    /** 已确认的银行匹配 */
    private List<BankMatchResponseBody> items;
}
