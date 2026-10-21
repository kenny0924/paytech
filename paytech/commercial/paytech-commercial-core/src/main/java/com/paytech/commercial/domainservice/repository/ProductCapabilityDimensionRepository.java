package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;

/** t_product_capability_dimension 的本地仓储；随可开通项整体替换，审计归 PRODUCT_CATALOG。 */
public interface ProductCapabilityDimensionRepository {
    /**
     * 批量插入；空集合不访问数据库，影响行数不符时抛出系统异常。
     *
     * @param rows 已准备 ID 的新行
     */
    void insertAll(List<ProductCapabilityDimension> rows);

    /**
     * 读取一组可开通项的可配维度；空集合不访问数据库。
     *
     * @param capabilityCodes 可开通项代码
     * @return 按可开通项代码、维度代码升序
     */
    List<ProductCapabilityDimension> findByCapabilityCodes(Collection<String> capabilityCodes);

    /**
     * 删除一个可开通项的全部可配维度。
     *
     * @param capabilityCode 可开通项代码
     */
    void deleteByCapabilityCode(String capabilityCode);
}
