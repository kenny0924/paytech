package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.AttributeDefinition;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_attribute_definition 只读 SQL。 */
public interface AttributeDefinitionMapper {
    /** 辖区专用与全局属性定义（含非 ACTIVE）。 */
    List<AttributeDefinition> findForJurisdiction(@Param("jurisdictionCode") String jurisdictionCode);
}
