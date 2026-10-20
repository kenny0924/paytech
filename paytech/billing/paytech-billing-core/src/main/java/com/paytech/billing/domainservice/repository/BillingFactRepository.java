package com.paytech.billing.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.domain.BillingFactBookScope;
import com.paytech.billing.domain.BillingFactSearchCriteria;
import com.paytech.billing.domain.view.BillingFactProgressView;

/** t_billing_fact：计费事实头的登记、状态推进与读取。 */
public interface BillingFactRepository {

    /** 插入事实头；来源唯一键冲突由数据库抛出。 */
    void insert(BillingFact fact);

    /** 按主键读取，不存在返回空。 */
    BillingFact findById(long id);

    /** 按一组主键读取，按 ID 升序；集合为空时返回空列表。 */
    List<BillingFact> findByIds(Collection<Long> ids);

    /** 按业务唯一身份（来源系统 + 来源事件键）读取，不存在返回空。 */
    BillingFact findBySource(String sourceSystem, String sourceEventKey);

    /** 替代给定事实的全部事实，按 ID 升序。 */
    List<BillingFact> findBySupersedesFactId(long supersedesFactId);

    /** 以给定事实为来源的全部派生事实，按 ID 升序。 */
    List<BillingFact> findByParentFactId(long parentFactId);

    /**
     * DRAFT → APPROVED 并定稿版本，业务时间覆盖为批准时间，成功后同步原对象
     *
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 事实已不是 DRAFT
     */
    void approve(BillingFact fact, long factVersion, long checkerId, Instant now);

    /**
     * DRAFT → VOIDED，成功后同步原对象
     *
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 事实已不是 DRAFT
     */
    void voidDraft(BillingFact fact, Instant now);

    /**
     * APPROVED → SUPERSEDED，以原对象的版本做条件，成功后同步原对象
     *
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 被替代事实已被替代或版本变化
     */
    void supersede(BillingFact fact, Instant now);

    /**
     * 首次发布确认时写入 published_at，成功后同步原对象
     *
     * @return 本次是否写入；已写入过返回 false
     */
    boolean markPublished(BillingFact fact, Instant now);

    /** 账簿主体下 ID 大于游标的已批准事实，按 ID 升序取 limit 条。 */
    List<BillingFact> findRecordedByBook(BillingFactBookScope scope, long fromFactId, int limit);

    /** 账簿主体下已批准事实的发布进度。 */
    BillingFactProgressView publicationProgress(BillingFactBookScope scope);

    /** 管理列表一页：按筛选条件、ID 降序。 */
    List<BillingFact> findSearchPage(BillingFactSearchCriteria criteria, int limit, int offset);

    /** 管理列表在同一筛选条件下的总数。 */
    long countSearch(BillingFactSearchCriteria criteria);
}
