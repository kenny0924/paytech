package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;

/** t_business_change_version（commercial 实例）的持久化读写；快照只插入。 */
public interface BusinessChangeVersionMapper {
    /** 插入一条不可变快照。 */
    int insert(@Param("row") BusinessChangeVersion row);

    /** 读取一条历史身份的全部快照。 */
    List<BusinessChangeVersion> findByHistoryId(@Param("historyId") long historyId);

    /** 读取一组历史身份的全部快照，按 history_id、version_no 升序。 */
    List<BusinessChangeVersion> findByHistoryIds(@Param("historyIds") Collection<Long> historyIds);

    /** 读取一组历史身份的一页快照，按 version_no 倒序；同版本 Root 在前（rootHistoryId），其余按 history_id 升序。 */
    List<BusinessChangeVersion> selectPageByHistoryIds(@Param("historyIds") Collection<Long> historyIds,
        @Param("rootHistoryId") long rootHistoryId, @Param("offset") int offset, @Param("limit") int limit);

    /** 一组历史身份的快照总数。 */
    long countByHistoryIds(@Param("historyIds") Collection<Long> historyIds);
}
