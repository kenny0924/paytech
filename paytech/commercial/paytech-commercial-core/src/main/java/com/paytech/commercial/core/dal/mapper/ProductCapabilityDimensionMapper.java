package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;

/** t_product_capability_dimension 的持久化访问。 */
public interface ProductCapabilityDimensionMapper {
    /** 批量插入，返回影响行数；rows 必须非空。 */
    int insertAll(@Param("rows") List<ProductCapabilityDimension> rows);

    /** 读取一组可开通项的可配维度，按可开通项代码、维度代码升序；capabilityCodes 必须非空。 */
    List<ProductCapabilityDimension> selectByCapabilityCodes(@Param("capabilityCodes") Collection<String> capabilityCodes);

    /** 删除一个可开通项的全部可配维度，返回删除行数。 */
    int deleteByCapabilityCode(@Param("capabilityCode") String capabilityCode);
}
