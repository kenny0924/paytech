package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.mapper.ProductMapper;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 ProductMapper 访问 t_product。 */
@AllArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductMapper mapper;

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(Product product) {
        product.setCreateTime(micros(product.getCreateTime()));
        product.setUpdateTime(micros(product.getUpdateTime()));
        if (mapper.insert(product) != 1) {
            throw new ErrorException("产品目录插入行数不符: " + product.getProductCode());
        }
    }

    /** 按主键读取。 */
    @Override
    public Product findById(long productId) {
        return mapper.selectById(productId);
    }

    /** 按产品代码读取。 */
    @Override
    public Product findByCode(ProductCode productCode) {
        return mapper.selectByCode(productCode);
    }

    /** 读取全部产品。 */
    @Override
    public List<Product> findAll() {
        return List.copyOf(mapper.selectAll());
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(Product product, ProductStatus status, Instant now) {
        Instant time = micros(now);
        if (mapper.updateStatus(product, status, time) != 1) {
            throw new ConcurrentRequestException("产品状态已变化: " + product.getProductCode());
        }
        product.setStatus(status);
        product.setUpdateTime(time);
    }
}
