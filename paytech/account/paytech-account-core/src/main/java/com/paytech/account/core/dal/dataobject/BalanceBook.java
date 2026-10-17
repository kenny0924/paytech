package com.paytech.account.core.dal.dataobject;

import lombok.Getter;
import lombok.Setter;

/**
 * t_ledger_book 的只读投影：余额查询只需账簿主键与科目表
 * <li>共库过渡：Owner 为 ledger，本服务只读（account_app 在 mor_ledger 只有 SELECT）；第 11b 步改经 ledger-api 读取。</li>
 */
@Getter
@Setter
public class BalanceBook {
    /**
     * 账簿主键
     */
    private Long id;

    /**
     * 账簿使用的科目表主键
     */
    private Long chartId;
}
