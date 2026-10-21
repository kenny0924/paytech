package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;
import com.paytech.commercial.domain.ProductCatalogContext;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.service.processor.request.ProductCapabilityDefinitionRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * 产品目录写入 Processor 的公共部分：单一目录锁 {@code product-catalog}（LOCK，租约 -1）、目录 BCR Root 读取、目录行版本比较与可配维度构造
 * <li>目录规模小且写入低频，四个目录写入口共用同一资源锁，避免可开通项与计费事件挂接交叉修改。</li>
 *
 * @param <Q> 内部请求
 * @param <R> 内部响应
 */
public abstract class AbstractProductCatalogWriteProcessor<Q, R> extends AbstractLockProcessor<Q, R> {
    protected final Clock clock;
    protected final IdGenerator idGenerator;
    protected final ProductRepository productRepository;
    protected final BusinessChangeRecordRepository businessChangeRecordRepository;

    /**
     * 注入公共依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param productRepository 产品仓储
     * @param businessChangeRecordRepository BCR 仓储
     */
    protected AbstractProductCatalogWriteProcessor(Clock clock, IdGenerator idGenerator, ProductRepository productRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.productRepository = productRepository;
        this.businessChangeRecordRepository = businessChangeRecordRepository;
    }

    /** 目录锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(Q request) {
        return LockCommand.lock(CommercialLocks.catalog());
    }

    /** 本次命令的业务时刻，截断到 DATETIME(6) 微秒。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 目录 BCR Root；首次写入前不存在。 */
    BusinessChangeRecord catalogRoot() {
        return businessChangeRecordRepository.findByRecord(BusinessChangeRecordRecordType.PRODUCT_CATALOG,
            Long.toString(ProductCatalogContext.ROOT_ID));
    }

    /** 目录行版本必须等于期望版本，写入时仍由条件 SQL 兜底。 */
    static void checkVersion(String label, long current, long expected) {
        if (current != expected) {
            throw new ConcurrentRequestException("目录已变化，请刷新后重试: " + label + "，当前版本 " + current + "，期望版本 "
                + expected);
        }
    }

    /** 归属产品须存在且 ACTIVE。 */
    void checkProductActive(ProductCode productCode) {
        Product product = productRepository.findByCode(productCode);
        if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
            throw new BizCheckFailException("归属产品不存在或未启用: " + productCode.getCode());
        }
    }

    /** 可配维度不重复且可用于开通接受条件。 */
    static void checkDimensions(List<ProductCapabilityDefinitionRequest.Dimension> dimensions) {
        Set<String> seen = new HashSet<>();
        for (ProductCapabilityDefinitionRequest.Dimension dimension : dimensions) {
            if (!seen.add(dimension.getDimensionCode().getCode())) {
                throw new InvalidParameterException("dimensions 不得重复: " + dimension.getDimensionCode().getCode());
            }
            if (!dimension.getDimensionCode().isAcceptanceAllowed()) {
                throw new BizCheckFailException("维度不可用于开通接受条件: " + dimension.getDimensionCode().getCode());
            }
        }
    }

    /**
     * 可配维度新行
     *
     * @param capabilityCode 可开通项代码
     * @param dimensions 请求维度
     * @param now 业务时刻
     * @return 新行
     */
    List<ProductCapabilityDimension> dimensionRows(String capabilityCode,
        List<ProductCapabilityDefinitionRequest.Dimension> dimensions, Instant now) {
        return dimensions.stream().map(dimension -> {
            ProductCapabilityDimension row = new ProductCapabilityDimension();
            row.setCapabilityDimensionId(idGenerator.nextId());
            row.setCapabilityCode(capabilityCode);
            row.setDimensionCode(dimension.getDimensionCode());
            row.setRequired(dimension.isRequired());
            row.setCreateTime(now);
            row.setUpdateTime(now);
            return row;
        }).toList();
    }
}
