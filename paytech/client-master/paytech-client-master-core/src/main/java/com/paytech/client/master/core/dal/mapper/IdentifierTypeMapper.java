package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_identifier_type 只读 SQL。 */
public interface IdentifierTypeMapper {
    /** 辖区专用与全局标识类型（含非 ACTIVE）。 */
    List<IdentifierType> findForJurisdiction(@Param("jurisdictionCode") String jurisdictionCode);

    /** 按代码读取标识类型。 */
    IdentifierType findByCode(@Param("code") String code);
}
