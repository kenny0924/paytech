package com.paytech.reconciliation.service.processor.response;

import java.util.List;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 银行匹配命令结果：匹配与其币种（响应金额按币种精度补齐）。 */
@Getter
@AllArgsConstructor
public class BankMatchResponse {
    /** 本次确认、拒绝或新建的匹配 */
    private final List<BankMatch> matches;

    /** 银行账户币种 */
    private final String currency;
}
