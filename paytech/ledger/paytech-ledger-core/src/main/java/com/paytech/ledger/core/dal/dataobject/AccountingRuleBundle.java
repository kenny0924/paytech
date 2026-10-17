package com.paytech.ledger.core.dal.dataobject;

import java.time.Instant;

import com.paytech.ledger.core.enums.ReferenceDataStatus;

import lombok.Getter;
import lombok.Setter;

/** t_accounting_rule_bundle：自动记账规则集稳定身份与当前生效版本指针。 */
@Getter
@Setter
public class AccountingRuleBundle {
    /**
     * 自动记账规则集的 Snowflake 主键
     */
    private Long id;

    /**
     * 自动记账规则集的稳定业务代码
     */
    private String bundleCode;

    /**
     * 当前生效的自动记账规则版本主键
     * <li>可为空。</li>
     */
    private Long currentVerId;

    /**
     * 规则集生命周期状态
     */
    private ReferenceDataStatus status;

    /**
     * 条件更新使用的乐观锁版本
     */
    private Long version;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
