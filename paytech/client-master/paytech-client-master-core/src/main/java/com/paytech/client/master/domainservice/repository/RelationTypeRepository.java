package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.RelationType;
import java.util.List;

/** t_relation_type 仓储。 */
public interface RelationTypeRepository {
    /**
     * 辖区专用与全局关系类型（含非 ACTIVE）
     * 
     * @param jurisdictionCode 辖区代码
     * @return 关系类型
     */
    List<RelationType> findForJurisdiction(String jurisdictionCode);

    /**
     * 全部关系类型
     * 
     * @return 关系类型
     */
    List<RelationType> findAll();
}
