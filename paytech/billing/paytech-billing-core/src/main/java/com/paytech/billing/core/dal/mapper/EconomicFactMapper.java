package com.paytech.billing.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.domain.view.EconomicFactPublicationView;

/** t_economic_fact：经济事实头的登记、批准、替代、发布标记与按账簿主体读取。 */
@Mapper
public interface EconomicFactMapper {

    /** 按主键读取。 */
    EconomicFact selectById(@Param("id") long id);

    /** 按来源系统与来源事件键（提交的业务唯一身份）读取。 */
    EconomicFact selectBySource(@Param("sourceSystem") String sourceSystem,
        @Param("sourceEventKey") String sourceEventKey);

    /** 插入 DRAFT 事实头。 */
    int insert(EconomicFact fact);

    /** DRAFT → APPROVED 并定稿版本；返回影响行数。 */
    int approve(@Param("id") long id, @Param("factVersion") long factVersion, @Param("checkerId") long checkerId,
        @Param("now") Instant now);

    /** APPROVED（指定版本）→ SUPERSEDED；返回影响行数。 */
    int supersede(@Param("id") long id, @Param("factVersion") long factVersion, @Param("now") Instant now);

    /** 首次发布确认时写入 published_at；返回影响行数。 */
    int markPublished(@Param("id") long id, @Param("factVersion") long factVersion, @Param("now") Instant now);

    /** 账簿主体下 id 大于游标的已批准事实，按 id 升序取 limit 条。 */
    List<EconomicFact> selectRecordedByBook(@Param("entityNo") String entityNo,
        @Param("financeEntityId") Long financeEntityId, @Param("currency") String currency,
        @Param("fromFactId") long fromFactId, @Param("limit") int limit);

    /** 账簿主体下已批准事实的发布进度。 */
    EconomicFactPublicationView selectPublicationProgress(@Param("entityNo") String entityNo,
        @Param("financeEntityId") Long financeEntityId, @Param("currency") String currency);
}
