package com.paytech.billing.api.response;

import lombok.Getter;
import lombok.Setter;

/** 账簿主体下已批准计费事实的发布进度，供 ledger 关账步骤 CHECK_ACCOUNT_POSTING_PROGRESS 比对 billing → account 段。 */
@Getter
@Setter
public class BillingFactProgressResponseBody {
    /** 已批准（含已被替代）但 BillingFactRecorded 尚未确认发布的事实数 */
    private String pendingCount;

    /** 未发布事实中最大的 factId；没有未发布事实时为空 */
    private String maxPendingFactId;

    /**
     * 已批准（含已被替代）且 BillingFactRecorded 已确认发布的事实数，十进制字符串
     * <li>ledger 关账以它与 account 进度中的已应用事实数比对（billing → account 段）。</li>
     */
    private String publishedFactCount;

    /** 已确认发布事实中最大的 factId，供与 account / ledger 已消费的最大事实比对；尚无已发布事实时为空 */
    private String maxPublishedFactId;
}
