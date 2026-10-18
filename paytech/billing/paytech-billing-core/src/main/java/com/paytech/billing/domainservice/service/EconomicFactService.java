package com.paytech.billing.domainservice.service;

import java.time.Instant;

import com.paytech.billing.domain.EconomicFactBookScope;
import com.paytech.billing.domain.EconomicFactContext;
import com.paytech.billing.domain.view.EconomicFactPageView;
import com.paytech.billing.domain.view.EconomicFactPublicationView;
import com.paytech.billing.domain.view.EconomicFactView;

/**
 * 经济事实的本地保存、Maker-Checker 状态推进、发布标记与查询
 * <li>状态推进以原状态与版本为条件；批准与 EconomicFactRecorded 发布任务登记在同一本地事务。</li>
 * <li>只操作本模块数据库，不调用外部依赖。</li>
 */
public interface EconomicFactService {

    /**
     * 读取事实头、全部明细行与币种精度
     *
     * @param factId 经济事实 ID
     * @return 上下文（不含被替代事实）；事实不存在返回空
     */
    EconomicFactContext loadContext(long factId);

    /** 一个事务内写入 DRAFT 事实头与全部明细行；来源引用被并发登记时按并发冲突拒绝。 */
    void saveDraft(EconomicFactContext context);

    /**
     * 一个事务内批准事实：DRAFT → APPROVED 并定稿版本；有被替代事实时将其 APPROVED → SUPERSEDED；登记 EconomicFactRecorded 发布任务
     * <li>任一条件更新未命中按并发冲突拒绝并整体回滚。</li>
     *
     * @param context 含被替代事实的已验证上下文
     * @param checkerId 复核人
     * @param now 批准时间
     */
    void approve(EconomicFactContext context, long checkerId, Instant now);

    /** 查询事实详情；不存在时业务拒绝。 */
    EconomicFactView getFact(long factId);

    /** 账簿主体下 ID 大于游标的已批准事实及其全部明细行，按 ID 升序。 */
    EconomicFactPageView listRecordedFacts(EconomicFactBookScope scope, long fromFactId, int limit);

    /** 账簿主体下已批准事实的发布进度。 */
    EconomicFactPublicationView getPublicationProgress(EconomicFactBookScope scope);
}
