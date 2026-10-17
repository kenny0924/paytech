package com.paytech.ledger.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_close_account_rule：不可变地定义 Revenue 或 Expense 科目向本期损益及留存收益科目的有效期映射。 */
@Getter
@Setter
public class CloseAccountRule {
    /**
     * 关账科目映射规则的 Snowflake 主键
     */
    private Long id;

    /**
     * 该关账映射适用的本库稳定会计科目表
     */
    private Long chartId;

    /**
     * 需要在关账时结转的 Revenue 或 Expense 科目定义
     */
    private Long sourceAccountId;

    /**
     * 接收当期收入或费用结转的 Equity 科目定义
     */
    private Long currentResultAccountId;

    /**
     * 接收累计期间结果结转的 Equity 科目定义
     */
    private Long retainedEarningsAccountId;

    /**
     * 该关账科目映射开始生效的 UTC 微秒时间
     */
    private Instant effectiveFrom;

    /**
     * 该关账科目映射停止生效的 UTC 微秒时间
     * <li>可为空。</li>
     */
    private Instant effectiveTo;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
