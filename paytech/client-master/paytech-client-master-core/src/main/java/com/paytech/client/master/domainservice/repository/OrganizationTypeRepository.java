package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.OrganizationType;
import java.util.List;

/** t_organization_type 仓储。 */
public interface OrganizationTypeRepository {
    /**
     * 辖区全部组织类型（含非 ACTIVE），按展示顺序
     * 
     * @param jurisdictionCode 辖区代码
     * @return 组织类型
     */
    List<OrganizationType> findByJurisdiction(String jurisdictionCode);
}
