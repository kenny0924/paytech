package com.paytech.account.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 账簿的余额消费进度（design/04 §6）
 * <li>ledger 关账步骤 2 以 {@link #maxAppliedFactId} 与 billing 已发布的最大 factId 比对；步骤 3 读取 CHANNEL_ACCOUNT / CASH_ACCOUNT 的非零账户数。</li>
 */
@Getter
@Setter
public class BalanceConsistencyResponseBody {
    /** ledger 账簿主键。 */
    private String ledgerBookId;

    /** 账簿主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
    private String bookEntityType;

    /** 账簿主体标识。 */
    private String bookEntityId;

    /** 账簿币种。 */
    private String currency;

    /** 已处理（应用、冲回或因被替代而跳过）的属于该账簿主体的经济事实数。 */
    private String appliedFactCount;

    /** 已处理经济事实中最大的 factId；尚未处理任何事实时为空。 */
    private String maxAppliedFactId;

    /** 按 Owner 类型的净余额非零账户数，固定列出 CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT。 */
    private List<OwnerBalanceCountResponseBody> nonZeroAccountCounts;
}
