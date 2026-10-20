package com.paytech.billing.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.domain.BillingFactSearchCriteria;
import com.paytech.billing.domain.view.BillingFactProgressView;

/** t_billing_fact：计费事实头的登记、批准、替代、发布标记与读取。 */
@Mapper
public interface BillingFactMapper {

    /** 插入事实头；返回影响行数。 */
    int insert(BillingFact fact);

    /** 按主键读取。 */
    BillingFact selectById(@Param("id") long id);

    /** 按一组主键读取，按 ID 升序。 */
    List<BillingFact> selectByIds(@Param("ids") Collection<Long> ids);

    /** 按来源系统与来源事件键（业务唯一身份）读取。 */
    BillingFact selectBySource(@Param("sourceSystem") String sourceSystem,
        @Param("sourceEventKey") String sourceEventKey);

    /** 替代给定事实的全部事实，按 ID 升序。 */
    List<BillingFact> selectBySupersedesFactId(@Param("supersedesFactId") long supersedesFactId);

    /** 以给定事实为来源的全部派生事实，按 ID 升序。 */
    List<BillingFact> selectByParentFactId(@Param("parentFactId") long parentFactId);

    /** DRAFT（版本 0）→ APPROVED 并定稿版本，业务时间覆盖为批准时间；返回影响行数。 */
    int approve(@Param("id") long id, @Param("factVersion") long factVersion, @Param("checkerId") long checkerId,
        @Param("now") Instant now);

    /** DRAFT（版本 0）→ VOIDED；返回影响行数。 */
    int voidDraft(@Param("id") long id, @Param("now") Instant now);

    /** APPROVED（指定版本）→ SUPERSEDED；返回影响行数。 */
    int supersede(@Param("id") long id, @Param("factVersion") long factVersion, @Param("now") Instant now);

    /** 首次发布确认时写入 published_at；返回影响行数。 */
    int markPublished(@Param("id") long id, @Param("factVersion") long factVersion, @Param("now") Instant now);

    /** 账簿主体下 ID 大于游标的已批准事实，按 ID 升序取 limit 条。 */
    List<BillingFact> selectRecordedByBook(@Param("entityNo") String entityNo,
        @Param("financeEntityId") Long financeEntityId, @Param("currency") String currency,
        @Param("fromFactId") long fromFactId, @Param("limit") int limit);

    /** 账簿主体下已批准事实的发布进度（未发布与已发布两段计数）。 */
    BillingFactProgressView selectPublicationProgress(@Param("entityNo") String entityNo,
        @Param("financeEntityId") Long financeEntityId, @Param("currency") String currency);

    /** 管理列表一页：按筛选条件、ID 降序取 limit 条。 */
    List<BillingFact> selectSearchPage(@Param("criteria") BillingFactSearchCriteria criteria, @Param("limit") int limit,
        @Param("offset") int offset);

    /** 管理列表在同一筛选条件下的总数。 */
    long countSearch(@Param("criteria") BillingFactSearchCriteria criteria);
}
