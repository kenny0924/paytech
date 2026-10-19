package com.paytech.channel.connector.api.cost.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本命中结果：方案版本、唯一命中范围与该范围全部成本组成；ID 与版本号为十进制字符串。 */
@Getter
@Setter
public class ChannelCostResolutionResponseBody {
    /** 方案代码。 */
    private String planCode;

    /** 命中版本主键（t_channel_cost_plan_ver.id）；billing 行证据 sourceRuleVersionId 引用它。 */
    private String costVerId;

    /** 方案内版本号。 */
    private String costVer;

    /** 版本内容摘要，64 位小写十六进制。 */
    private String contentHash;

    /** 版本开始参与匹配的时间（UTC）。 */
    private Instant effectiveFrom;

    /**
     * 版本停止参与匹配的时间（UTC）
     * <li>长期有效时为空。</li>
     */
    private Instant effectiveTo;

    /** 命中范围主键（t_channel_cost_scope.id）。 */
    private String scopeId;

    /** 版本内范围序号。 */
    private Integer scopeNo;

    /** 该范围实际采用的 Channel 合同或费率文件稳定引用。 */
    private String channelAgreementRef;

    /** 范围币种，也是成本金额的币种。 */
    private String currency;

    /** 按 componentCode 升序的成本组成，非空。 */
    private List<ChannelCostComponentResponseBody> components;
}
