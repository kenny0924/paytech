package com.paytech.account.core.dal.dataobject;

import java.time.Instant;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.PostingSourceType;

import lombok.Getter;
import lombok.Setter;

/** t_account_posting：账户入账批次，一次经济事实入账或一次桶移动一条，经补偿事件发布为 AccountPosted。 */
@Getter
@Setter
public class AccountPosting {
    /** 账户服务生成的批次 Snowflake 主键，即 AccountPosted 的 postingId。 */
    private Long id;

    /** 批次所属主体类型。 */
    private AccountEntityType entityType;

    /** 批次所属主体标识。 */
    private String entityId;

    /** 批次币种。 */
    private String currency;

    /** 分片号，当前固定 0。 */
    private Integer shardNo;

    /** 同一流内从 1 起连续递增的入账序号。 */
    private Long postingSequence;

    /** 批次来源。 */
    private PostingSourceType sourceType;

    /** 事实批次的经济事实 ID；桶移动批次为空。 */
    private Long factId;

    /** 事实批次的经济事实版本；桶移动批次为空。 */
    private Long factVersion;

    /** 桶移动批次的移动记录 ID；事实批次为空。 */
    private Long movementId;

    /** 本批次流水行数，可为 0。 */
    private Integer entryCount;

    /** 入账事务内记录的 UTC 微秒时间。 */
    private Instant postedTime;

    /** AccountPosted 收到 Broker 确认的 UTC 微秒时间；尚未发布为空。 */
    private Instant publishedTime;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;
}
