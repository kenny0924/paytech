package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.ProductBusinessModeScope;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.processor.request.ReplaceAgreementProductsRequest;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 整体替换订阅产品（PUT /agreements/v1/{agreementId}/products），锁 {@code agreement:{id}}
 * <li>原请求：请求集合与当前订阅等价时不写入，直接返回当前版本（不比较期望版本）。</li>
 * <li>新请求：期望版本一致、合同未结束；产品须存在、ACTIVE、非基座，CLIENT 合同的 businessModeScope 须包含合同业务模式（PARTNER 合同
 * 无业务模式，不做模式匹配）；仍有开通项的产品不能移除。Root CAS 后为删除行先写 DELETE 快照再删除，新增行插入后写 CREATE 快照。</li>
 */
public class ReplaceAgreementProductsProcessor
    extends AbstractAgreementWriteProcessor<ReplaceAgreementProductsRequest, AgreementResponse> {
    private final IdGenerator idGenerator;
    private final AgreementService agreementService;
    private final ProductRepository productRepository;
    private final AgreementProductRepository agreementProductRepository;
    private final ProductCapabilityRepository productCapabilityRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param agreementService 合同写入服务
     * @param productRepository 产品目录仓储
     * @param agreementRepository 合同仓储
     * @param agreementProductRepository 订阅仓储
     * @param productCapabilityRepository 可开通项目录仓储
     * @param agreementCapabilityRepository 开通项仓储
     * @param businessChangeRecordRepository BCR 仓储
     */
    public ReplaceAgreementProductsProcessor(Clock clock, IdGenerator idGenerator, AgreementService agreementService,
        ProductRepository productRepository, AgreementRepository agreementRepository,
        AgreementProductRepository agreementProductRepository, ProductCapabilityRepository productCapabilityRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.idGenerator = idGenerator;
        this.agreementService = agreementService;
        this.productRepository = productRepository;
        this.agreementProductRepository = agreementProductRepository;
        this.productCapabilityRepository = productCapabilityRepository;
        this.agreementCapabilityRepository = agreementCapabilityRepository;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(ReplaceAgreementProductsRequest request) {
        return request.getAgreementId();
    }

    /** 产品代码不得重复。 */
    @Override
    protected void validate(ReplaceAgreementProductsRequest request) {
        if (new HashSet<>(request.getProductCodes()).size() != request.getProductCodes().size()) {
            throw new InvalidParameterException("productCodes 不得重复");
        }
    }

    @Override
    protected AgreementResponse processWithinLock(ReplaceAgreementProductsRequest request) {
        // 查询合同与当前订阅
        Agreement agreement = getAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        List<AgreementProduct> current = agreementProductRepository.findByAgreementId(agreement.getAgreementId());
        Set<ProductCode> requested = Set.copyOf(request.getProductCodes());
        if (codes(current).equals(requested)) {
            return response(agreement, root);
        }

        // 构建上下文
        AgreementContext context = buildContext(agreement, root, current, requested, request);

        // 保存上下文
        agreementService.replaceProducts(context);

        // 响应处理
        return response(agreement, root);
    }

    /** 校验版本、状态、产品资格与移除约束，构造新增与删除行。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, List<AgreementProduct> current,
        Set<ProductCode> requested, ReplaceAgreementProductsRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        checkOpen(agreement, "修改订阅产品");
        Map<ProductCode, Product> catalog = productRepository.findAll().stream()
            .collect(Collectors.toMap(Product::getProductCode, Function.identity()));
        Set<ProductCode> existing = codes(current);
        requested.stream().filter(code -> !existing.contains(code))
            .forEach(code -> checkSubscribable(agreement, catalog.get(code), code));
        List<AgreementProduct> removed = current.stream().filter(row -> !requested.contains(row.getProductCode())).toList();
        checkNoCapabilities(agreement, removed);
        Instant now = now();
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now,
            request.getExpectedChangeVersion());
        context.setRemovedProducts(removed);
        context.setAddedProducts(requested.stream().filter(code -> !existing.contains(code))
            .sorted((left, right) -> left.getCode().compareTo(right.getCode()))
            .map(code -> buildProduct(agreement, code, now)).toList());
        return context;
    }

    /** 产品存在、ACTIVE、非基座，且适用范围包含合同业务模式。 */
    static void checkSubscribable(Agreement agreement, Product product, ProductCode code) {
        if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
            throw new BizCheckFailException("产品不存在或未启用: " + code.getCode());
        }
        if (Boolean.TRUE.equals(product.getBaseProduct())) {
            throw new BizCheckFailException("基座产品无需订阅: " + code.getCode());
        }
        BusinessMode mode = agreement.getBusinessMode();
        if (mode != null && !scopeIncludes(product.getBusinessModeScope(), mode)) {
            throw new BizCheckFailException("产品不适用于合同业务模式: " + code.getCode() + ", 业务模式: " + mode.getCode()
                + ", 产品范围: " + product.getBusinessModeScope().getCode());
        }
    }

    /** 产品适用范围是否包含业务模式。 */
    static boolean scopeIncludes(ProductBusinessModeScope scope, BusinessMode mode) {
        return scope == ProductBusinessModeScope.BOTH
            || (scope == ProductBusinessModeScope.MOR_ONLY && mode == BusinessMode.MOR)
            || (scope == ProductBusinessModeScope.PSP_ONLY && mode == BusinessMode.PSP);
    }

    /** 移除的产品下不能仍有开通项。 */
    void checkNoCapabilities(Agreement agreement, List<AgreementProduct> removed) {
        if (removed.isEmpty()) {
            return;
        }
        List<AgreementCapability> capabilities = agreementCapabilityRepository.findByAgreementId(agreement.getAgreementId());
        Set<ProductCode> removedCodes = codes(removed);
        productCapabilityRepository.findByCodes(capabilities.stream().map(AgreementCapability::getCapabilityCode).toList())
            .stream().filter(capability -> removedCodes.contains(capability.getProductCode())).findFirst()
            .map(ProductCapability::getProductCode).ifPresent(code -> {
                throw new BizCheckFailException("产品仍有开通项，不能移除订阅: " + code.getCode() + ", 合同: "
                    + agreement.getAgreementId());
            });
    }

    /** 订阅行的产品集合。 */
    static Set<ProductCode> codes(List<AgreementProduct> rows) {
        return rows.stream().map(AgreementProduct::getProductCode).collect(Collectors.toSet());
    }

    /** 新订阅行。 */
    AgreementProduct buildProduct(Agreement agreement, ProductCode code, Instant now) {
        AgreementProduct row = new AgreementProduct();
        row.setAgreementProductId(idGenerator.nextId());
        row.setAgreementId(agreement.getAgreementId());
        row.setProductCode(code);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }
}
