package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.AttributeDefinition;
import java.util.List;

/** t_attribute_definition 仓储。 */
public interface AttributeDefinitionRepository {
    /**
     * 辖区专用与全局属性定义（含非 ACTIVE）
     * 
     * @param jurisdictionCode 辖区代码
     * @return 属性定义
     */
    List<AttributeDefinition> findForJurisdiction(String jurisdictionCode);
}
