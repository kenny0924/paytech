package com.paytech.commercial.domainservice.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.ProductCatalogContext;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domainservice.repository.ProductCapabilityDimensionRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.service.ProductCatalogService;

import lombok.AllArgsConstructor;

/**
 * ProductCatalogService 实现：只访问 mor_commercial，每个写方法一个 TransactionTemplate 事务
 * <li>审计为单一 PRODUCT_CATALOG Root：首次写入创建 Root（版本 1），之后每条命令推进 Root 并写入本次目录行的快照；目录锁串行化写入。</li>
 */
@Service
@AllArgsConstructor
public class ProductCatalogServiceImpl implements ProductCatalogService {
    private final TransactionTemplate transactionTemplate;
    private final BusinessChangeHistoryWriter historyWriter;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final ProductCapabilityDimensionRepository productCapabilityDimensionRepository;

    /** 插入可开通项与维度后写目录快照。 */
    @Override
    public void createCapability(ProductCatalogContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            productCapabilityRepository.insert(context.getCapability());
            productCapabilityDimensionRepository.insertAll(context.getDimensions());
            audit(context, BusinessChangeReason.CATALOG_CAPABILITY_CREATED,
                BusinessChangeSnapshot.productCapabilityData(context.getCapability(), context.getDimensions()));
        });
    }

    /** 版本条件改写定义、整体替换维度后写目录快照。 */
    @Override
    public void updateCapability(ProductCatalogContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            productCapabilityRepository.updateDefinition(context.getCapability(), context.getNextCapabilityName(),
                context.getNextProductCode(), context.getNow());
            productCapabilityDimensionRepository.deleteByCapabilityCode(context.getCapability().getCapabilityCode());
            productCapabilityDimensionRepository.insertAll(context.getDimensions());
            audit(context, BusinessChangeReason.CATALOG_CAPABILITY_UPDATED,
                BusinessChangeSnapshot.productCapabilityData(context.getCapability(), context.getDimensions()));
        });
    }

    /** 版本与状态条件迁移后写目录快照。 */
    @Override
    public void transitionCapability(ProductCatalogContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            productCapabilityRepository.updateStatus(context.getCapability(), context.getNextStatus(), context.getNow());
            audit(context, BusinessChangeReason.CATALOG_CAPABILITY_STATUS_CHANGED,
                BusinessChangeSnapshot.productCapabilityData(context.getCapability(), context.getDimensions()));
        });
    }

    /** 版本条件改写挂接后写目录快照。 */
    @Override
    public void updateFeeEventCapability(ProductCatalogContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            productFeeEventRepository.updateCapability(context.getFeeEvent(), context.getNextCapabilityKind(),
                context.getNextCapabilityCode(), context.getNow());
            audit(context, BusinessChangeReason.CATALOG_FEE_EVENT_UPDATED,
                BusinessChangeSnapshot.productFeeEventData(context.getFeeEvent()));
        });
    }

    /** 首次写入创建目录 Root，否则以锁内读取的当前版本推进 Root。 */
    void audit(ProductCatalogContext context, BusinessChangeReason reason, Map<String, Object> data) {
        if (context.getRoot() == null) {
            historyWriter.createRoot(BusinessChangeRecordAggregateRootType.PRODUCT_CATALOG, ProductCatalogContext.ROOT_ID,
                reason, data, context.getActorId(), context.getNow());
            return;
        }
        historyWriter.advanceRoot(context.getRoot(), context.getRoot().getCurrentVersion(), reason, data,
            context.getActorId(), context.getNow());
    }
}
