package com.paytech.commercial.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Product;

/** t_product 的持久化访问。 */
public interface ProductMapper {
    /** 插入一行产品目录，返回影响行数。 */
    int insert(Product product);

    /** 按主键读取，不存在返回 null。 */
    Product selectById(@Param("productId") long productId);

    /** 按产品代码读取，不存在返回 null。 */
    Product selectByCode(@Param("productCode") ProductCode productCode);

    /** 读取全部产品，按主键升序。 */
    List<Product> selectAll();

    /** 仅当状态仍为 product.status 时迁移状态，返回影响行数。 */
    int updateStatus(@Param("product") Product product, @Param("status") ProductStatus status, @Param("now") Instant now);

    /** 按可选状态读取产品，按 product_id 升序；status 为空不筛选。 */
    List<Product> selectFiltered(@Param("status") ProductStatus status);
}
