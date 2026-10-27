package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.RelationType;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_relation_type 只读 SQL。 */
public interface RelationTypeMapper {
    /** 辖区专用与全局关系类型（含非 ACTIVE）。 */
    List<RelationType> findForJurisdiction(@Param("jurisdictionCode") String jurisdictionCode);

    /** 全部关系类型。 */
    List<RelationType> findAll();
}
