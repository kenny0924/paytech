package com.paytech.billing.domain;

import java.util.List;

import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.core.dal.dataobject.EconomicFactLine;
import com.paytech.billing.domain.view.EconomicFactView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一条经济事实的登记、批准与发布上下文：事实头、按行号升序的全部明细行、被替代事实与币种精度
 * <li>提交时由 Processor 一次构造（头与明细行尚未落库）；批准与发布时从已提交事实加载。</li>
 * <li>Repository 在 SQL 成功后直接同步 fact / supersededFact 的状态、版本与时间。</li>
 */
@Getter
@AllArgsConstructor
public class EconomicFactContext {
    /** 经济事实头。 */
    private final EconomicFact fact;

    /** 全部明细行，按行号升序。 */
    private final List<EconomicFactLine> lines;

    /**
     * 被替代事实
     * <li>批准时加载；首次登记或仅读取时为空。</li>
     */
    private final EconomicFact supersededFact;

    /** 事实币种的 minor unit，用于输出金额补齐小数位。 */
    private final int minorUnit;

    /** 批准时定稿的事实版本：首次登记为 1，替代事实为被替代事实版本 + 1。 */
    public long approvedVersion() {
        return supersededFact == null ? 1 : supersededFact.getFactVersion() + 1;
    }

    /** 事实已批准（含随后被替代），即已登记或可登记发布任务。 */
    public boolean recorded() {
        return fact.getStatus() == EconomicFactStatus.APPROVED || fact.getStatus() == EconomicFactStatus.SUPERSEDED;
    }

    /** 头与全部明细行的载荷：事件 Payload、登记不变量校验与响应共用。 */
    public EconomicFactPayload toPayload() {
        return EconomicFactPayloads.of(fact, lines, minorUnit);
    }

    /** 查询与写接口响应。 */
    public EconomicFactView toView() {
        return EconomicFactView.of(fact, toPayload());
    }
}
