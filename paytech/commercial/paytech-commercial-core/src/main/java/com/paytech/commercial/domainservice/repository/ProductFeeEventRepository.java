package com.paytech.commercial.domainservice.repository;

import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import java.time.Instant;
import com.paytech.commercial.api.enums.CapabilityKind;

/** t_product_fee_event 的本地仓储；目录随发版维护，不接入 BCR。 */
public interface ProductFeeEventRepository {
    /**
     * 插入计费事件目录行；影响行数不为 1 时抛出系统异常。
     *
     * @param feeEvent 已准备 ID 与全部字段的新行
     */
    void insert(ProductFeeEvent feeEvent);

    /**
     * 按主键读取。
     *
     * @param productFeeEventId 计费事件目录行 ID
     * @return 计费事件，不存在为 null
     */
    ProductFeeEvent findById(long productFeeEventId);

    /**
     * 按唯一键读取，用于校验组件 trigger_code 是否可在该条款类型下收费。
     *
     * @param feeEventCode 计费事件代码
     * @param termType 条款类型
     * @return 计费事件，未登记为 null
     */
    ProductFeeEvent findByFeeEventAndTermType(String feeEventCode, String termType);

    /**
     * 读取某产品的全部计费事件。
     *
     * @param productCode 产品代码
     * @return 按主键升序的计费事件
     */
    List<ProductFeeEvent> findByProductCode(ProductCode productCode);

    /**
     * 读取某条款类型下可收费的全部计费事件；结果为空即该 term_type 未登记。
     *
     * @param termType 条款类型
     * @return 按主键升序的计费事件
     */
    List<ProductFeeEvent> findByTermType(String termType);

    /**
     * 按可选产品代码与条款类型读取计费事件。
     *
     * @param productCode 产品代码，空不筛选
     * @param termType 条款类型，空不筛选
     * @return 按 product_fee_event_id 升序
     */
    List<ProductFeeEvent> findFiltered(ProductCode productCode, String termType);

    /**
     * 以对象 changeVersion 为条件改写挂接的开通项种类与功能开通项；成功后同步原对象，版本加一。
     *
     * @param feeEvent 已读取的计费事件
     * @param capabilityKind 挂接种类，可空
     * @param capabilityCode 功能开通项代码，仅 FEATURE 非空
     * @param now 本次更新时间
     */
    void updateCapability(ProductFeeEvent feeEvent, CapabilityKind capabilityKind, String capabilityCode, Instant now);

    /**
     * 读取精确挂接到某功能开通项的计费事件。
     *
     * @param capabilityCode 功能开通项代码
     * @return 按主键升序
     */
    List<ProductFeeEvent> findByCapabilityCode(String capabilityCode);
}
