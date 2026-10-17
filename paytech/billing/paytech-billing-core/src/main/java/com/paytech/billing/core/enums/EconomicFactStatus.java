package com.paytech.billing.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 经济事实登记状态（t_economic_fact.status）
 * <li>本期只有 RECORDED：事实一经登记即不可变，更正以新事实 + supersedes_fact_id 表达。</li>
 * <li>Maker-Checker 登记的中间状态与被替代状态由第 11a 步按登记接口定义补充。</li>
 */
public enum EconomicFactStatus implements CodeEnum {
    /** 已登记，可被 ledger 与 account 消费 */
    RECORDED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
