package com.paytech.risk.decision.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;

/** 发布审计事实的单表 Mapper。 */
public interface ReleaseAuditEventMapper {

    List<ReleaseAuditEvent> selectReleaseAuditEvents(@Param("releaseId") long releaseId);

    List<ReleaseAuditEvent> selectReleaseAuditEventsByReleaseIds(@Param("releaseIds") List<Long> releaseIds);

    int insertReleaseAuditEvent(@Param("value") ReleaseAuditEvent value);

    int insertReleaseAuditEvents(@Param("values") List<ReleaseAuditEvent> values);
}
