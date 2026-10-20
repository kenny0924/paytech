package com.paytech.commercial.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.domain.ProductCapabilityView;
import com.paytech.commercial.domain.ProductFeeEventView;
import com.paytech.commercial.domain.ProductView;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.ProductCatalogQueryService;

import lombok.AllArgsConstructor;

/** ProductCatalogQueryService 实现：逐表读取目录行并转换为不携带 DataObject 的只读结果。 */
@Service
@AllArgsConstructor
public class ProductCatalogQueryServiceImpl implements ProductCatalogQueryService {
    private final ProductRepository productRepository;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final ProductCapabilityRepository productCapabilityRepository;

    /** 按可选状态读取产品。 */
    @Override
    public List<ProductView> listProducts(ProductStatus status) {
        return productRepository.findByStatus(status).stream().map(ProductView::new).toList();
    }

    /** 按可选产品与状态读取可开通项。 */
    @Override
    public List<ProductCapabilityView> listCapabilities(ProductCode productCode, ProductStatus status) {
        return productCapabilityRepository.findFiltered(productCode, status).stream().map(ProductCapabilityView::new).toList();
    }

    /** 按可选产品与条款类型读取计费事件。 */
    @Override
    public List<ProductFeeEventView> listFeeEvents(ProductCode productCode, String termType) {
        return productFeeEventRepository.findFiltered(productCode, termType).stream().map(ProductFeeEventView::new).toList();
    }
}
