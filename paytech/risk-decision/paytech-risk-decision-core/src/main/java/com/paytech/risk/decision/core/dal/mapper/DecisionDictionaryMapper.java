package com.paytech.risk.decision.core.dal.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;

/** 原因与标签的固定表 SQL 映射。 */
public interface DecisionDictionaryMapper {
    /** 读取原因当前值，并按调用方要求使用共享锁或写锁。 */
    RuleRejectReason findReason(@Param("code") String code, @Param("lock") String lock);
    /** 按已转义的字面搜索条件及状态返回编码升序的原因页。 */
    List<RuleRejectReason> listReasons(@Param("pattern") String pattern, @Param("status") DictionaryStatus status,
        @Param("limit") int limit, @Param("offset") int offset);
    /** 统计与原因列表相同条件的记录数量。 */
    long countReasons(@Param("pattern") String pattern, @Param("status") DictionaryStatus status);
    /** 插入原因当前值，编码永久唯一由数据库索引保护。 */
    int insertReason(RuleRejectReason value);
    /** 以编码和期望版本更新原因内容、状态与最后变更信息。 */
    int updateReason(@Param("code") String code, @Param("expectedVersion") long expectedVersion,
        @Param("name") String name, @Param("description") String description, @Param("defaultMessage") String defaultMessage,
        @Param("status") DictionaryStatus status, @Param("actorId") long actorId, @Param("time") Instant time);
    /** 读取标签当前值，并按调用方要求使用共享锁或写锁。 */
    RuleSubjectTag findTag(@Param("code") String code, @Param("lock") String lock);
    /** 按已转义的字面搜索条件及状态返回编码升序的标签页。 */
    List<RuleSubjectTag> listTags(@Param("pattern") String pattern, @Param("status") DictionaryStatus status,
        @Param("limit") int limit, @Param("offset") int offset);
    /** 统计与标签列表相同条件的记录数量。 */
    long countTags(@Param("pattern") String pattern, @Param("status") DictionaryStatus status);
    /** 插入标签当前值，不增加适用主体关系。 */
    int insertTag(RuleSubjectTag value);
    /** 以编码和期望版本更新标签内容、状态与最后变更信息。 */
    int updateTag(@Param("code") String code, @Param("expectedVersion") long expectedVersion,
        @Param("name") String name, @Param("description") String description, @Param("defaultMessage") String defaultMessage,
        @Param("status") DictionaryStatus status, @Param("actorId") long actorId, @Param("time") Instant time);
}
