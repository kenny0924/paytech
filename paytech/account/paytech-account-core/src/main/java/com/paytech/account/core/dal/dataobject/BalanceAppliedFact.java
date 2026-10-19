package com.paytech.account.core.dal.dataobject;

import java.time.Instant;

import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.core.enums.MaturityStatus;

import lombok.Getter;
import lombok.Setter;

/** t_balance_applied_fact：已处理的经济事实，按 fact_id + fact_version 幂等，记录替代关系、入账批次与到期转可用进度。 */
@Getter
@Setter
public class BalanceAppliedFact {
    /** 账户服务生成的 Snowflake 主键。 */
    private Long id;

    /** billing 经济事实 ID。 */
    private Long factId;

    /** 经济事实版本；替代关系预登记时为替代事实版本减一。 */
    private Long factVersion;

    /** 经济事实类型代码；仅预登记时为空。 */
    private String factType;

    /** 本事实替代的前一经济事实；首次登记为空。 */
    private Long supersedesFactId;

    /** 替代本事实的后一经济事实；未被替代时为空。 */
    private Long supersededByFactId;

    /** 经济事实头的 Operating Entity 编号，决定入账流。 */
    private String entityNo;

    /** 经济事实头的平台服务费 Finance Entity；无合作方为空。 */
    private Long financeEntityId;

    /** 经济事实币种。 */
    private String currency;

    /** 事实对余额的影响状态。 */
    private AppliedFactStatus applyStatus;

    /** 自身事件到达后生成的入账批次；仅预登记时为空。 */
    private Long postingId;

    /** 应付余额到期转可用的 UTC 时间；billing 未给出为空。 */
    private Instant balanceAvailableTime;

    /** 到期转可用进度。 */
    private MaturityStatus maturityStatus;

    /** 到期转可用完成的 UTC 微秒时间。 */
    private Instant maturedTime;

    /** 事实被本服务处理的 UTC 微秒时间；仅预登记时为空。 */
    private Instant appliedTime;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
