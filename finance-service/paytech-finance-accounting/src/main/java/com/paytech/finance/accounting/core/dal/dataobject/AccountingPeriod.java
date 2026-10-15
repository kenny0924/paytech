package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.PeriodStatus;
import java.time.Instant;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** t_accounting_period：保存财务日历下可过账、关闭或重开的会计期间。 */
@Getter
@Setter
public class AccountingPeriod {
    /**
     * 服务侧生成的会计期间 Snowflake 主键
     */
    private Long id;

    /**
     * 该会计期间所属的本库财务日历
     */
    private Long calendarId;

    /**
     * 该期间所属的四位财务年度
     */
    private Integer fiscalYear;

    /**
     * 同一财年中严格递增的会计期间序号
     */
    private Integer periodNo;

    /**
     * 该会计期间包含的首个业务日期
     */
    private LocalDate startDate;

    /**
     * 该会计期间包含的最后一个业务日期
     */
    private LocalDate endDate;

    /**
     * 控制该期间是否允许新会计分录过账
     * <li>允许值：FUTURE、OPEN、CLOSING、CLOSED、REOPENING。</li>
     */
    private PeriodStatus status;

    /**
     * 该期间完成受控重开的累计次数
     */
    private Integer reopenCount;

    /**
     * 更新期间状态时进行条件更新的乐观锁版本号
     */
    private Long version;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
