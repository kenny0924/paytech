package com.paytech.account.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.account.api.enums.EntrySide;

import lombok.Getter;
import lombok.Setter;

/** t_account_entry：不可变账户流水明细，是 Owner 账户余额的唯一来源。 */
@Getter
@Setter
public class AccountEntry {
    /** 账户服务生成的流水 Snowflake 主键。 */
    private Long id;

    /** 所属入账批次。 */
    private Long postingId;

    /** 批次内从 1 起连续的行号。 */
    private Integer lineNo;

    /** 被改变的 Owner 账户。 */
    private Long accountId;

    /** 对账户的借贷方向。 */
    private EntrySide entrySide;

    /** 已按币种精度归整的正金额。 */
    private BigDecimal amount;

    /**
     * 来源经济事实明细行号
     * <li>事实头总额为 0；桶移动为空。</li>
     */
    private Integer factLineNo;

    /** 替代冲回时被冲回的原流水；普通行为空。 */
    private Long reversesEntryId;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;
}
