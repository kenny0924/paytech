package com.paytech.billing.domain;

import java.util.List;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.BillingFactLine;
import com.paytech.billing.core.dal.dataobject.BillingFactPricing;
import com.paytech.billing.core.dal.dataobject.BillingFactRef;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一条计费事实的登记、批准与发布上下文：事实头、外部引用、按行号升序的全部明细行、被替代事实、定价输入与币种精度
 * <li>提交时由 Processor 一次构造（尚未落库）；批准与发布时从已提交事实加载。</li>
 * <li>自动 PAYMENT 事实登记时由 PaymentFactAssembler 一次构造，携带待写入的定价输入；其他路径不加载定价输入。</li>
 * <li>Repository 在 SQL 成功后直接同步 fact / supersededFact 的状态、版本与时间。</li>
 */
@Getter
@AllArgsConstructor
public class BillingFactContext {
    /** 计费事实头。 */
    private final BillingFact fact;

    /** 外部引用，按引用类型升序；无引用为空列表。 */
    private final List<BillingFactRef> refs;

    /** 全部明细行，按行号升序。 */
    private final List<BillingFactLine> lines;

    /**
     * 被替代事实
     * <li>批准时加载；首次登记或仅读取时为空。</li>
     */
    private final BillingFact supersededFact;

    /** 事实币种的 minor unit，用于输出金额补齐小数位。 */
    private final int minorUnit;

    /**
     * PAYMENT 事实的定价与结算输入
     * <li>仅自动 PAYMENT 事实登记时持有；人工登记、批准与读取路径为空。</li>
     */
    private final BillingFactPricing pricing;

    /** 不携带定价输入的上下文：人工登记、批准、发布与读取路径。 */
    public BillingFactContext(BillingFact fact, List<BillingFactRef> refs, List<BillingFactLine> lines,
        BillingFact supersededFact, int minorUnit) {
        this(fact, refs, lines, supersededFact, minorUnit, null);
    }

    /** 批准时定稿的事实版本：首次登记为 1，替代事实为被替代事实版本 + 1。 */
    public long approvedVersion() {
        return supersededFact == null ? 1 : supersededFact.getFactVersion() + 1;
    }

    /** 事实已批准（含随后被替代），即已登记或可登记发布任务。 */
    public boolean recorded() {
        return fact.getStatus() == BillingFactStatus.APPROVED || fact.getStatus() == BillingFactStatus.SUPERSEDED;
    }

    /** 头、引用与全部明细行的载荷：事件 Payload、登记不变量校验与响应共用。 */
    public BillingFactPayload toPayload() {
        return BillingFactPayloads.of(fact, refs, lines, minorUnit);
    }
}
