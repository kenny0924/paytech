package com.paytech.billing.domain;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** channel-connector 命中的渠道成本方案版本、范围与全部成本组件（Owner 为 channel-connector，只读取得）。 */
@Getter
@Builder
public class ChannelCostResolution {
    /** 方案代码，证据 */
    private final String planCode;

    /** 命中版本主键，明细行 source_rule_version_id */
    private final long costVerId;

    /** 方案内版本号，证据 */
    private final String costVer;

    /** 版本内容摘要，证据 */
    private final String contentHash;

    /** 命中范围主键，证据 */
    private final String scopeId;

    /** 范围币种，即成本币种 */
    private final String currency;

    /** 成本组件，按 componentCode 升序，非空 */
    private final List<ChannelCostComponentRule> components;
}
