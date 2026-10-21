package com.paytech.commercial.service.processor;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.domain.ProductCatalogContext;
import com.paytech.commercial.domain.ProductFeeEventView;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.ProductCatalogService;
import com.paytech.commercial.service.processor.request.UpdateFeeEventCapabilityRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 改写计费事件挂接的开通项（PUT /products/v1/fee-events/{id}/capability），锁 {@code product-catalog}
 * <li>种类为空表示无需开通的基础产品事件；FEATURE 时 capabilityCode 必须是同产品下的 FEATURE 可开通项；其他种类 capabilityCode 为空。</li>
 * <li>目录行版本 CAS（不一致 409）；挂接条件更新与目录 BCR 快照同一事务写入。已生效价格不重新校验，下次排期 / 激活按新挂接校验。</li>
 */
@Component
public class UpdateFeeEventCapabilityProcessor
    extends AbstractProductCatalogWriteProcessor<UpdateFeeEventCapabilityRequest, ProductFeeEventView> {
    private final ProductCatalogService productCatalogService;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final ProductCapabilityRepository productCapabilityRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param productRepository 产品仓储
     * @param productCatalogService 目录写入服务
     * @param productFeeEventRepository 计费事件仓储
     * @param productCapabilityRepository 可开通项仓储
     * @param businessChangeRecordRepository BCR 仓储
     */
    public UpdateFeeEventCapabilityProcessor(Clock clock, IdGenerator idGenerator, ProductRepository productRepository,
        ProductCatalogService productCatalogService, ProductFeeEventRepository productFeeEventRepository,
        ProductCapabilityRepository productCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, idGenerator, productRepository, businessChangeRecordRepository);
        this.productCatalogService = productCatalogService;
        this.productFeeEventRepository = productFeeEventRepository;
        this.productCapabilityRepository = productCapabilityRepository;
    }

    /** 功能开通项代码只在 FEATURE 时出现且必填。 */
    @Override
    protected void validate(UpdateFeeEventCapabilityRequest request) {
        boolean feature = request.getCapabilityKind() == CapabilityKind.FEATURE;
        if (feature != (request.getCapabilityCode() != null)) {
            throw new InvalidParameterException("FEATURE 必须给出 capabilityCode，其他种类与空种类必须为空");
        }
    }

    @Override
    protected ProductFeeEventView processWithinLock(UpdateFeeEventCapabilityRequest request) {
        // 查询计费事件并核对版本
        ProductFeeEvent feeEvent = productFeeEventRepository.findById(request.getProductFeeEventId());
        if (feeEvent == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "计费事件不存在: " + request.getProductFeeEventId());
        }
        checkVersion(feeEvent.getFeeEventCode() + "/" + feeEvent.getTermType(), feeEvent.getChangeVersion(),
            request.getExpectedChangeVersion());
        if (request.getCapabilityKind() == CapabilityKind.FEATURE) {
            checkFeature(feeEvent, request.getCapabilityCode());
        }

        // 构建上下文并保存
        ProductCatalogContext context = ProductCatalogContext.builder().now(now()).actorId(request.getActorId())
            .root(catalogRoot()).feeEvent(feeEvent).nextCapabilityKind(request.getCapabilityKind())
            .nextCapabilityCode(request.getCapabilityCode()).build();
        productCatalogService.updateFeeEventCapability(context);

        // 响应处理
        return new ProductFeeEventView(feeEvent);
    }

    /** 功能开通项须存在、为 FEATURE 且与计费事件同产品。 */
    void checkFeature(ProductFeeEvent feeEvent, String capabilityCode) {
        ProductCapability capability = productCapabilityRepository.findByCode(capabilityCode);
        if (capability == null || capability.getCapabilityKind() != CapabilityKind.FEATURE
            || capability.getProductCode() != feeEvent.getProductCode()) {
            throw new BizCheckFailException("功能开通项不存在、不是 FEATURE 或不属于计费事件产品: " + capabilityCode + ", 产品 "
                + feeEvent.getProductCode().getCode());
        }
    }
}
