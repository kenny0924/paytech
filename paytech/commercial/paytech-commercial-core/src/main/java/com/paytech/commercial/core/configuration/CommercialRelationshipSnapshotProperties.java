package com.paytech.commercial.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Checkout 解析输出的商业关系快照是否生效（technique.md §3.2）。 */
@Getter
@AllArgsConstructor
@ConfigurationProperties("commercial.relationship-snapshot")
public class CommercialRelationshipSnapshotProperties {
    /**
     * 快照是否生效
     * <li>false（默认）：响应 effectiveModelVersion=0，收单只记录快照；true：effectiveModelVersion=1，新订单按快照生效。</li>
     * <li>只影响之后的解析，已冻结的订单不变；修改需重启。</li>
     */
    private final boolean enabled;

    /** 当前开关对应的生效模型版本。 */
    public int effectiveModelVersion() {
        return enabled ? 1 : 0;
    }
}
