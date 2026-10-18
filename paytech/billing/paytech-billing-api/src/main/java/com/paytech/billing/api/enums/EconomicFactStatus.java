package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 经济事实登记状态（t_economic_fact.status）
 * <li>DRAFT → APPROVED 由 Checker 批准（Maker-Checker）；APPROVED 事实被新事实替代后为 SUPERSEDED。</li>
 * <li>只有 APPROVED 与 SUPERSEDED 事实会发布 EconomicFactRecorded；SUPERSEDED 不撤回已发布事件，替代关系由新事实的
 * supersedesFactId 与 factVersion 表达。</li>
 */
public enum EconomicFactStatus implements CodeEnum {
    /** Maker 已提交，待 Checker 批准；内容不可修改 */
    DRAFT,

    /** 已批准，可被 ledger 与 account 消费 */
    APPROVED,

    /** 已批准且已被更正事实替代 */
    SUPERSEDED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
