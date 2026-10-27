package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import java.util.List;

/** t_onboarding_schema 仓储。 */
public interface OnboardingSchemaRepository {
    /**
     * 精确 Schema 键的 ACTIVE 版本
     * 
     * @param jurisdictionCode 辖区代码
     * @param partyType 主主体类型代码
     * @param roleType 角色
     * @param organizationTypeCode 组织类型代码；默认版本传 *
     * @return ACTIVE 版本；没有为 null
     */
    OnboardingSchema findActive(String jurisdictionCode, String partyType, String roleType, String organizationTypeCode);

    /**
     * 按 ID 读取
     * 
     * @param schemaId 版本 ID
     * @return 版本；不存在为 null
     */
    OnboardingSchema findById(long schemaId);

    /**
     * 辖区全部版本，供运营只读查看
     * 
     * @param jurisdictionCode 辖区代码
     * @return 版本列表
     */
    List<OnboardingSchema> findByJurisdiction(String jurisdictionCode);
}
