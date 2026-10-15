package com.paytech.finance.accounting.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_book_sequence：为每个总账账簿在 Journal 过账本地事务中通过行锁分配严格递增的 Journal 序号；已提交 Journal 的序号不可复用。 */
@Getter
@Setter
public class BookSequence {
    /**
     * 服务侧生成的账簿分录序列 Snowflake 主键
     */
    private Long id;

    /**
     * 使用该分录序列的本库总账账簿
     */
    private Long ledgerBookId;

    /**
     * 下一次在 Journal 过账本地事务中通过 SELECT FOR UPDATE 锁定本行后可分配的正整数序号
     */
    private Long nextValue;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
