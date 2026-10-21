package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;
import com.paytech.commercial.domain.ProductCapabilityView;
import com.paytech.commercial.domain.ProductCatalogContext;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.ProductCatalogService;
import com.paytech.commercial.service.processor.request.UpdateProductCapabilityRequest;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 改写可开通项名称、归属产品与可配维度（PUT /products/v1/capabilities/{id}），锁 {@code product-catalog}
 * <li>代码与种类不可改；目录行版本 CAS（不一致 409）；新归属产品须 ACTIVE。名称与产品条件更新、可配维度整体替换、目录 BCR 快照同一事务写入。</li>
 * <li>已开通的合同条件不随之改写，下次修改开通项时按新维度校验。</li>
 */
@Component
public class UpdateProductCapabilityProcessor
    extends AbstractProductCatalogWriteProcessor<UpdateProductCapabilityRequest, ProductCapabilityView> {
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
    public UpdateProductCapabilityProcessor(Clock clock, IdGenerator idGenerator, ProductRepository productRepository,
        ProductCatalogService productCatalogService, ProductCapabilityRepository productCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, idGenerator, productRepository, businessChangeRecordRepository);
        this.productCatalogService = productCatalogService;
        this.productCapabilityRepository = productCapabilityRepository;
    }

    /** 可配维度规范只依赖输入。 */
    @Override
    protected void validate(UpdateProductCapabilityRequest request) {
        checkDimensions(request.getDimensions());
    }

    @Override
    protected ProductCapabilityView processWithinLock(UpdateProductCapabilityRequest request) {
        // 查询可开通项并核对版本
        ProductCapability capability = capability(productCapabilityRepository, request.getProductCapabilityId());
        checkVersion(capability.getCapabilityCode(), capability.getChangeVersion(), request.getExpectedChangeVersion());
        checkProductActive(request.getProductCode());

        // 构建上下文
        Instant now = now();
        List<ProductCapabilityDimension> dimensions = dimensionRows(capability.getCapabilityCode(), request.getDimensions(),
            now);
        ProductCatalogContext context = ProductCatalogContext.builder().now(now).actorId(request.getActorId())
            .root(catalogRoot()).capability(capability).dimensions(dimensions)
            .nextCapabilityName(request.getCapabilityName()).nextProductCode(request.getProductCode()).build();

        // 保存上下文
        productCatalogService.updateCapability(context);

        // 响应处理
        return new ProductCapabilityView(capability, dimensions);
    }

    /**
     * 可开通项必须存在
     *
     * @param repository 可开通项仓储
     * @param productCapabilityId 可开通项 ID
     * @return 可开通项
     */
    static ProductCapability capability(ProductCapabilityRepository repository, long productCapabilityId) {
        ProductCapability capability = repository.findById(productCapabilityId);
        if (capability == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "可开通项不存在: " + productCapabilityId);
        }
        return capability;
    }
}
