package com.paytech.billing.api.response;

import lombok.Getter;
import lombok.Setter;

/** 账簿主体下已批准经济事实的发布进度，供关账一致性检查（design/04 §6）。 */
@Getter
@Setter
public class PendingEconomicFactsResponseBody {
    /** 已批准（含已被替代）但 EconomicFactRecorded 尚未确认发布的事实数 */
    private String pendingCount;

    /** 未发布事实中最大的 factId；没有未发布事实时为空 */
    private String maxPendingFactId;

    /** 已确认发布事实中最大的 factId，供与 account / ledger 已消费的最大事实比对；尚无已发布事实时为空 */
    private String maxPublishedFactId;
}
