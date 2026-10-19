package com.paytech.commercial.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;

/** t_product_capability 的本地仓储；目录随发版维护，不接入 BCR。 */
public interface ProductCapabilityRepository {
    /**
     * 插入可开通项目录行；影响行数不为 1 时抛出系统异常。
     *
     * @param capability 已准备 ID 与全部字段的新行
     */
    void insert(ProductCapability capability);

    /**
     * 按主键读取。
     *
     * @param productCapabilityId 可开通项目录行 ID
     * @return 可开通项，不存在为 null
     */
    ProductCapability findById(long productCapabilityId);

    /**
     * 按可开通项代码读取。
     *
     * @param capabilityCode 可开通项代码
     * @return 可开通项，不存在为 null
     */
    ProductCapability findByCode(String capabilityCode);

    /**
     * 按一组可开通项代码读取；空集合不访问数据库。
     *
     * @param capabilityCodes 可开通项代码
     * @return 按主键升序的已登记项，未登记的代码不出现
     */
    List<ProductCapability> findByCodes(Collection<String> capabilityCodes);

    /**
     * 读取某产品的全部可开通项。
     *
     * @param productCode 产品代码
     * @return 按主键升序的可开通项
     */
    List<ProductCapability> findByProductCode(ProductCode productCode);

    /**
     * 以对象当前状态为条件迁移状态；成功后同步原对象的状态与更新时间。
     *
     * @param capability 已读取的可开通项
     * @param status 目标状态
     * @param now 本次更新时间
     */
    void updateStatus(ProductCapability capability, ProductStatus status, Instant now);
}
