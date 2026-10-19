package com.paytech.channel.connector.api.cost.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本版本摘要。 */
@Getter
@Setter
public class ChannelCostVersionSummaryResponseBody {
    /** 方案内递增的版本号。 */
    private String costVer;

    /**
     * 版本状态
     * <li>DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、INACTIVE。</li>
     */
    private String status;

    /** 开始参与匹配的时间（UTC）。 */
    private Instant effectiveFrom;

    /**
     * 停止参与匹配的时间（UTC）
     * <li>长期有效时为空；被新版本替代时等于新版本开始时间。</li>
     */
    private Instant effectiveTo;

    /** 创建时冻结的内容摘要，64 位小写十六进制。 */
    private String contentHash;

    /** 编制人。 */
    private String makerId;

    /**
     * 复核人
     * <li>未复核时为空。</li>
     */
    private String checkerId;
}
