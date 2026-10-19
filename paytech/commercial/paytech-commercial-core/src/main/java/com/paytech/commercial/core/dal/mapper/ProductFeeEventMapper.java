package com.paytech.commercial.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;

/** t_product_fee_event 的持久化访问。 */
public interface ProductFeeEventMapper {
    /** 插入一行计费事件目录，返回影响行数。 */
    int insert(ProductFeeEvent feeEvent);

    /** 按主键读取，不存在返回 null。 */
    ProductFeeEvent selectById(@Param("productFeeEventId") long productFeeEventId);

    /** 按唯一键（计费事件、条款类型）读取，不存在返回 null。 */
    ProductFeeEvent selectByFeeEventAndTermType(@Param("feeEventCode") String feeEventCode,
        @Param("termType") String termType);

    /** 读取某产品的全部计费事件，按主键升序。 */
    List<ProductFeeEvent> selectByProductCode(@Param("productCode") ProductCode productCode);

    /** 读取某条款类型下可收费的全部计费事件，按主键升序。 */
    List<ProductFeeEvent> selectByTermType(@Param("termType") String termType);
}
