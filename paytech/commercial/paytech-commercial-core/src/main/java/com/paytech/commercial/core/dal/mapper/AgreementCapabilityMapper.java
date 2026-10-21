package com.paytech.commercial.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

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

    /** 仅当状态仍为 capability.status 时迁移状态，返回影响行数。 */
    int updateStatus(@Param("capability") AgreementCapability capability,
        @Param("status") AgreementCapabilityStatus status, @Param("now") Instant now);

    /** 仅当 update_time 仍为 capability.updateTime 时改写生效区间、保证金条款与 3DS 要求，返回影响行数。 */
    int updateTerms(@Param("capability") AgreementCapability capability, @Param("effectiveFrom") Instant effectiveFrom,
        @Param("effectiveTo") Instant effectiveTo, @Param("rollingReserveRate") BigDecimal rollingReserveRate,
        @Param("rollingReserveDays") Integer rollingReserveDays, @Param("require3ds") Boolean require3ds,
        @Param("now") Instant now);
}
