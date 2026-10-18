package com.paytech.account.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.account.core.enums.EntrySide;

import lombok.Getter;
import lombok.Setter;

/** t_balance_applied_entry：一条经济事实映射到余额账户的不可变逐行借贷。 */
@Getter
@Setter
public class BalanceAppliedEntry {
    /** 账户服务生成的 Snowflake 主键。 */
    private Long id;

    /** 所属已处理事实（t_balance_applied_fact.id）。 */
    private Long appliedFactId;

    /** billing 经济事实 ID。 */
    private Long factId;

    /** 产生变化的经济事实明细行号；交易事实总额取自事实头，行号为 0。 */
    private Integer lineNo;

    /** 被改变的余额账户（t_account_balance.id）。 */
    private Long balanceId;

    /** 借贷方向。 */
    private EntrySide entrySide;

    /** 已按币种精度归整的正金额。 */
    private BigDecimal amount;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;
}
