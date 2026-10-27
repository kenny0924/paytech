package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import java.util.List;

/** t_identifier_type 仓储。 */
public interface IdentifierTypeRepository {
    /**
     * 辖区专用与全局标识类型（含非 ACTIVE）
     * 
     * @param jurisdictionCode 辖区代码
     * @return 标识类型
     */
    List<IdentifierType> findForJurisdiction(String jurisdictionCode);

    /**
     * 按代码读取
     * 
     * @param code 标识类型代码
     * @return 标识类型；不存在为 null
     */
    IdentifierType findByCode(String code);
}
