package com.paytech.commercial.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Product;

/** t_product 的本地仓储；目录随发版维护，不接入 BCR。 */
public interface ProductRepository {
    /**
     * 插入产品目录行；影响行数不为 1 时抛出系统异常。
     * <li>创建与更新时间截断到微秒后写回对象，与 DATETIME(6) 一致。</li>
     *
     * @param product 已准备 ID 与全部字段的新行
     */
    void insert(Product product);

    /**
     * 按主键读取。
     *
     * @param productId 产品目录行 ID
     * @return 产品，不存在为 null
     */
    Product findById(long productId);

    /**
     * 按产品代码读取。
     *
     * @param productCode 产品代码
     * @return 产品，不存在为 null
     */
    Product findByCode(ProductCode productCode);

    /**
     * 读取全部产品。
     *
     * @return 按主键升序的产品列表
     */
    List<Product> findAll();

    /**
     * 以对象当前状态为条件迁移状态；成功后同步原对象的状态与更新时间。
     *
     * @param product 已读取的产品
     * @param status 目标状态
     * @param now 本次更新时间
     */
    void updateStatus(Product product, ProductStatus status, Instant now);
}
