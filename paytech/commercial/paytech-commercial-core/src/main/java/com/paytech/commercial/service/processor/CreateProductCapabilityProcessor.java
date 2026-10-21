package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;
import com.paytech.commercial.domain.ProductCapabilityView;
import com.paytech.commercial.domain.ProductCatalogContext;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.ProductCatalogService;
import com.paytech.commercial.service.processor.request.CreateProductCapabilityRequest;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 新增可开通项（POST /products/v1/capabilities），锁 {@code product-catalog}
 * <li>业务唯一身份 uk(capability_code)：已存在返回 409，不做重放（technique.md §4.1）。</li>
 * <li>新请求：归属产品 ACTIVE；PAYMENT_METHOD 必须给出支付方式代码，其他种类（含 FEATURE）必须为空；可配维度可用于开通且不重复。可开通项
 * （ACTIVE，版本 1）、可配维度与目录 BCR 快照同一事务写入。</li>
 */
@Component
public class CreateProductCapabilityProcessor
    extends AbstractProductCatalogWriteProcessor<CreateProductCapabilityRequest, ProductCapabilityView> {
    private final ProductCatalogService productCatalogService;
    private final ProductCapabilityRepository productCapabilityRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param productRepository 产品仓储
     * @param productCatalogService 目录写入服务
     * @param productCapabilityRepository 可开通项仓储
     * @param businessChangeRecordRepository BCR 仓储
     */
    public CreateProductCapabilityProcessor(Clock clock, IdGenerator idGenerator, ProductRepository productRepository,
        ProductCatalogService productCatalogService, ProductCapabilityRepository productCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, idGenerator, productRepository, businessChangeRecordRepository);
        this.productCatalogService = productCatalogService;
        this.productCapabilityRepository = productCapabilityRepository;
    }

    /** 支付方式代码与种类匹配、可配维度规范只依赖输入。 */
    @Override
    protected void validate(CreateProductCapabilityRequest request) {
        boolean paymentMethod = request.getCapabilityKind() == CapabilityKind.PAYMENT_METHOD;
        if (paymentMethod != (request.getPaymentMethodCode() != null)) {
            throw new InvalidParameterException("PAYMENT_METHOD 必须给出 paymentMethodCode，其他种类必须为空");
        }
        checkDimensions(request.getDimensions());
    }

    @Override
    protected ProductCapabilityView processWithinLock(CreateProductCapabilityRequest request) {
        // 业务唯一身份
        if (productCapabilityRepository.findByCode(request.getCapabilityCode()) != null) {
            throw new ConcurrentRequestException("可开通项代码已存在: " + request.getCapabilityCode());
        }

        // 构建上下文
        checkProductActive(request.getProductCode());
        Instant now = now();
        ProductCapability capability = newCapability(request, now);
        List<ProductCapabilityDimension> dimensions = dimensionRows(capability.getCapabilityCode(), request.getDimensions(),
            now);
        ProductCatalogContext context = ProductCatalogContext.builder().now(now).actorId(request.getActorId())
            .root(catalogRoot()).capability(capability).dimensions(dimensions).build();

        // 保存上下文
        try {
            productCatalogService.createCapability(context);
        } catch (DuplicateKeyException concurrent) {
            throw new ConcurrentRequestException("可开通项代码已被并发占用: " + request.getCapabilityCode(), concurrent);
        }

        // 响应处理
        return new ProductCapabilityView(capability, dimensions);
    }

    /** ACTIVE 可开通项新行，废弃维度列恒为空。 */
    ProductCapability newCapability(CreateProductCapabilityRequest request, Instant now) {
        ProductCapability capability = new ProductCapability();
        capability.setProductCapabilityId(idGenerator.nextId());
        capability.setProductCode(request.getProductCode());
        capability.setCapabilityCode(request.getCapabilityCode());
        capability.setCapabilityName(request.getCapabilityName());
        capability.setCapabilityKind(request.getCapabilityKind());
        capability.setPaymentMethodCode(request.getPaymentMethodCode());
        capability.setStatus(ProductStatus.ACTIVE);
        capability.setChangeVersion(1L);
        capability.setCreateTime(now);
        capability.setUpdateTime(now);
        return capability;
    }
}
