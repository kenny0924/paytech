package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_jurisdiction 只读 SQL。 */
public interface JurisdictionMapper {
    /** 按代码读取辖区包。 */
    Jurisdiction find(@Param("jurisdictionCode") String jurisdictionCode);

    /** 全部辖区包。 */
    List<Jurisdiction> findAll();
}
