package com.paytech.finance.api.accounting.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本方案及其版本摘要。 */
@Getter
@Setter
public class ChannelCostPlanResponseBody {
    /** 方案代码。 */
    private String planCode;

    /** Payment Channel 稳定业务 ID。 */
    private String channelId;

    /** 方案状态：DRAFT、ACTIVE、INACTIVE。 */
    private String status;

    /**
     * 当前生效版本号
     * <li>尚无生效版本时为空。</li>
     */
    private String currentCostVer;

    /** 全部版本摘要，按版本号降序；新方案为空列表。 */
    private List<ChannelCostVersionSummaryResponseBody> versions;
}
