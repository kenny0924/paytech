package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.OrganizationType;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_organization_type 只读 SQL。 */
public interface OrganizationTypeMapper {
    /** 辖区全部组织类型（含非 ACTIVE）。 */
    List<OrganizationType> findByJurisdiction(@Param("jurisdictionCode") String jurisdictionCode);
}
