package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;
import com.paytech.commercial.domain.ProductCapabilityView;
import com.paytech.commercial.domain.ProductCatalogContext;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityDimensionRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.ProductCatalogService;
import com.paytech.commercial.service.processor.request.TransitionProductCapabilityRequest;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 停用 / 启用可开通项（POST /products/v1/capabilities/{id}/retire|activate），锁 {@code product-catalog}
 * <li>原请求：已处于目标状态且目录行版本恰好为期望版本加一，说明同一推进已提交，直接返回当前结果。</li>
 * <li>新请求：目录行版本 CAS；停用后新增开通被拒，已开通的合同不受影响。状态条件更新与目录 BCR 快照同一事务写入。</li>
 */
@Component
public class TransitionProductCapabilityProcessor
    extends AbstractProductCatalogWriteProcessor<TransitionProductCapabilityRequest, ProductCapabilityView> {
    private final ProductCatalogService productCatalogService;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final ProductCapabilityDimensionRepository productCapabilityDimensionRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param productRepository 产品仓储
     * @param productCatalogService 目录写入服务
     * @param productCapabilityRepository 可开通项仓储
     * @param businessChangeRecordRepository BCR 仓储
     * @param productCapabilityDimensionRepository 可配维度仓储
     */
    public TransitionProductCapabilityProcessor(Clock clock, IdGenerator idGenerator, ProductRepository productRepository,
        ProductCatalogService productCatalogService, ProductCapabilityRepository productCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        ProductCapabilityDimensionRepository productCapabilityDimensionRepository) {
        super(clock, idGenerator, productRepository, businessChangeRecordRepository);
        this.productCatalogService = productCatalogService;
        this.productCapabilityRepository = productCapabilityRepository;
        this.productCapabilityDimensionRepository = productCapabilityDimensionRepository;
    }

    /** 目标状态只能是 ACTIVE 或 RETIRED。 */
    @Override
    protected void validate(TransitionProductCapabilityRequest request) {
        if (request.getTargetStatus() == ProductStatus.DRAFT) {
            throw new InvalidParameterException("可开通项只能停用或启用");
        }
    }

    @Override
    protected ProductCapabilityView processWithinLock(TransitionProductCapabilityRequest request) {
        // 查询可开通项与可配维度；同一推进已提交时直接返回
        ProductCapability capability = UpdateProductCapabilityProcessor.capability(productCapabilityRepository,
            request.getProductCapabilityId());
        List<ProductCapabilityDimension> dimensions = productCapabilityDimensionRepository.findByCapabilityCodes(
            List.of(capability.getCapabilityCode()));
        if (capability.getStatus() == request.getTargetStatus()
            && capability.getChangeVersion() == request.getExpectedChangeVersion() + 1) {
            return new ProductCapabilityView(capability, dimensions);
        }

        // 构建上下文
        checkVersion(capability.getCapabilityCode(), capability.getChangeVersion(), request.getExpectedChangeVersion());
        if (capability.getStatus() == request.getTargetStatus()) {
            return new ProductCapabilityView(capability, dimensions);
        }
        ProductCatalogContext context = ProductCatalogContext.builder().now(now()).actorId(request.getActorId())
            .root(catalogRoot()).capability(capability).dimensions(dimensions).nextStatus(request.getTargetStatus()).build();

        // 保存上下文
        productCatalogService.transitionCapability(context);

        // 响应处理
        return new ProductCapabilityView(capability, dimensions);
    }
}
