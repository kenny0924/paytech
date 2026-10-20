package com.paytech.channel.connector.api.cost.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本版本摘要。 */
@Getter
@Setter
public class ChannelCostVersionSummaryResponseBody {
    /**
     * 版本主键，十进制字符串
     * <li>查询变更记录时作为 targetType=CHANNEL_COST_VERSION 的 targetId。</li>
     */
    private String costVerId;

    /** 方案内递增的版本号。 */
    private String costVer;

    /** 版本下适用范围数。 */
    private int scopeCount;

    /** 版本下全部适用范围的成本组成数。 */
    private int componentCount;

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
