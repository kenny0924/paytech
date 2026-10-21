package com.paytech.commercial.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;

/** t_product_capability 的持久化访问。 */
public interface ProductCapabilityMapper {
    /** 插入一行可开通项目录，返回影响行数。 */
    int insert(ProductCapability capability);

    /** 按主键读取，不存在返回 null。 */
    ProductCapability selectById(@Param("productCapabilityId") long productCapabilityId);

    /** 按可开通项代码读取，不存在返回 null。 */
    ProductCapability selectByCode(@Param("capabilityCode") String capabilityCode);

    /** 按一组可开通项代码读取，按主键升序；capabilityCodes 必须非空。 */
    List<ProductCapability> selectByCodes(@Param("capabilityCodes") Collection<String> capabilityCodes);

    /** 读取某产品的全部可开通项，按主键升序。 */
    List<ProductCapability> selectByProductCode(@Param("productCode") ProductCode productCode);

    /** 仅当状态仍为 capability.status 时迁移状态，返回影响行数。 */
    int updateStatus(@Param("capability") ProductCapability capability, @Param("status") ProductStatus status,
        @Param("now") Instant now);

    /** 按可选产品代码与状态读取可开通项，按 product_capability_id 升序；参数为空不筛选。 */
    List<ProductCapability> selectFiltered(@Param("productCode") ProductCode productCode, @Param("status") ProductStatus status);

    /** 仅当 change_version 仍为 capability.changeVersion 时改写名称与归属产品并版本加一，返回影响行数。 */
    int updateDefinition(@Param("capability") ProductCapability capability, @Param("capabilityName") String capabilityName,
        @Param("productCode") ProductCode productCode, @Param("now") Instant now);
}
