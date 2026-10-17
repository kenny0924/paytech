package com.paytech.billing.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本版本详情。 */
@Getter
@Setter
public class ChannelCostVersionResponseBody {
    /** 方案代码。 */
    private String planCode;

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

    /**
     * 内容摘要
     * <li>{@code CODE-MOR:FINANCE:CHANNEL_COST:1} 的 SHA-256 小写十六进制，创建时冻结；复核时必须原样确认。</li>
     */
    private String contentHash;

    /** 编制人。 */
    private String makerId;

    /**
     * 复核人
     * <li>未复核时为空。</li>
     */
    private String checkerId;

    /** 按 scopeNo 升序的适用范围。 */
    private List<ChannelCostScopeResponseBody> scopes;
}
