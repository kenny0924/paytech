package com.paytech.billing.domainservice.service;

import java.time.Instant;

import com.paytech.billing.domain.BillingFactContext;

/**
 * 计费事实的本地保存与 Maker-Checker 状态推进
 * <li>状态推进以原状态与版本为条件；批准与 BillingFactRecorded 发布任务登记在同一本地事务。</li>
 * <li>只操作本模块数据库，不调用外部依赖。</li>
 */
public interface BillingFactService {

    /**
     * 读取事实头、引用、全部明细行与币种精度
     *
     * @param factId 计费事实 ID
     * @return 上下文（不含被替代事实）；事实不存在返回空
     */
    BillingFactContext loadContext(long factId);

    /** 一个事务内写入 DRAFT 事实头、全部引用与全部明细行；来源引用被并发登记时按并发冲突拒绝。 */
    void saveDraft(BillingFactContext context);

    /**
     * 一个事务内登记自动事实：写入 APPROVED 事实头（版本 1）、定价输入、全部引用与全部明细行，并登记 BILLING_FACT_RECORDED 发布任务
     * <li>同一来源事件被并发登记时（uk_fact_source 冲突）整体回滚并按并发冲突拒绝，由消费方重投后命中原事实。</li>
     *
     * @param context 已组装并通过登记不变量校验、携带定价输入的上下文
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 来源事件已被并发登记
     */
    void recordAutoFact(BillingFactContext context);

    /**
     * 一个事务内批准事实：DRAFT → APPROVED 并定稿版本、业务时间覆盖为批准时间；有被替代事实时将其 APPROVED → SUPERSEDED；登记
     * BILLING_FACT_RECORDED 发布任务
     * <li>任一条件更新未命中按并发冲突拒绝并整体回滚。</li>
     *
     * @param context 含被替代事实的已验证上下文
     * @param checkerId 复核人
     * @param now 批准时间
     */
    void approve(BillingFactContext context, long checkerId, Instant now);

    /**
     * 作废草稿：DRAFT → VOIDED；单条条件更新，不登记发布任务
     * <li>条件更新未命中（已被批准或作废）按并发冲突拒绝。</li>
     *
     * @param context 已确认为 DRAFT 的事实上下文
     * @param now 作废时间
     */
    void voidDraft(BillingFactContext context, Instant now);
}
