package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 计费事实登记状态（t_billing_fact.status）
 * <li>MANUAL 事实 DRAFT → APPROVED 由 Checker 批准（Maker-Checker）；AUTO 事实登记即 APPROVED；APPROVED 事实被新事实替代后为
 * SUPERSEDED；填错的 DRAFT 可作废为 VOIDED（终态，不能再批准）。</li>
 * <li>只有 APPROVED 与 SUPERSEDED 事实会发布 BillingFactRecorded；SUPERSEDED 不撤回已发布事件，替代关系由新事实的
 * supersedesFactId 与 factVersion 表达。</li>
 */
public enum BillingFactStatus implements CodeEnum {
    /** Maker 已提交，待 Checker 批准；内容不可修改，仅 MANUAL */
    DRAFT,

    /** 已批准，可被 account 与 ledger 消费 */
    APPROVED,

    /** 已批准且已被更正事实替代 */
    SUPERSEDED,

    /** DRAFT 被作废；从不发布事件，不能再批准，仅 MANUAL */
    VOIDED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
