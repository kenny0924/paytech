package com.paytech.billing.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.domain.EconomicFactBookScope;
import com.paytech.billing.domain.view.EconomicFactPublicationView;

/** t_economic_fact：经济事实头的登记、状态推进与读取。 */
public interface EconomicFactRepository {

    /** 按主键读取，不存在返回空。 */
    EconomicFact findById(long id);

    /** 按提交的业务唯一身份（来源系统 + 来源事件键）读取，不存在返回空。 */
    EconomicFact findBySource(String sourceSystem, String sourceEventKey);

    /** 插入 DRAFT 事实头；来源唯一键冲突由数据库抛出。 */
    void insert(EconomicFact fact);

    /**
     * DRAFT → APPROVED 并定稿版本，成功后同步原对象
     *
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 事实已不是 DRAFT
     */
    void approve(EconomicFact fact, long factVersion, long checkerId, Instant now);

    /**
     * APPROVED → SUPERSEDED，以原对象的版本做条件，成功后同步原对象
     *
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 被替代事实已被替代或版本变化
     */
    void supersede(EconomicFact fact, Instant now);

    /**
     * 首次发布确认时写入 published_at，成功后同步原对象
     *
     * @return 本次是否写入；已写入过返回 false
     */
    boolean markPublished(EconomicFact fact, Instant now);

    /** 账簿主体下 ID 大于游标的已批准事实，按 ID 升序取 limit 条。 */
    List<EconomicFact> findRecordedByBook(EconomicFactBookScope scope, long fromFactId, int limit);

    /** 账簿主体下已批准事实的发布进度。 */
    EconomicFactPublicationView publicationProgress(EconomicFactBookScope scope);
}
