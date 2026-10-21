package com.paytech.reconciliation.domain;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * channel-connector MID 在对账侧需要的事实：挂 MID 核对与未归属 MID 计算
 * <li>由 integration 从 MID 详情（状态、经营主体、能力组币种）与列表摘要（渠道编码）组装；远程 DTO 不越过 integration 边界。</li>
 */
@Getter
@AllArgsConstructor
public class MidProfile {
    /** channel-connector MID 主键 */
    private final long midId;

    /** 渠道侧外部 MID 编码 */
    private final String externalMidCode;

    /**
     * 渠道编码
     * <li>MID 详情只有 channelId，渠道编码取自列表摘要；摘要中找不到同一 MID 时为空。</li>
     */
    private final String channelCode;

    /** 经营主体编号 */
    private final String entityNo;

    /** MID 状态：DRAFT、ACTIVE、SUSPENDED、CLOSED */
    private final String status;

    /** 能力组中出现的币种 */
    private final Set<String> currencies;
}
