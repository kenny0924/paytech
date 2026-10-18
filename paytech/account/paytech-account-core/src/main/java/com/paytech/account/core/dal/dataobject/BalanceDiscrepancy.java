package com.paytech.account.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.BookEntityType;
import com.paytech.account.core.enums.DiscrepancyStatus;

import lombok.Getter;
import lombok.Setter;

/** t_balance_discrepancy：日终核对发现的账户余额与 ledger 分录聚合余额差异（design/04 §7）。 */
@Getter
@Setter
public class BalanceDiscrepancy {
    /** 账户服务生成的 Snowflake 主键。 */
    private Long id;

    /** 核对任务执行的 UTC 日期。 */
    private LocalDate reconDate;

    /** 被核对的 ledger 账簿主键。 */
    private Long ledgerBookId;

    /** 账簿主体类型。 */
    private BookEntityType bookEntityType;

    /** 账簿主体标识。 */
    private String bookEntityId;

    /** 账簿币种。 */
    private String currency;

    /** 存在差异的标准科目代码。 */
    private String accountCode;

    /** 核算对象类型。 */
    private OwnerType ownerType;

    /** 核算对象标识。 */
    private String ownerId;

    /** 余额桶。 */
    private BalanceBucket balanceBucket;

    /** 账户服务余额的净额；没有该余额时为空。 */
    private BigDecimal accountNetBalance;

    /** ledger 按分录聚合的净额；ledger 没有该账户时为空。 */
    private BigDecimal ledgerNetBalance;

    /** 两侧净额差的绝对值，缺失一侧按零计。 */
    private BigDecimal differenceAmount;

    /** 差异处置状态。 */
    private DiscrepancyStatus status;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
