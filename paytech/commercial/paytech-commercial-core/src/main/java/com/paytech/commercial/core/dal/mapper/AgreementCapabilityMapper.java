package com.paytech.commercial.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;

/** t_agreement_capability 的持久化访问。 */
public interface AgreementCapabilityMapper {
    /** 插入一行开通项，返回影响行数。 */
    int insert(AgreementCapability capability);

    /** 按主键读取，不存在返回 null。 */
    AgreementCapability selectById(@Param("agreementCapabilityId") long agreementCapabilityId);

    /** 按唯一键（合同、OE、可开通项）读取，不存在返回 null。 */
    AgreementCapability selectByUniqueKey(@Param("agreementId") long agreementId, @Param("entityNo") String entityNo,
        @Param("capabilityCode") String capabilityCode);

    /** 读取一份合同的全部开通项，按主键升序。 */
    List<AgreementCapability> selectByAgreementId(@Param("agreementId") long agreementId);

    /** 读取一组合同的全部开通项，按主键升序；agreementIds 必须非空。 */
    List<AgreementCapability> selectByAgreementIds(@Param("agreementIds") Collection<Long> agreementIds);

    /** 仅当成员资格与执行状态仍为 capability 当前值时改写二者，返回影响行数。 */
    int updateMembership(@Param("capability") AgreementCapability capability,
        @Param("status") AgreementCapabilityStatus status,
        @Param("executionStatus") AgreementCapabilityExecutionStatus executionStatus, @Param("now") Instant now);

    /** 仅当成员资格为 ACTIVE 且执行状态仍为 capability 当前值时迁移执行状态，返回影响行数。 */
    int updateExecutionStatus(@Param("capability") AgreementCapability capability,
        @Param("executionStatus") AgreementCapabilityExecutionStatus executionStatus, @Param("now") Instant now);

    /** 有执行状态属于给定集合的开通项、ID 大于游标且落在本分片的合同 ID，升序取一页。 */
    List<Long> selectAgreementIdsByExecutionStatus(
        @Param("executionStatuses") Collection<AgreementCapabilityExecutionStatus> executionStatuses,
        @Param("afterAgreementId") long afterAgreementId, @Param("shardIndex") int shardIndex,
        @Param("shardTotal") int shardTotal, @Param("limit") int limit);

    /** 在某 OE 上有开通项的合同 ID，升序。 */
    List<Long> selectAgreementIdsByEntityNo(@Param("entityNo") String entityNo);

    /** 仅当 update_time 仍为 capability.updateTime 时改写生效区间、保证金条款与 3DS 要求，返回影响行数。 */
    int updateTerms(@Param("capability") AgreementCapability capability, @Param("effectiveFrom") Instant effectiveFrom,
        @Param("effectiveTo") Instant effectiveTo, @Param("rollingReserveRate") BigDecimal rollingReserveRate,
        @Param("rollingReserveDays") Integer rollingReserveDays, @Param("require3ds") Boolean require3ds,
        @Param("now") Instant now);
}
