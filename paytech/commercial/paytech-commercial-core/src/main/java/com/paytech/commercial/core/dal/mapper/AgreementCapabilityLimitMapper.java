package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;

/** t_agreement_capability_limit 的持久化访问。 */
public interface AgreementCapabilityLimitMapper {
    /** 批量插入约束行，返回影响行数；limits 必须非空。 */
    int insertAll(@Param("limits") List<AgreementCapabilityLimit> limits);

    /** 按主键读取，不存在返回 null。 */
    AgreementCapabilityLimit selectById(@Param("agreementCapabilityLimitId") long agreementCapabilityLimitId);

    /** 读取一个开通项的全部约束，按主键升序。 */
    List<AgreementCapabilityLimit> selectByAgreementCapabilityId(@Param("agreementCapabilityId") long agreementCapabilityId);

    /** 读取一组开通项的全部约束，按主键升序；agreementCapabilityIds 必须非空。 */
    List<AgreementCapabilityLimit> selectByAgreementCapabilityIds(
        @Param("agreementCapabilityIds") Collection<Long> agreementCapabilityIds);

    /** 删除一个开通项的全部约束，返回删除行数。 */
    int deleteByAgreementCapabilityId(@Param("agreementCapabilityId") long agreementCapabilityId);
}
