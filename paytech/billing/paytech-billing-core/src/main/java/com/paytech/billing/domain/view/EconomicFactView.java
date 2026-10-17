package com.paytech.billing.domain.view;

import java.time.Instant;

import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.core.dal.dataobject.EconomicFact;

import lombok.Getter;
import lombok.Setter;

/**
 * 经济事实详情：事实头与全部明细行的载荷，加上登记状态、编制 / 复核人与发布时间
 * <li>金额已按币种精度补齐；载荷与 EconomicFactRecorded 事件 Payload 同源。</li>
 */
@Getter
@Setter
public class EconomicFactView {
    /** 头与全部明细行。 */
    private EconomicFactPayload payload;

    /** 登记状态。 */
    private EconomicFactStatus status;

    /**
     * 编制人
     * <li>系统生成的交易事实为空。</li>
     */
    private Long makerId;

    /**
     * 复核人
     * <li>未批准为空。</li>
     */
    private Long checkerId;

    /**
     * 批准时间
     * <li>未批准为空。</li>
     */
    private Instant approvedTime;

    /**
     * 事件发布确认时间
     * <li>未发布为空。</li>
     */
    private Instant publishedAt;

    /** 由事实头与其载荷组装详情。 */
    public static EconomicFactView of(EconomicFact fact, EconomicFactPayload payload) {
        EconomicFactView view = new EconomicFactView();
        view.setPayload(payload);
        view.setStatus(fact.getStatus());
        view.setMakerId(fact.getMakerId());
        view.setCheckerId(fact.getCheckerId());
        view.setApprovedTime(fact.getApprovedTime());
        view.setPublishedAt(fact.getPublishedAt());
        return view;
    }
}
