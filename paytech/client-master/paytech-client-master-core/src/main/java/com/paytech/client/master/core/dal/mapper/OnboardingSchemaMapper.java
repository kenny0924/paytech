package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_onboarding_schema 只读 SQL。 */
public interface OnboardingSchemaMapper {
    /** 精确键的 ACTIVE 版本。 */
    OnboardingSchema findActive(@Param("jurisdictionCode") String jurisdictionCode, @Param("partyType") String partyType, @Param("roleType") String roleType, @Param("organizationTypeCode") String organizationTypeCode);

    /** 按 ID 读取。 */
    OnboardingSchema findById(@Param("schemaId") long schemaId);

    /** 辖区全部版本。 */
    List<OnboardingSchema> findByJurisdiction(@Param("jurisdictionCode") String jurisdictionCode);
}
