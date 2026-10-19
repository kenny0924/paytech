package com.paytech.account.core.dal.dataobject;

import java.time.Instant;

import com.paytech.account.api.enums.AccountEntityType;

import lombok.Getter;
import lombok.Setter;

/** t_account_posting_stream：一个主体 + 币种 + 分片的入账流序号行，入账与桶移动在本行锁内分配连续序号。 */
@Getter
@Setter
public class AccountPostingStream {
    /** 账户服务生成的 Snowflake 主键。 */
    private Long id;

    /** 入账流所属主体类型。 */
    private AccountEntityType entityType;

    /** 入账流所属主体标识。 */
    private String entityId;

    /** 入账流币种。 */
    private String currency;

    /** 分片号，当前固定 0。 */
    private Integer shardNo;

    /** 已分配的最大入账序号；尚未入账为 0。 */
    private Long lastSequence;

    /** 最近一次入账的 UTC 微秒时间；尚未入账为空。 */
    private Instant lastPostedTime;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
