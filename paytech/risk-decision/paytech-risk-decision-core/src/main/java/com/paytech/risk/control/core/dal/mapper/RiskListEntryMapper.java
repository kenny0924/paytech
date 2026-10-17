package com.paytech.risk.control.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.domain.list.RiskSubjectType;

/** 风险名单成员表映射。 */
public interface RiskListEntryMapper {
    /** 插入成员。 */
    int insert(@Param("row") RiskListEntry row);

    /** 按 ID 读取成员。 */
    RiskListEntry selectById(@Param("entryId") long entryId);

    /** 读取同名单同主体当前占用生效槽位的成员。 */
    RiskListEntry selectActive(@Param("listId") long listId, @Param("subjectType") RiskSubjectType subjectType,
        @Param("subjectKey") String subjectKey);

    /** 以期望状态和版本结束成员生效区间。 */
    int close(@Param("row") RiskListEntry row, @Param("status") RiskListEntryStatus status,
        @Param("deactivatedBy") Long deactivatedBy, @Param("deactivatedReason") String deactivatedReason,
        @Param("effectiveTo") Instant effectiveTo, @Param("now") Instant now);

    /** 统计在指定时刻处于生效区间的成员。 */
    long countEffective(@Param("listId") long listId, @Param("subjectType") RiskSubjectType subjectType,
        @Param("subjectKey") String subjectKey, @Param("at") Instant at);

    /** 按条件分页读取成员，新成员在前。 */
    List<RiskListEntry> selectPage(@Param("listId") long listId, @Param("subjectKey") String subjectKey,
        @Param("status") RiskListEntryStatus status, @Param("limit") int limit, @Param("offset") long offset);

    /** 统计同一条件下的成员数量。 */
    long count(@Param("listId") long listId, @Param("subjectKey") String subjectKey,
        @Param("status") RiskListEntryStatus status);
}
